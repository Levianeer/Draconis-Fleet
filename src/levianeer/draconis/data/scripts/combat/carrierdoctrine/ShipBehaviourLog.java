package levianeer.draconis.data.scripts.combat.carrierdoctrine;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

/**
 * Raw per-second record of how each of our hulls is fighting, for measuring the individual ship AI's
 * failures at self-preservation and aggression timing (see {@code .claude/systems/ship-ai.md}). It
 * logs signals, not verdicts: the failure measures are computed offline from these rows so they can
 * be tuned without a rebuild. Observation only.
 * <p>
 * Distances are edge to edge. A hull's target is what its AI is maneuvering against
 * ({@code MANEUVER_TARGET}), falling back to its ship target. {@code backOff} is vanilla's own
 * {@code BACK_OFF} flag, which its flux manager sets after sustained high flux.
 */
public class ShipBehaviourLog {

    private static final Logger log = Global.getLogger(ShipBehaviourLog.class);

    /** Radius for counting who is in a hull's local fight. */
    private static final float LOCAL_RADIUS = 2000f;

    private final int owner;
    private final IntervalUtil interval = new IntervalUtil(1f, 1f);
    private final Set<ShipAPI> seen = Collections.newSetFromMap(new IdentityHashMap<ShipAPI, Boolean>());
    private final Map<ShipAPI, Float> lastHull = new IdentityHashMap<>();

    public ShipBehaviourLog(int owner) {
        this.owner = owner;
        log.info("DraconisAdmiral,shipHeader,t,ship,id,size,personality,hull,flux,hardFlux,overloadVent,backOff,"
                + "range,target,targetDist,targetFlux,targetOverloadVent,targetHull,nearestEnemyDist,"
                + "enemiesNear,alliesNear,opening,openingDist");
    }

    /** Every frame. {@code ours} and {@code enemies} are live, non-fighter hulls of each side. */
    public void advance(float amount, float elapsed, List<ShipAPI> ours, List<ShipAPI> enemies) {
        for (ShipAPI ship : seen) {
            if (lastHull.containsKey(ship) && (!ship.isAlive() || ship.isHulk())) {
                log.info(String.format("DraconisAdmiral,shipDeath,t=%.0f,ship=%s,id=%s,size=%s",
                        elapsed, ship.getName(), ship.getId(), ship.getHullSize()));
                lastHull.remove(ship);
            }
        }
        interval.advance(amount);
        if (!interval.intervalElapsed()) return;

        for (ShipAPI ship : ours) {
            if (ship.getOwner() != owner) continue;
            seen.add(ship);
            lastHull.put(ship, ship.getHullLevel());
            float range = gunRange(ship);
            ShipAPI target = target(ship);
            ShipAPI nearest = null;
            float nearestDist = Float.MAX_VALUE;
            ShipAPI opening = null;
            float openingDist = Float.MAX_VALUE;
            int enemiesNear = 0;
            for (ShipAPI enemy : enemies) {
                float d = edge(ship, enemy);
                if (d < nearestDist) {
                    nearestDist = d;
                    nearest = enemy;
                }
                if (d <= LOCAL_RADIUS) enemiesNear++;
                if (d <= range && overloadedOrVenting(enemy) && d < openingDist) {
                    openingDist = d;
                    opening = enemy;
                }
            }
            int alliesNear = 0;
            for (ShipAPI ally : ours) {
                if (edge(ship, ally) <= LOCAL_RADIUS) alliesNear++;
            }
            log.info(String.format("DraconisAdmiral,ship,%.0f,%s,%s,%s,%s,%.2f,%.2f,%.2f,%d,%d,%.0f,%s,%.0f,%.2f,%d,%.2f,%.0f,%d,%d,%s,%.0f",
                    elapsed, ship.getName(), ship.getId(), ship.getHullSize(), personality(ship),
                    ship.getHullLevel(), ship.getFluxLevel(), ship.getHardFluxLevel(),
                    overloadedOrVenting(ship) ? 1 : 0,
                    ship.getAIFlags() != null && ship.getAIFlags().hasFlag(AIFlags.BACK_OFF) ? 1 : 0,
                    range, target == null ? "-" : target.getId(), target == null ? -1f : edge(ship, target),
                    target == null ? -1f : target.getFluxLevel(), target != null && overloadedOrVenting(target) ? 1 : 0,
                    target == null ? -1f : target.getHullLevel(),
                    nearest == null ? -1f : nearestDist, enemiesNear, alliesNear,
                    opening == null ? "-" : opening.getId(), opening == null ? -1f : openingDist));
        }
    }

    private static ShipAPI target(ShipAPI ship) {
        Object flagged = ship.getAIFlags() == null ? null : ship.getAIFlags().getCustom(AIFlags.MANEUVER_TARGET);
        ShipAPI target = flagged instanceof ShipAPI ? (ShipAPI) flagged : ship.getShipTarget();
        if (target == null || target.isFighter() || target.isDrone() || !target.isAlive() || target.getOwner() == ship.getOwner()) {
            return null;
        }
        return target;
    }

    /** The personality the AI is actually using: the override if one is set, else the captain's. */
    private static String personality(ShipAPI ship) {
        if (ship.getShipAI() != null && ship.getShipAI().getConfig() != null
                && ship.getShipAI().getConfig().personalityOverride != null) {
            return ship.getShipAI().getConfig().personalityOverride;
        }
        if (ship.getCaptain() != null && ship.getCaptain().getPersonalityAPI() != null) {
            return ship.getCaptain().getPersonalityAPI().getId();
        }
        return "-";
    }

    /** Longest non-missile range: the range a hull actually trades at, missiles aside. */
    private static float gunRange(ShipAPI ship) {
        float max = 0f;
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (weapon.isDecorative() || weapon.getType() == WeaponAPI.WeaponType.MISSILE) continue;
            max = Math.max(max, weapon.getRange());
        }
        return max;
    }

    private static boolean overloadedOrVenting(ShipAPI ship) {
        return ship.getFluxTracker() != null && ship.getFluxTracker().isOverloadedOrVenting();
    }

    private static float edge(ShipAPI a, ShipAPI b) {
        return Misc.getDistance(a.getLocation(), b.getLocation()) - a.getCollisionRadius() - b.getCollisionRadius();
    }
}
