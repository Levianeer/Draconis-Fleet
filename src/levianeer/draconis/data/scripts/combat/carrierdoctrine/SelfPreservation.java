package levianeer.draconis.data.scripts.combat.carrierdoctrine;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

/**
 * Decides which hulls are outnumbered and must leave their local fight; {@link DraconisAdmiralAI}
 * acts on it by putting them on timid at once, which hands them to {@link FleetCohesion}'s fallback
 * to our main body. Vanilla's ship AI never leaves a fight on its own, and in the baseline hulls
 * outnumbered for most of their last 30s made up the clearest death pattern. See
 * {@code .claude/systems/ship-ai.md}.
 * <p>
 * Not {@code BACK_OFF}: the first version held that flag on and the hulls still died, mostly while
 * backing off - it makes a hull strafe at about the enemy's threat range, keeping distance without
 * leaving. Timid will not engage at all, and the fallback moves the hull to friends.
 * <p>
 * Outnumbered is by DP within {@link #LOCAL_RADIUS}, the hull and its allies against enemies. It
 * starts below {@link #ENTER_RATIO} and lifts at {@link #EXIT_RATIO} or with no enemy near, but not
 * before {@link #MIN_HOLD_SECONDS}: without the hold, leaving the radius released the hull at once
 * and it drifted back in, so episodes lasted 3-4s and repeated.
 */
public class SelfPreservation {

    private static final Logger log = Global.getLogger(SelfPreservation.class);

    private static final float LOCAL_RADIUS = 2000f;
    private static final float ENTER_RATIO = 1.0f;
    private static final float EXIT_RATIO = 1.5f;
    private static final float MIN_HOLD_SECONDS = 8f;

    private final boolean acting;
    private final IntervalUtil interval = new IntervalUtil(0.5f, 0.5f);
    /** Hull -> when its current episode started. */
    private final Map<ShipAPI, Float> holding = new IdentityHashMap<>();

    public SelfPreservation(boolean acting) {
        this.acting = acting;
    }

    /** Whether the admiral should hold {@code ship} on timid now. Always false in observe-only mode. */
    public boolean holds(ShipAPI ship) {
        return acting && holding.containsKey(ship);
    }

    /** Hulls currently in an episode, for the fleet log line. */
    public int holding() {
        return holding.size();
    }

    /** Every frame. {@code steered} are the Draconis hulls the admiral may act on while its gate is open. */
    public void advance(float amount, float elapsed, List<ShipAPI> steered, List<ShipAPI> ours, List<ShipAPI> enemies) {
        interval.advance(amount);
        if (!interval.intervalElapsed()) return;
        for (ShipAPI ship : steered) {
            float enemyDp = dpWithin(ship, enemies);
            float ratio = enemyDp <= 0f ? Float.MAX_VALUE : dpWithin(ship, ours) / enemyDp;
            Float since = holding.get(ship);
            if (since == null && ratio < ENTER_RATIO) {
                holding.put(ship, elapsed);
                logEvent(elapsed, ship, "start", ratio, 0f);
            } else if (since != null && ratio >= EXIT_RATIO && elapsed - since >= MIN_HOLD_SECONDS) {
                holding.remove(ship);
                logEvent(elapsed, ship, "end", ratio, elapsed - since);
            }
        }
        holding.keySet().retainAll(steered);
    }

    /** DP of {@code ships} within {@link #LOCAL_RADIUS} of {@code ship}, edge to edge, counting itself if listed. */
    private static float dpWithin(ShipAPI ship, List<ShipAPI> ships) {
        float sum = 0f;
        for (ShipAPI other : ships) {
            float edge = Misc.getDistance(ship.getLocation(), other.getLocation())
                    - ship.getCollisionRadius() - other.getCollisionRadius();
            if (other != ship && edge > LOCAL_RADIUS) continue;
            FleetMemberAPI member = other.getFleetMember();
            if (member != null) sum += member.getDeploymentPointsCost();
        }
        return sum;
    }

    private void logEvent(float elapsed, ShipAPI ship, String phase, float ratio, float seconds) {
        log.info(String.format("DraconisAdmiral,selfPreservation,t=%.0f,ship=%s,id=%s,size=%s,phase=%s,mode=%s,ratio=%.2f,hull=%.2f,seconds=%.0f",
                elapsed, ship.getName(), ship.getId(), ship.getHullSize(), phase, acting ? "acting" : "observing",
                Math.min(99f, ratio), ship.getHullLevel(), seconds));
    }
}
