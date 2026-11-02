package levianeer.draconis.data.scripts.combat.commander;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import levianeer.draconis.data.scripts.hullmods.XLII_SystemHullModBase;
import org.apache.log4j.Logger;

/**
 * Pulls a ship that is about to die out of the fight with the Draconis Transverse Jump, saving its DP.
 * Vanilla ships never retreat on their own. A ship qualifies once its hull is down to
 * {@link #HULL_AT} and its loss of hull plus armour over the last {@link #WINDOW} seconds predicts that
 * both are gone within {@link #CHARGE_MARGIN} times its jump charge time ({@link #EXPOSED_MARGIN} if it
 * is overloaded or its shield is about to fail). The jump cannot be aborted and a ship destroyed
 * mid-charge is lost anyway, so the order has to land early: v1 (hull 0.4, hull only, 5 s window, 2x)
 * saw 33 of 44 ordered ships die charging. Ordered through the task manager without a command point;
 * the ship is marked so the jump does not refund one.
 */
final class JumpOut {

    private static final Logger log = Global.getLogger(JumpOut.class);

    private static final float HULL_AT = 0.6f;
    private static final float WINDOW = 3f;
    private static final float CHARGE_MARGIN = 2.5f;
    private static final float EXPOSED_MARGIN = 3.5f;
    /** At this flux a shield is about to fail. */
    private static final float SHIELD_FAILING = 0.95f;
    private static final float SAMPLE_SECONDS = 0.25f;

    private final int side;
    /** Ship -> recent {time, hull + armour} samples, oldest first. */
    private final Map<ShipAPI, Deque<float[]>> history = new IdentityHashMap<>();
    private final Map<ShipAPI, String> ordered = new IdentityHashMap<>();
    /** Fleet member ids of ships that fell to {@link #HULL_AT}, jumped or not. */
    private final Set<String> lowHull = new HashSet<>();
    private float sinceSample;

    JumpOut(int side) {
        this.side = side;
    }

    void advance(CombatEngineAPI engine, float amount) {
        sinceSample += amount;
        if (sinceSample < SAMPLE_SECONDS) return;
        sinceSample = 0f;
        float t = engine.getTotalElapsedTime(false);
        CombatFleetManagerAPI fleet = engine.getFleetManager(side);

        for (ShipAPI ship : engine.getShips()) {
            if (!Commander.controllable(engine, ship, side) || ship.isRetreating() || ordered.containsKey(ship)) continue;
            if (ship.getListeners(XLII_SystemHullModBase.WarpDriveScript.class).isEmpty()) continue;
            float structure = ship.getHitpoints() + armour(ship);
            Deque<float[]> samples = history.computeIfAbsent(ship, s -> new ArrayDeque<>());
            samples.addLast(new float[]{t, structure});
            while (samples.size() > 1 && t - samples.peekFirst()[0] > WINDOW) samples.removeFirst();

            if (ship.getHullLevel() > HULL_AT) continue;
            if (ship.getFleetMember() != null) lowHull.add(ship.getFleetMember().getId());
            float[] oldest = samples.peekFirst();
            float span = t - oldest[0];
            if (span <= 0f) continue;
            float rate = (oldest[1] - structure) / span;
            if (rate <= 0f) continue;
            float timeToDeath = structure / rate;
            float charge = XLII_SystemHullModBase.getChargeTime(ship.getHullSize());
            boolean exposed = ship.getFluxTracker().isOverloaded() || ship.getFluxLevel() >= SHIELD_FAILING;
            if (timeToDeath > charge * (exposed ? EXPOSED_MARGIN : CHARGE_MARGIN)) continue;

            DeployedFleetMemberAPI member = fleet.getDeployedFleetMember(ship);
            if (member == null) continue;
            ship.setCustomData(XLII_SystemHullModBase.WarpDriveScript.NO_CP_REFUND_KEY, true);
            fleet.getTaskManager(false).orderRetreat(member, false, true);
            ordered.put(ship, ship.getFleetMember() == null ? "" : ship.getFleetMember().getId());
            log.info(String.format("DraconisCommander,jump,t=%.1f,ship=%s,hull=%.2f,armour=%.0f,lossPerSecond=%.0f,timeToDeath=%.1f,charge=%.0f,exposed=%s,direct=%s",
                    t, ship.getName(), ship.getHullLevel(), armour(ship), rate, timeToDeath, charge, exposed, ship.isDirectRetreat()));
        }
    }

    private static float armour(ShipAPI ship) {
        float total = 0f;
        for (float[] column : ship.getArmorGrid().getGrid()) {
            for (float cell : column) total += cell;
        }
        return total;
    }

    /** Battle-end summary: jumps ordered, ships that got out, died charging, and low-hull ships lost without a jump. */
    void report(CombatEngineAPI engine, float t) {
        CombatFleetManagerAPI fleet = engine.getFleetManager(side);
        Set<String> lost = new HashSet<>();
        for (FleetMemberAPI m : fleet.getDisabledCopy()) lost.add(m.getId());
        for (FleetMemberAPI m : fleet.getDestroyedCopy()) lost.add(m.getId());
        int out = 0;
        int diedCharging = 0;
        float savedDp = 0f;
        for (Map.Entry<ShipAPI, String> entry : ordered.entrySet()) {
            if (lost.contains(entry.getValue())) {
                diedCharging++;
            } else {
                out++;
                if (entry.getKey().getFleetMember() != null) savedDp += entry.getKey().getFleetMember().getDeploymentPointsCost();
            }
        }
        int lostWithoutJump = 0;
        for (String id : lowHull) {
            if (lost.contains(id) && !ordered.containsValue(id)) lostWithoutJump++;
        }
        log.info(String.format("DraconisCommander,jumpSummary,t=%.0f,ordered=%d,out=%d,savedDp=%.0f,diedCharging=%d,lowHullLostWithoutJump=%d",
                t, ordered.size(), out, savedDp, diedCharging, lostWithoutJump));
    }
}
