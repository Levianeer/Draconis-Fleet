package levianeer.draconis.data.campaign.companion;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyInteractionListener;
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener;
import com.fs.starfarer.api.campaign.listeners.DiscoverEntityListener;
import com.fs.starfarer.api.campaign.listeners.SurveyPlanetListener;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

/**
 * Watches the base game on Korrin's behalf and queues topics for it.
 * <p>
 * Nothing in vanilla can call {@link KorrinTopicQueue#queue}, so without this he can only react to
 * Draconis content. This is the layer that makes him a companion traveling with you rather than a
 * commentator on our own questline.
 * <p>
 * <b>It decides whether, never what.</b> No dialogue, no words, no draw. Every callback resolves a
 * situation into candidate topic ids and hands them to {@link #consider}; the writing lives in
 * korrin_topics.csv and rules.csv exactly as it does for everything else.
 * <p>
 * Design: {@code work/outline/korrin-reactions.md} § The observer layer.
 */
public class KorrinObserver implements ColonyInteractionListener, CurrentLocationChangedListener,
        DiscoverEntityListener, SurveyPlanetListener {

    private static final Logger log = Global.getLogger(KorrinObserver.class);

    /** Topics already fired, so everything is one-shot unless deliberately re-queued. */
    private static final String FIRED_KEY = "$korrin_fired";

    /**
     * How close the player fleet has to get to an uninhabited planet before
     * {@link #pollNearbyPlanets()} treats it as visited. Not tied to any actual game mechanic -
     * this is flavor only, so the margin just has to feel like "arrived," not be precise.
     */
    private static final float PLANET_PROXIMITY_MARGIN = 500f;

    /** Registered transient: re-added fresh on each game load, nothing to clean up. */
    public static void register() {
        Global.getSector().getListenerManager().addListener(new KorrinObserver(), true);
    }

    /**
     * Queues the first candidate that someone has actually written, and records it.
     * <p>
     * <b>This is the specificity floor.</b> Candidates run most specific first, and a candidate
     * with no row in korrin_topics.csv is simply skipped - so he remarks on the places and events
     * that have a particular line waiting and stays quiet everywhere else. There is no generic
     * fallback and there must never be one: a canned line poisons every specific line around it,
     * and silence costs nothing.
     * <p>
     * The happy consequence is that adding a reaction is a data edit. Nothing here needs to know
     * which markets or systems have content.
     */
    private static void consider(String... candidates) {
        // GONE is checked alongside UNMET: KorrinTopicQueue.queue() itself already refuses both
        // states, so this is a hygiene fix on top of that - without it, isKnownTopic()/hasFired()
        // still run and queue() is still called every time for content that can never be read
        // again once he's permanently gone.
        if (KorrinCompanion.getState() == KorrinCompanion.State.UNMET
                || KorrinCompanion.isGoneForever()) {
            return;
        }

        for (String id : candidates) {
            if (id == null || id.isEmpty()) continue;
            if (!KorrinTopicQueue.isKnownTopic(id)) continue;
            if (hasFired(id)) continue;

            if (KorrinTopicQueue.queue(id)) {
                markFired(id);
                log.info("Draconis: Korrin observer queued '" + id + "'");
            }
            // Whether it fired or not, one written candidate is enough. Falling through to a
            // less specific one would let a faction line stand in for a market line the player
            // was simply not aboard to hear.
            return;
        }
    }

    // --- events --------------------------------------------------------------------------------

    /**
     * Docking. The Hegemony thread hangs off this one: he knows only what he has been told about
     * them, suspects it is managed, and the player is the only primary source he has ever had.
     */
    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {
        if (market == null) return;
        consider("korrin_market_" + market.getId(),
                 "korrin_faction_" + market.getFactionId());
    }

    /** Arriving somewhere. Systems he has a line about; silence in the several hundred he does not. */
    @Override
    public void reportCurrentLocationChanged(LocationAPI prev, LocationAPI curr) {
        if (!(curr instanceof StarSystemAPI)) return;
        consider("korrin_system_" + curr.getId());
    }

    /**
     * Finding something. The Domain thread runs through here - the hardware looks closer to
     * Draconis pattern than fifty cycles and a Rift should allow, and he cannot take that to
     * anyone.
     */
    @Override
    public void reportEntityDiscovered(SectorEntityToken entity) {
        if (entity == null) return;
        String type = entity.getCustomEntityType();
        if (type == null || type.isEmpty()) return;
        consider("korrin_entity_" + type);
    }

    /**
     * Surveying a planet. Left in for any future uninhabited body the player has to genuinely
     * survey, but this never fires for Fafnir's own outer worlds - they sit in Draconis' home
     * system and the game already treats them as surveyed, so the player never performs the
     * action that would raise this event. {@link #pollNearbyPlanets()} is the route that actually
     * covers them.
     */
    @Override
    public void reportPlayerSurveyedPlanet(PlanetAPI planet) {
        if (planet == null) return;
        consider("korrin_entity_" + planet.getId());
    }

    /**
     * Player fleet arriving near an uninhabited planet in the current system. The route in for
     * bodies with no market to dock at and no {@code customEntityType} for
     * {@link #reportEntityDiscovered} to see - and, in practice, the only route in for planets the
     * game already considers surveyed, which never raise {@link #reportPlayerSurveyedPlanet} at
     * all. Polled rather than event-driven, same as {@link #pollWatches()}: there is no listener
     * for "the player got close to this entity."
     * <p>
     * Iterates every planet in the system rather than a known list - the specificity floor in
     * {@link #consider} is what keeps this cheap and correct, exactly as it does for docking and
     * discovery. A planet with no row in korrin_topics.csv costs one distance check and nothing
     * else.
     */
    public static void pollNearbyPlanets() {
        // See consider()'s own note on GONE.
        if (KorrinCompanion.getState() == KorrinCompanion.State.UNMET
                || KorrinCompanion.isGoneForever()) {
            return;
        }

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet == null) return;

        LocationAPI loc = playerFleet.getContainingLocation();
        if (!(loc instanceof StarSystemAPI)) return;

        for (PlanetAPI planet : loc.getPlanets()) {
            float range = planet.getRadius() + PLANET_PROXIMITY_MARGIN;
            if (Misc.getDistance(playerFleet, planet) <= range) {
                consider("korrin_entity_" + planet.getId());
            }
        }
    }

    @Override
    public void reportPlayerClosedMarket(MarketAPI market) {
    }

    @Override
    public void reportPlayerOpenedMarketAndCargoUpdated(MarketAPI market) {
    }

    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
    }

    // --- vanilla quest flags -------------------------------------------------------------------

    /**
     * Polls the memory flags declared in the {@code watch} column of korrin_topics.csv.
     * <p>
     * Vanilla quests are the one surface with no listener - they are memory flags and intel
     * objects - so they have to be looked at rather than waited on. Driven from
     * {@link KorrinCommentScript}, which already ticks with the right guards; a second script would
     * buy nothing.
     * <p>
     * Only declared flags are read. Do not widen this into a scan of sector memory.
     * <p>
     * Checks sector memory first, then falls back to the player person's memory - some vanilla
     * quests (e.g. Sword of Eventide's duel/Neriene outcomes) write their branch flags there instead
     * of to sector memory, and rules.csv's own {@code $player.} scope resolves the same way.
     */
    public static void pollWatches() {
        // See consider()'s own note on GONE.
        if (KorrinCompanion.getState() == KorrinCompanion.State.UNMET
                || KorrinCompanion.isGoneForever()) {
            return;
        }

        for (KorrinTopicQueue.Topic topic : KorrinTopicQueue.getWatchers()) {
            if (hasFired(topic.id)) continue;
            boolean flagSet = Global.getSector().getMemoryWithoutUpdate().getBoolean(topic.watch)
                    || Global.getSector().getPlayerPerson().getMemoryWithoutUpdate().getBoolean(topic.watch);
            if (!flagSet) continue;
            consider(topic.id);
        }
    }

    // --- one-shot bookkeeping ------------------------------------------------------------------

    private static boolean hasFired(String topicId) {
        return KorrinTopicQueue.contains(FIRED_KEY, topicId);
    }

    private static void markFired(String topicId) {
        KorrinTopicQueue.add(FIRED_KEY, topicId);
    }
}
