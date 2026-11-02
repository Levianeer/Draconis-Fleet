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
 * Pulls a ship that is about to die out of the fight, saving its DP. Vanilla ships never retreat on
 * their own. A ship qualifies once its hull is down to {@link #HULL_AT} and its loss of hull plus armour
 * over the last {@link #WINDOW} seconds predicts that both are gone within {@link #CHARGE_MARGIN} times
 * its jump charge time ({@link #EXPOSED_MARGIN} if it is overloaded or its shield is about to fail).
 * <p>
 * It disengages first and jumps second. The Transverse Jump charge slows the ship to a crawl for 5-9 s
 * and cannot be aborted, so ordering it under fire killed 70% of the ships in v1 and v2. Instead the
 * ship gets a plain retreat, which vanilla flies away from the enemy, and the jump is started only once
 * it has taken no damage for {@link #CLEAR} seconds. A ship that reaches the map edge first just leaves.
 * Ordered through the task manager without a command point; the ship is marked so the jump does not
 * refund one.
 */
final class JumpOut {

    private static final Logger log = Global.getLogger(JumpOut.class);

    private static final float HULL_AT = 0.6f;
    private static final float WINDOW = 3f;
    private static final float CHARGE_MARGIN = 2.5f;
    private static final float EXPOSED_MARGIN = 3.5f;
    /** At this flux a shield is about to fail. */
    private static final float SHIELD_FAILING = 0.95f;
    /** Seconds without damage before a disengaging ship starts its jump. */
    private static final float CLEAR = 1.5f;
    private static final float SAMPLE_SECONDS = 0.25f;

    /** A ship ordered out: when, its last hit, and when it started the jump or died (-1 = not yet). */
    private static final class Order {
        final FleetMemberAPI member;
        final float orderedAt;
        float structure;
        float lastHitAt;
        float jumpAt = -1f;
        float diedAt = -1f;

        Order(FleetMemberAPI member, float t, float structure) {
            this.member = member;
            this.orderedAt = t;
            this.structure = structure;
            this.lastHitAt = t;
        }
    }

    private final int side;
    /** Ship -> recent {time, hull + armour} samples, oldest first. */
    private final Map<ShipAPI, Deque<float[]>> history = new IdentityHashMap<>();
    private final Map<ShipAPI, Order> ordered = new IdentityHashMap<>();
    /** Fleet member ids of ships that fell to {@link #HULL_AT}, ordered out or not. */
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
            Order order = ordered.get(ship);
            if (order != null) {
                follow(ship, order, t);
                continue;
            }
            if (!Commander.controllable(engine, ship, side) || ship.isRetreating()) continue;
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
            if (member == null || ship.getFleetMember() == null) continue;
            ship.setCustomData(XLII_SystemHullModBase.WarpDriveScript.NO_CP_REFUND_KEY, true);
            fleet.getTaskManager(false).orderRetreat(member, false, false);
            ordered.put(ship, new Order(ship.getFleetMember(), t, structure));
            history.remove(ship);
            log.info(String.format("DraconisCommander,disengage,t=%.1f,ship=%s,hull=%.2f,armour=%.0f,lossPerSecond=%.0f,timeToDeath=%.1f,charge=%.0f,exposed=%s,direct=%s",
                    t, ship.getName(), ship.getHullLevel(), armour(ship), rate, timeToDeath, charge, exposed, ship.isDirectRetreat()));
        }
    }

    /** Starts the jump once a disengaging ship is clear of fire, and logs when an ordered ship dies. */
    private void follow(ShipAPI ship, Order order, float t) {
        if (order.diedAt >= 0f) return;
        if (ship.isHulk() || !ship.isAlive()) {
            order.diedAt = t;
            log.info(String.format("DraconisCommander,lost,t=%.1f,ship=%s,phase=%s,sinceOrder=%.1f,sinceJump=%.1f",
                    t, ship.getName(), order.jumpAt < 0f ? "disengaging" : "charging", t - order.orderedAt,
                    order.jumpAt < 0f ? -1f : t - order.jumpAt));
            return;
        }
        if (order.jumpAt >= 0f) return;
        float structure = ship.getHitpoints() + armour(ship);
        if (structure < order.structure) order.lastHitAt = t;
        order.structure = structure;
        if (t - order.lastHitAt < CLEAR) return;
        ship.setRetreating(true, true);
        order.jumpAt = t;
        log.info(String.format("DraconisCommander,jump,t=%.1f,ship=%s,hull=%.2f,afterDisengage=%.1f",
                t, ship.getName(), ship.getHullLevel(), t - order.orderedAt));
    }

    private static float armour(ShipAPI ship) {
        float total = 0f;
        for (float[] column : ship.getArmorGrid().getGrid()) {
            for (float cell : column) total += cell;
        }
        return total;
    }

    /**
     * Battle-end summary: ships ordered out, how many got out and how many of those jumped, how many died
     * disengaging or charging, and low-hull ships lost without an order.
     */
    void report(CombatEngineAPI engine, float t) {
        CombatFleetManagerAPI fleet = engine.getFleetManager(side);
        Set<String> lost = new HashSet<>();
        for (FleetMemberAPI m : fleet.getDisabledCopy()) lost.add(m.getId());
        for (FleetMemberAPI m : fleet.getDestroyedCopy()) lost.add(m.getId());
        Set<String> orderedIds = new HashSet<>();
        int out = 0;
        int jumped = 0;
        int diedDisengaging = 0;
        int diedCharging = 0;
        float savedDp = 0f;
        for (Order order : ordered.values()) {
            orderedIds.add(order.member.getId());
            if (order.jumpAt >= 0f) jumped++;
            if (!lost.contains(order.member.getId())) {
                out++;
                savedDp += order.member.getDeploymentPointsCost();
            } else if (order.jumpAt >= 0f) {
                diedCharging++;
            } else {
                diedDisengaging++;
            }
        }
        int lostWithoutJump = 0;
        for (String id : lowHull) {
            if (lost.contains(id) && !orderedIds.contains(id)) lostWithoutJump++;
        }
        log.info(String.format("DraconisCommander,jumpSummary,t=%.0f,ordered=%d,out=%d,savedDp=%.0f,jumped=%d,diedDisengaging=%d,diedCharging=%d,lowHullLostWithoutJump=%d",
                t, ordered.size(), out, savedDp, jumped, diedDisengaging, diedCharging, lostWithoutJump));
    }
}
