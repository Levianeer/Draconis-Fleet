package levianeer.draconis.data.scripts.combat.commander;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

/**
 * Core 1 of section 12 of the build doc: every ship decides which fights it may take, and vanilla's
 * ship AI does the flying through a live personality override. P4 measured timid ships spending 0.11 of
 * their time inside enemy range against vanilla's 0.28, at twice the exchange: vanilla's harass mode holds
 * at the enemy's threat range and darts in when the target faces away.
 * <p>
 * But timid for every ship all battle is a retreat: in core2-v1 all 7 ships stayed timid and the fleet
 * backed away from the advancing Hegemony line for the whole battle. So each ship has a base mode set by
 * its group's role ({@link TaskForces}), and keep out is the exception for ships that are outmatched:
 * <ul>
 * <li><b>keep out</b> (timid): threatened and our strength near it below {@link #KEEP_OUT}x the enemy's
 * (until {@link #KEEP_OUT_END}x);</li>
 * <li><b>hold</b> (steady, which prefers its own longest range): the base for every ship. Timid as the
 * skirmish base left frigates idle in core2-v3: timid holds outside the enemy's reach, and when the enemy
 * out-ranges a frigate that is outside its own reach too;</li>
 * <li><b>commit</b> (aggressive): our strength within {@link #SUPPORT} of the ship is at least
 * {@link #COMMIT}x that of every enemy that reaches it or could within {@link #INCOMING} more, and it stays
 * committed until that falls below {@link #COMMIT_END}x. core1-v1 counted enemies within a flat 2500 and
 * committed 5-6 of 7 ships at first contact, against the few forward ships, with the main body just
 * beyond: the fleet fought aggressive, the probe's worst-but-one mode.</li>
 * </ul>
 * <b>Openings</b>, for opportunists (the skirmish role): an enemy within {@link #OPENING_RANGE} that is
 * overloaded, venting, at {@link #OPENING_FLUX} flux or above, or under {@link #OPENING_HULL} hull, while
 * the ship's own flux is under {@link #OPENING_OWN_FLUX} and it is not badly outmatched
 * ({@link #OPENING_BALANCE}). The ship commits with that enemy as its target, through the AI's maneuver
 * target flag, its ship target and the target override together, until the opening closes. After the
 * Divisions mod's frigate handling (MIT, defarius777; {@code DivisionCommanderAI.applyDirectCloseEngageFlags}
 * and {@code directShipAndFightersOntoTarget}), reimplemented.
 * <p>
 * A ship holds a mode at least {@link #MIN_MODE_SECONDS}, unless it drops to keep out or takes an
 * opening. Outmatched frigates and destroyers also get {@code DO_NOT_PURSUE}, so they are not baited off
 * alone.
 * <p>
 * <b>Capitals never chase frigates</b> (author's rule, core2-v4: a close fight became a loss when the
 * Alwaid chased a Wolf across the map). A capital whose AI target is a frigate gets {@code DO_NOT_PURSUE},
 * and once that frigate is outside the capital's reach it is retargeted onto the nearest enemy that is not
 * a frigate.
 */
final class Spacing {

    private static final Logger log = Global.getLogger(Spacing.class);

    enum Mode {
        KEEP_OUT("timid"), HOLD("steady"), COMMIT("aggressive");

        final String personality;

        Mode(String personality) {
            this.personality = personality;
        }
    }

    /** Our ships this close to a ship back it up. */
    private static final float SUPPORT = 1500f;
    /** Enemies this far beyond their reach count against committing: they arrive within seconds. */
    private static final float INCOMING = 1500f;
    private static final float COMMIT = 2f;
    private static final float COMMIT_END = 1.5f;
    /** Below this local balance a frigate or destroyer stops chasing. */
    private static final float OUTMATCHED = 0.95f;
    private static final float KEEP_OUT = 0.8f;
    private static final float KEEP_OUT_END = 1f;
    /** An enemy counts as a threat once the ship is within its reach plus this. */
    private static final float THREAT_MARGIN = 500f;
    private static final float MIN_MODE_SECONDS = 5f;
    private static final float OPENING_RANGE = 2000f;
    private static final float OPENING_FLUX = 0.75f;
    private static final float OPENING_HULL = 0.4f;
    private static final float OPENING_OWN_FLUX = 0.5f;
    private static final float OPENING_BALANCE = 0.6f;
    private static final float FLAG_TTL = 1.5f;

    final Map<ShipAPI, Mode> modes = new IdentityHashMap<>();
    /** Opportunists currently taking an opening, and on whom. */
    final Map<ShipAPI, ShipAPI> openings = new IdentityHashMap<>();
    private final Map<ShipAPI, Float> since = new IdentityHashMap<>();
    /** Mode changes and openings taken since the last {@link #logState}. */
    private int changes;
    private int openingsTaken;

