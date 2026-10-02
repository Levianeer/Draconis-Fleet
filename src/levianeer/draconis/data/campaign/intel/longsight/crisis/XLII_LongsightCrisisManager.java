package levianeer.draconis.data.campaign.intel.longsight.crisis;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseEventManager;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import levianeer.draconis.data.campaign.econ.XLII_MarketTransfer;
import levianeer.draconis.data.campaign.ids.Factions;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Sector-wide orchestrator for the Office Takeover crisis - spawns individual
 * {@link XLII_LongsightBastionIntel} entries over time, the same way vanilla's own
 * {@code PirateBaseManager} spawns {@code PirateBaseIntel}. Location-picking below is adapted
 * directly from {@code PirateBaseManager.pickSystemForPirateBase()}.
 * <p>
 * <b>Live-wired, as of Stage 7 (work/outline/office-takeover-crisis-checklist.md).</b> Created for
 * real by {@code XLII_NanoforgeExchange}'s {@code give_uplink} branch, the moment the player is
 * handed the Longsight uplink - see {@link #DEBUG_FORCE_KEY}'s own doc for why that field is
 * still named the way it is despite no longer being a testing-only flag. Every Bastion spawned here
 * starts hidden (per the design doc's "The reveal and the severed uplink") until
 * {@link #checkReveal} fires - see that method's own doc.
 * <p>
 * <b>Persistence note - read before changing how this is registered.</b> Unlike the small monitor
 * scripts in {@code XLII_ModPlugin} that get unconditionally removed and recreated on every
 * {@code onGameLoad()}, this class must persist as the *same instance* across saves - it owns the
 * {@code active} list of every currently-live Bastion (inherited from {@link BaseEventManager}),
 * and each Bastion is ticked exclusively through that list, not independently (see the class doc on
 * {@link XLII_LongsightBastionIntel} for why). Recreating this on load would silently orphan every
 * already-spawned Bastion - it would still exist in the sector, but nothing would ever call its
 * {@code advance()} again. This follows {@code DraconisAIOTracker}'s singleton-lookup-and-
 * conditionally-create shape instead of the monitor family's pattern - see {@link #get()} and
 * {@link #createIfNecessary()}.
 */
public class XLII_LongsightCrisisManager extends BaseEventManager {

    private static final Logger log = Global.getLogger(XLII_LongsightCrisisManager.class);

    private static final String KEY = "$XLII_longsightCrisisManagerRef";

    /**
     * The real production gate as of Stage 7 - set permanently true by {@code XLII_NanoforgeExchange}'s
     * {@code give_uplink} branch the moment the uplink is granted, never unset. Kept under its original
     * "debug" name rather than renamed: it was a genuine testing-only shim through Stages 0-6.5
     * ({@code XLII_ModPlugin.onGameLoad()} used to force-set it unconditionally on every load, with no
     * connection to real quest state - that shim is gone, see that method's own comment). The design
     * checklist treats reusing this exact flag/method pair as the intended real trigger, not something
     * to rename in the same pass.
     */
    public static final String DEBUG_FORCE_KEY = "$XLII_longsightCrisisDebugForceOn";

    /** Set once, in {@link #createIfNecessary()}, the moment this manager is actually created for
     *  real - the "moment the uplink is granted" reference point {@link #checkReveal} measures
     *  elapsed days against, via {@code getElapsedDaysSince()} (the correct idiom - see the
     *  timestamp-unit bug documented at length on {@code XLII_LongsightBastionIntel.MS_PER_DAY} for
     *  why a raw {@code getTimestamp()} difference must never be combined with
     *  {@code convertToSeconds()} instead). */
    private static final String CREATED_TIMESTAMP_KEY = "$XLII_longsightCrisisCreatedTimestamp";

    // ==================== Placeholder tuning (Stage 1 / Stage 8) ====================
    // TEMP, cranked deliberately extreme to speed-run testing the "Longsight wins" end state, which
    // otherwise requires consuming the whole Sector to observe. NOT the real pacing intent - restore
    // to something sane before Stage 8's tuning pass. Not yet time-scaled (no growth-over-time curve).
    private static final int PLACEHOLDER_MIN_CONCURRENT = 5;
    private static final int PLACEHOLDER_MAX_CONCURRENT = 12;
    private static final float PLACEHOLDER_BASE_INTERVAL_DAYS = 0.5f;
    private static final int PLACEHOLDER_HARD_LIMIT = 40;

    private final Random random = new Random();

    // ==================== Stage 6: end states ====================
    // Both permanent, no third "still going, unaddressed forever" state - see the design doc's
    // "End states" section. Checked every tick in advance() below.

    /** Persistent - once set, createIfNecessary() refuses to ever start the crisis again. */
    private static final String RESOLVED_KEY = "$XLII_longsightCrisisResolved";

    /** Persistent - "LONGSIGHT_WINS" or "PLAYER_WINS", for any future epilogue/logging hook. */
    private static final String RESOLVED_OUTCOME_KEY = "$XLII_longsightCrisisOutcome";

    /**
     * How long the active count has to stay at zero, with nothing new spawning, before "player
     * wins" is declared. BaseEventManager's own spawn-check timing isn't directly observable from
     * here, so this is a grace-period heuristic (a multiple of the base interval) rather than a
     * precise "is a spawn pending" flag - placeholder, not tuned, see checklist Stage 8.
     */
    private static final float VICTORY_GRACE_DAYS = PLACEHOLDER_BASE_INTERVAL_DAYS * 3f;

    private float daysWithNoActiveBastions = 0f;
    private boolean crisisResolved = false;

    /** Guards against declaring "player wins" before the crisis has even spawned its first Bastion
     *  - without this, a fresh crisis sits at zero active for its first couple of days by
     *  definition, which would otherwise race the victory-grace timer from turn one. */
    private boolean everHadActiveBastion = false;

    // ==================== Stage 7: the reveal ====================
    // The crisis climbs hidden from the moment the uplink is granted (see CREATED_TIMESTAMP_KEY)
    // until it silently crosses an elapsed-time threshold. At that point: the crisis becomes visible
    // (same model as Remnant/pirate activity - fightable on sight from then on), a short
    // non-branching scene plays, and the uplink item goes mechanically inert elsewhere
    // (XLII_LongsightWatchdog/XLII_LongsightConfrontation's own job, untouched here). See the design
    // doc's "The reveal and the severed uplink" section for the full design intent.

    /** Persistent, one-shot. Read by {@code XLII_LongsightBastionIntel.isHidden()} for every Bastion
     *  at once - a single sector-wide flag, not per-instance state, since the reveal is one event
     *  that applies to the whole crisis simultaneously. */
    private static final String REVEALED_KEY = "$XLII_longsightCrisisRevealed";

    /**
     * TEMP placeholder, deliberately short for testing convenience - matches the other placeholder
     * constants above (see {@link #PLACEHOLDER_MIN_CONCURRENT} etc.). Explicitly called out as
     * not-yet-tuned in the design doc; restore to something deliberately paced before Stage 8's
     * tuning pass.
     */
    private static final float REVEAL_THRESHOLD_DAYS = 5f;

    public static boolean isRevealed() {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(REVEALED_KEY);
    }

    // ==================== Captured colonies ====================

    /**
     * Set on a market's memory by {@code XLII_LongsightBastionIntel.resolveInvasionOutcome()} the
     * moment this crisis captures it. Never cleared from here - {@code
     * XLII_LongsightCrisisTrackerIntel.getCapturedMarkets()} filters on current Draconis ownership
     * as well, so a market later retaken by anyone else drops off that list on its own with no
     * separate liberation bookkeeping needed.
     */
    public static final String CAPTURED_FLAG = "$XLII_longsightCrisisCaptured";

    /**
     * Set alongside {@link #CAPTURED_FLAG} to the market's faction id at the moment of capture,
     * before {@code XLII_MarketTransfer.transferMarket()} overwrites it to Draconis - read by
     * {@code XLII_LongsightCrisisTrackerIntel} so the "Colonies Captured" list can show who
     * actually lost the place instead of just "Draconis" (which every row would otherwise say,
     * unhelpfully, since that's who owns it now).
     */
    public static final String PREVIOUS_OWNER_FLAG = "$XLII_longsightCrisisPreviousOwner";

    // ==================== Destroyed colonies ====================
    // Unlike a capture, XLII_LongsightBastionIntel.resolveInvasionOutcome()'s destroy branch calls
    // DecivTracker.decivilize(target, true, true) - fullDestroy=true, which unconditionally calls
    // Economy.removeMarket() regardless of that flag. The MarketAPI is gone from
    // getEconomy().getMarketsCopy() from that point on, so unlike captured colonies (tagged via a
    // memory flag and found by a live market scan), a destroyed one can only be listed by snapshotting
    // its name/former owner into this plain record list at the moment of destruction.

    public static class DestroyedMarketRecord {
        public final String name;
        public final String previousOwnerId;
        public DestroyedMarketRecord(String name, String previousOwnerId) {
            this.name = name;
            this.previousOwnerId = previousOwnerId;
        }
    }

    private final List<DestroyedMarketRecord> destroyedMarkets = new ArrayList<>();

    /** Called by {@code XLII_LongsightBastionIntel.resolveInvasionOutcome()}'s destroy branch,
     *  before the market reference becomes unreachable through the economy. */
    public void recordMarketDestroyed(String name, String previousOwnerId) {
        destroyedMarkets.add(new DestroyedMarketRecord(name, previousOwnerId));
    }

    public List<DestroyedMarketRecord> getDestroyedMarkets() {
        return destroyedMarkets;
    }

    // ==================== Destroyed-Bastion slowdown ====================
    // Decaying friction: each Bastion the player destroys temporarily slows the next one's
    // appearance via getIntervalRateMult() below, fading back to nothing over FRICTION_DECAY_DAYS.
    // Placeholder tuning, consistent with every other "TEMP"/"PLACEHOLDER" constant in this class -
    // not yet balanced against real play.

    private static final float FRICTION_PER_DESTRUCTION = 0.15f;
    private static final float FRICTION_DECAY_DAYS = 30f;
    private static final float MAX_SLOWDOWN_FRACTION = 0.75f;

    private final List<Long> destructionTimestamps = new ArrayList<>();

    /** Called by {@code XLII_LongsightBastionIntel.advanceImpl()} the moment a Bastion is found gone. */
    public void recordBastionDestroyed() {
        long now = Global.getSector().getClock().getTimestamp();
        destructionTimestamps.add(now);
        destructionTimestamps.removeIf(
                ts -> Global.getSector().getClock().getElapsedDaysSince(ts) >= FRICTION_DECAY_DAYS);
        log.info("Draconis: Longsight Bastion destruction recorded - slowdown now "
                + Math.round(getSlowdownFraction() * 100f) + "%");
    }

    /** 0 (no recent destructions) to {@link #MAX_SLOWDOWN_FRACTION} - read by both
     *  {@link #getIntervalRateMult()} and {@link XLII_LongsightDestroyedBastionFactor} for display. */
    public float getSlowdownFraction() {
        float total = 0f;
        for (long ts : destructionTimestamps) {
            float daysAgo = Global.getSector().getClock().getElapsedDaysSince(ts);
            if (daysAgo >= FRICTION_DECAY_DAYS) continue;
            total += FRICTION_PER_DESTRUCTION * (1f - daysAgo / FRICTION_DECAY_DAYS);
        }
        return Math.min(MAX_SLOWDOWN_FRACTION, total);
    }

    @Override
    protected float getIntervalRateMult() {
        return 1f - getSlowdownFraction();
    }

    /**
     * Fires once, the moment elapsed days since {@link #CREATED_TIMESTAMP_KEY} crosses
     * {@link #REVEAL_THRESHOLD_DAYS}. Sets the sector-wide flag {@link #isRevealed} reads, tells
     * every currently-active Bastion to drop its own entity-level discovery gate (new Bastions
     * spawned after this point never set one in the first place - see
     * {@code XLII_LongsightBastionIntel.spawnBastionFleet}), and shows the reveal scene to the player.
     * {@code isHidden()} itself needs no per-instance update here - every Bastion already reads this
     * same global flag dynamically.
     */
    private void checkReveal() {
        if (isRevealed()) return;

        long created = Global.getSector().getMemoryWithoutUpdate().getLong(CREATED_TIMESTAMP_KEY);
        float elapsedDays = Global.getSector().getClock().getElapsedDaysSince(created);
        if (elapsedDays < REVEAL_THRESHOLD_DAYS) return;

        Global.getSector().getMemoryWithoutUpdate().set(REVEALED_KEY, true);
        log.info("Draconis: Longsight crisis revealed after " + (int) elapsedDays + " days");

        for (EveryFrameScript s : getActive()) {
            if (s instanceof XLII_LongsightBastionIntel bastion) {
                bastion.onCrisisRevealed();
            }
        }

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet != null) {
            Global.getSector().getCampaignUI()
                    .showInteractionDialog(new XLII_LongsightRevealDialog(), playerFleet);
        }
    }

    // ==================== Singleton lookup ====================

    public static XLII_LongsightCrisisManager get() {
        Object o = Global.getSector().getMemoryWithoutUpdate().get(KEY);
        if (o instanceof XLII_LongsightCrisisManager m) return m;

        // readResolve() is not called by XStream for non-Serializable classes (BaseEventManager
        // does not implement Serializable) - fall back to scanning registered scripts, same
        // reasoning DraconisAIOTracker.get() documents for its own intel-manager fallback.
        for (EveryFrameScript s : Global.getSector().getScripts()) {
            if (s instanceof XLII_LongsightCrisisManager m) {
                Global.getSector().getMemoryWithoutUpdate().set(KEY, m);
                return m;
            }
        }
        return null;
    }

    /** Safe to call every load - a no-op once created. Only actually creates one while testing mode
     *  ({@link #DEBUG_FORCE_KEY}) is on, and never again once the crisis has permanently resolved
     *  (see {@link #RESOLVED_KEY}). */
    public static void createIfNecessary() {
        if (!Global.getSector().getMemoryWithoutUpdate().getBoolean(DEBUG_FORCE_KEY)) return;
        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(RESOLVED_KEY)) return;
        if (get() != null) return;

        XLII_LongsightCrisisManager manager = new XLII_LongsightCrisisManager();
        Global.getSector().getMemoryWithoutUpdate().set(KEY, manager);
        Global.getSector().getMemoryWithoutUpdate().set(
                CREATED_TIMESTAMP_KEY, Global.getSector().getClock().getTimestamp());
        Global.getSector().addScript(manager);
        XLII_LongsightCrisisTrackerIntel.createIfNecessary();
        log.info("Draconis: Longsight Crisis Manager created");
    }

    // ==================== BaseEventManager overrides ====================

    @Override
    protected int getMinConcurrent() {
        return PLACEHOLDER_MIN_CONCURRENT;
    }

    @Override
    protected int getMaxConcurrent() {
        return PLACEHOLDER_MAX_CONCURRENT;
    }

    @Override
    protected float getBaseInterval() {
        return PLACEHOLDER_BASE_INTERVAL_DAYS;
    }

    @Override
    protected int getHardLimit() {
        return PLACEHOLDER_HARD_LIMIT;
    }

    @Override
    public void advance(float amount) {
        if (crisisResolved) return;

        super.advance(amount);
        checkReveal();

        if (!hasNonDraconisMarketsRemaining()) {
            resolveCrisis("LONGSIGHT_WINS");
            return;
        }

        if (getActiveCount() > 0) {
            everHadActiveBastion = true;
            daysWithNoActiveBastions = 0f;
            return;
        }

        if (!everHadActiveBastion) return; // crisis hasn't actually started yet - not a victory

        float days = Global.getSector().getClock().convertToDays(amount);
        daysWithNoActiveBastions += days;
        if (daysWithNoActiveBastions >= VICTORY_GRACE_DAYS) {
            resolveCrisis("PLAYER_WINS");
        }
    }

    /**
     * Marks the crisis over, permanently - see {@link #createIfNecessary()} for why it can never
     * restart after this. Stops this manager (and every remaining active Bastion, ticked exclusively
     * through it - see this class's own persistence note) via {@link #isDone()}. No real epilogue
     * scene yet - that's still Stage 7/8 territory - but a placeholder end-of-crisis message exists
     * now, same reasoning as every other placeholder {@code addMessage} call in
     * {@code XLII_LongsightBastionIntel}: a message exists for every key event, even if the prose
     * isn't final.
     */
    private void resolveCrisis(String outcome) {
        crisisResolved = true;
        Global.getSector().getMemoryWithoutUpdate().set(RESOLVED_KEY, true);
        Global.getSector().getMemoryWithoutUpdate().set(RESOLVED_OUTCOME_KEY, outcome);

        XLII_LongsightCrisisTrackerIntel trackerIntel = XLII_LongsightCrisisTrackerIntel.get();
        if (trackerIntel != null) trackerIntel.endImmediately();

        if ("LONGSIGHT_WINS".equals(outcome)) {
            Global.getSector().getCampaignUI().addMessage(
                    "The Office Takeover crisis has consumed the Sector - no market remains outside Draconis control.",
                    Misc.getNegativeHighlightColor());
        } else {
            Global.getSector().getCampaignUI().addMessage(
                    "The Office Takeover crisis has been contained - no bases remain active.",
                    Misc.getPositiveHighlightColor());
        }

        log.info("Draconis: Longsight crisis resolved permanently - " + outcome);
    }

    @Override
    public boolean isDone() {
        return crisisResolved;
    }

    /** "Longsight wins" - see the design doc's "End states" section. Hidden markets, and anything
     *  {@code XLII_MarketTransfer.isProtectedFromCrisisInvasion()} would also exclude from
     *  targeting (Nexerelin allies, or - universally - high-reputation factions), are excluded here
     *  too, matching {@code XLII_LongsightBastionIntel.pickInvasionTarget()}'s own exclusions: none
     *  of these were ever a valid invasion target to begin with, so their continued existence
     *  shouldn't block this condition either - otherwise "Longsight wins" could never trigger at all
     *  in any game where Draconis has an ally or a good-standing neighbor. */
    private boolean hasNonDraconisMarketsRemaining() {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.isHidden()) continue;
            if (Factions.DRACONIS.equals(market.getFactionId())) continue;
            if (XLII_MarketTransfer.isProtectedFromCrisisInvasion(market.getFactionId())) continue;
            return true;
        }
        return false;
    }

    @Override
    protected EveryFrameScript createEvent() {
        StarSystemAPI system = pickSystemForBastion();
        if (system == null) return null;

        XLII_LongsightBastionIntel bastion = new XLII_LongsightBastionIntel(system);
        if (bastion.isEnded()) return null;

        log.info("Draconis: Longsight Bastion spawned in [" + system.getName() + "]");
        return bastion;
    }

    // ==================== Location picking ====================
    // Adapted from vanilla PirateBaseManager.pickSystemForPirateBase(). Deliberately not yet doing
    // the "starts near Ladon/Fafnir, widens outward" geographic escalation the design calls for -
    // see the checklist's Stage 1 "not yet started" list.

    private StarSystemAPI pickSystemForBastion() {
        WeightedRandomPicker<StarSystemAPI> picker = new WeightedRandomPicker<>(random);

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.hasTag(Tags.THEME_SPECIAL)) continue;
            if (system.hasTag(Tags.THEME_HIDDEN)) continue;
            if (system.hasPulsar()) continue;
            if (system.getStar() == null) continue;
            if (!Misc.getMarketsInLocation(system).isEmpty()) continue;

            // TEMP: dropped from 45 for the same speed-run reason as the constants above - a
            // heavily-explored save could otherwise bottleneck spawning on this alone. Restore
            // before Stage 8.
            float daysSinceVisit = Global.getSector().getClock()
                    .getElapsedDaysSince(system.getLastPlayerVisitTimestamp());
            if (daysSinceVisit < 5f) continue;

            float weight;
            if (system.hasTag(Tags.THEME_CORE_UNPOPULATED)) {
                continue;
            } else if (system.hasTag(Tags.THEME_RUINS)) {
                weight = 5f;
            } else if (system.hasTag(Tags.THEME_REMNANT_NO_FLEETS)
                    || system.hasTag(Tags.THEME_REMNANT_DESTROYED)
                    || system.hasTag(Tags.THEME_MISC)) {
                weight = 3f;
            } else {
                weight = 1f;
            }

            picker.add(system, weight);
        }

        return picker.pick();
    }
}
