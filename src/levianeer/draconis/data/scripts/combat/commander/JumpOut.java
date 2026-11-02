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
 * {@link #HULL_AT} and its hull loss over the last {@link #WINDOW} seconds predicts death within
 * {@link #CHARGE_MARGIN} times its jump charge time (the jump cannot be aborted, and a ship destroyed
 * mid-charge is lost anyway). Ordered through the task manager without a command point; the ship is
 * marked so the jump does not refund one.
 */
final class JumpOut {

    private static final Logger log = Global.getLogger(JumpOut.class);

    private static final float HULL_AT = 0.4f;
    private static final float WINDOW = 5f;
    private static final float CHARGE_MARGIN = 2f;
    private static final float SAMPLE_SECONDS = 0.5f;

    private final int side;
    /** Ship -> recent {time, hitpoints} samples, oldest first. */
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
            Deque<float[]> samples = history.computeIfAbsent(ship, s -> new ArrayDeque<>());
            samples.addLast(new float[]{t, ship.getHitpoints()});
            while (samples.size() > 1 && t - samples.peekFirst()[0] > WINDOW) samples.removeFirst();

            if (ship.getHullLevel() > HULL_AT) continue;
            if (ship.getFleetMember() != null) lowHull.add(ship.getFleetMember().getId());
            float[] oldest = samples.peekFirst();
            float span = t - oldest[0];
            if (span <= 0f) continue;
            float rate = (oldest[1] - ship.getHitpoints()) / span;
            if (rate <= 0f) continue;
            float timeToDeath = ship.getHitpoints() / rate;
            float charge = XLII_SystemHullModBase.getChargeTime(ship.getHullSize());
            if (timeToDeath > charge * CHARGE_MARGIN) continue;

            DeployedFleetMemberAPI member = fleet.getDeployedFleetMember(ship);
            if (member == null) continue;
            ship.setCustomData(XLII_SystemHullModBase.WarpDriveScript.NO_CP_REFUND_KEY, true);
            fleet.getTaskManager(false).orderRetreat(member, false, true);
            ordered.put(ship, ship.getFleetMember() == null ? "" : ship.getFleetMember().getId());
            log.info(String.format("DraconisCommander,jump,t=%.1f,ship=%s,hull=%.2f,hullLossPerSecond=%.0f,timeToDeath=%.1f,charge=%.0f,direct=%s",
                    t, ship.getName(), ship.getHullLevel(), rate, timeToDeath, charge, ship.isDirectRetreat()));
        }
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