    void update(List<ShipAPI> ours, BattlePicture picture, float t, Predicate<ShipAPI> opportunist) {
        modes.keySet().removeIf(s -> !ours.contains(s));
        since.keySet().removeIf(s -> !ours.contains(s));
        openings.keySet().removeIf(s -> !ours.contains(s));
        for (ShipAPI s : ours) {
            if (s.getShipAI() == null) continue;
            float support = 0f;
            for (ShipAPI o : ours) if (Misc.getDistance(o.getLocation(), s.getLocation()) <= SUPPORT) support += BattlePicture.strength(o);
            boolean threatened = false;
            float incoming = 0f;
            for (BattlePicture.Cluster c : picture.clusters) {
                for (ShipAPI e : c.ships) {
                    float reach = BattlePicture.reach(e);
                    float gap = Misc.getDistance(e.getLocation(), s.getLocation()) - reach - s.getCollisionRadius();
                    if (gap <= INCOMING) incoming += BattlePicture.strength(e);
                    if (gap <= THREAT_MARGIN) threatened = true;
                }
            }
            float balance = incoming > 0f ? support / incoming : Float.POSITIVE_INFINITY;
            ShipAPI opening = opportunist.test(s) && s.getFluxLevel() < OPENING_OWN_FLUX && balance >= OPENING_BALANCE
                    ? opening(s, picture) : null;
            ShipAPI previous = opening == null ? openings.remove(s) : openings.put(s, opening);
            if (opening != null && previous != opening) openingsTaken++;
            Mode current = modes.get(s);
            Mode wanted;
            if (opening != null) {
                wanted = Mode.COMMIT;
                s.getAIFlags().setFlag(AIFlags.MANEUVER_TARGET, FLAG_TTL, opening);
                s.setShipTarget(opening);
                s.getShipAI().setTargetOverride(opening);
            // Nothing in reach is no fight to commit to: an "infinite" balance would send ships in aggressive.
            } else if (threatened && (balance >= COMMIT || current == Mode.COMMIT && balance >= COMMIT_END)) wanted = Mode.COMMIT;
            else if (threatened && (balance < KEEP_OUT || current == Mode.KEEP_OUT && balance < KEEP_OUT_END)) wanted = Mode.KEEP_OUT;
            else wanted = Mode.HOLD;

            if ((s.isFrigate() || s.isDestroyer()) && threatened && balance < OUTMATCHED && opening == null) {
                s.getAIFlags().setFlag(AIFlags.DO_NOT_PURSUE, FLAG_TTL);
            }
            if (s.isCapital()) noFrigateChase(s, picture);
            boolean held = current != null && t - since.get(s) < MIN_MODE_SECONDS && wanted != Mode.KEEP_OUT && opening == null;
            if (wanted == current || held) continue;
            modes.put(s, wanted);
            since.put(s, t);
            s.getShipAI().getConfig().personalityOverride = wanted.personality;
            s.getShipAI().cancelCurrentManeuver();
            if (current != null) changes++;
        }
    }

    private static void noFrigateChase(ShipAPI s, BattlePicture picture) {
        Object target = s.getAIFlags().getCustom(AIFlags.MANEUVER_TARGET);
        if (!(target instanceof ShipAPI) || !((ShipAPI) target).isFrigate()) return;
        ShipAPI frigate = (ShipAPI) target;
        s.getAIFlags().setFlag(AIFlags.DO_NOT_PURSUE, FLAG_TTL);
        float reach = BattlePicture.reach(s) + s.getCollisionRadius() + frigate.getCollisionRadius();
        if (Misc.getDistance(s.getLocation(), frigate.getLocation()) <= reach) return;
        ShipAPI best = null;
        for (BattlePicture.Cluster c : picture.clusters) {
            for (ShipAPI e : c.ships) {
                if (e.isFrigate()) continue;
                if (best == null || Misc.getDistance(e.getLocation(), s.getLocation()) < Misc.getDistance(best.getLocation(), s.getLocation())) best = e;
            }
        }
        if (best == null) return;
        s.getAIFlags().setFlag(AIFlags.MANEUVER_TARGET, FLAG_TTL, best);
        s.setShipTarget(best);
        s.getShipAI().setTargetOverride(best);
    }

    /** The nearest enemy in {@link #OPENING_RANGE} that is overloaded, venting, high on flux or low on hull. */
    private static ShipAPI opening(ShipAPI s, BattlePicture picture) {
        ShipAPI best = null;
        float bestDistance = OPENING_RANGE;
        for (BattlePicture.Cluster c : picture.clusters) {
            for (ShipAPI e : c.ships) {
                float distance = Misc.getDistance(e.getLocation(), s.getLocation()) - e.getCollisionRadius();
                if (distance > bestDistance) continue;
                boolean open = e.getFluxTracker().isOverloadedOrVenting() || e.getFluxLevel() >= OPENING_FLUX || e.getHullLevel() < OPENING_HULL;
                if (!open) continue;
                best = e;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** One log line: how many ships are in each mode, mode changes, and openings taken since the last line. */
    void logState(float t) {
        int[] count = new int[Mode.values().length];
        for (Mode m : modes.values()) count[m.ordinal()]++;
        log.info(String.format("DraconisCommander,spacing,t=%.0f,keepOut=%d,hold=%d,commit=%d,openingsNow=%d,openingsTaken=%d,changes=%d",
                t, count[Mode.KEEP_OUT.ordinal()], count[Mode.HOLD.ordinal()], count[Mode.COMMIT.ordinal()], openings.size(), openingsTaken, changes));
        changes = 0;
        openingsTaken = 0;
    }
}
