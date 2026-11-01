package levianeer.draconis.data.scripts.combat.commander;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.scripts.combat.commander.WorldModel.Unit;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

/**
 * The Draconis commander (third combat AI, {@code .claude/systems/draconis-commander.md}). Phase 2:
 * one task force of every ship on its side, driven by a world model rebuilt twice a second, with the
 * side's admiral replaced so vanilla issues no orders or retreats over it. Only ever controls ships of
 * its own side, and never the player's ship.
 * <p>
 * Also records the Phase 2 functional checks - cohesion, isolation, and how much of the time vanilla
 * flew our ships versus our steering stepping in - in a summary line at battle end.
 * <p>
 * With {@code shipControl} false it only replaces the admiral (deploy everything, no orders, no
 * retreats) and records: the floor check, which in a mirror should play like vanilla with no orders.
 */
public class Commander extends BaseEveryFrameCombatPlugin {

    private static final Logger log = Global.getLogger(Commander.class);

    private static final float DECIDE_SECONDS = 0.5f;
    /** No ally within this, edge to edge, counts as isolated. */
    private static final float ISOLATED = 1500f;

    private final int side;
    private final boolean shipControl;
    private final WorldModel world;
    private final TaskForce force;
    private final Map<ShipAPI, ShipControl> controls = new IdentityHashMap<>();
    private final IntervalUtil decide = new IntervalUtil(DECIDE_SECONDS, DECIDE_SECONDS);
    private final IntervalUtil second = new IntervalUtil(1f, 1f);
    private boolean admiralSet;
    private boolean reported;

    // Phase 2 functional checks
    private final Map<TaskForce.Mode, Integer> modeSeconds = new EnumMap<>(TaskForce.Mode.class);
    private final Map<ShipControl.Action, Integer> actionSeconds = new EnumMap<>(ShipControl.Action.class);
    private float spreadSum;
    private int spreadN;
    private int memberSeconds;
    private int isolatedSeconds;

    public Commander(int side, boolean shipControl) {
        this.side = side;
        this.shipControl = shipControl;
        this.world = new WorldModel(side);
        this.force = new TaskForce(world);
    }

    @Override
    public void init(CombatEngineAPI engine) {
        controls.clear();
        admiralSet = reported = false;
        modeSeconds.clear();
        actionSeconds.clear();
        spreadSum = 0f;
        spreadN = memberSeconds = isolatedSeconds = 0;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;
        float t = engine.getTotalElapsedTime(false);

        if (!admiralSet) {
            admiralSet = true;
            engine.getFleetManager(side).setAdmiralAI(new CommanderAdmiral(engine.getFleetManager(side), side));
            log.info(String.format("DraconisCommander,start,t=%.2f,side=%d,shipControl=%s", t, side, shipControl));
        }

        decide.advance(amount);
        if (decide.intervalElapsed()) {
            world.rebuild(engine);
            syncControls(engine);
            if (shipControl) force.decide(t, controls);
        }
        if (shipControl) {
            for (ShipControl c : controls.values()) c.advance();
        }

        second.advance(amount);
        if (second.intervalElapsed()) sample(t);

        if (!reported && engine.isCombatOver()) {
            reported = true;
            report(t);
        }
    }

    /** Only ships of our side, never fighters, stations or the player's ship. */
    private boolean controllable(CombatEngineAPI engine, ShipAPI ship) {
        return ship.getOwner() == side && !ship.isFighter() && !ship.isStation() && !ship.isStationModule()
                && ship != engine.getPlayerShip() && ship.isAlive() && !ship.isHulk();
    }

    private void syncControls(CombatEngineAPI engine) {
        for (Unit u : world.ours) {
            if (controllable(engine, u.ship) && !controls.containsKey(u.ship)) controls.put(u.ship, new ShipControl(u.ship));
        }
        for (Iterator<Map.Entry<ShipAPI, ShipControl>> it = controls.entrySet().iterator(); it.hasNext(); ) {
            if (!controllable(engine, it.next().getKey())) it.remove();
        }
    }

    private void sample(float t) {
        List<Unit> members = world.ours;
        modeSeconds.merge(force.mode, 1, Integer::sum);
        Map<ShipControl.Action, Integer> actions = new EnumMap<>(ShipControl.Action.class);
        for (ShipControl c : controls.values()) {
            actions.merge(c.action, 1, Integer::sum);
            actionSeconds.merge(c.action, 1, Integer::sum);
        }

        float spread = 0f;
        int isolated = 0;
        float flux = 0f;
        int firing = 0;
        int venting = 0;
        if (!members.isEmpty()) {
            Vector2f centre = WorldModel.centroid(members);
            for (Unit m : members) {
                spread += Misc.getDistance(m.loc(), centre);
                float nearest = Float.MAX_VALUE;
                for (Unit o : members) {
                    if (o == m) continue;
                    nearest = Math.min(nearest, Misc.getDistance(m.loc(), o.loc()) - m.ship.getCollisionRadius() - o.ship.getCollisionRadius());
                }
                if (members.size() > 1 && nearest > ISOLATED) isolated++;
                flux += m.ship.getFluxLevel();
                if (m.ship.getFluxTracker().isVenting()) venting++;
                for (WeaponAPI weapon : m.ship.getAllWeapons()) {
                    if (!weapon.isDecorative() && weapon.isFiring()) {
                        firing++;
                        break;
                    }
                }
            }
            spread /= members.size();
            spreadSum += spread;
            spreadN++;
            memberSeconds += members.size();
            isolatedSeconds += isolated;
        }
        log.info(String.format("DraconisCommander,fleet,t=%.0f,mode=%s,clusterRatio=%s,threatRatio=%s,members=%d,enemies=%d,"
                        + "free=%d,slot=%d,backOff=%d,regroup=%d,anchorGap=%.0f,spread=%.0f,isolated=%d,meanFlux=%.2f,firing=%d,venting=%d",
                t, shipControl ? force.mode : "OFF", TaskForce.fmt(force.clusterRatio),
                TaskForce.fmt(force.threatRatio), members.size(), world.theirs.size(),
                count(actions, ShipControl.Action.FREE), count(actions, ShipControl.Action.SLOT),
                count(actions, ShipControl.Action.BACK_OFF), count(actions, ShipControl.Action.REGROUP),
                members.isEmpty() || force.anchor == null ? 0f : Misc.getDistance(WorldModel.centroid(members), force.anchor),
                spread, isolated, members.isEmpty() ? 0f : flux / members.size(), firing, venting));
    }

    private static int count(Map<ShipControl.Action, Integer> map, ShipControl.Action action) {
        return map.getOrDefault(action, 0);
    }

    private void report(float t) {
        int vents = 0;
        for (ShipControl c : controls.values()) vents += c.vents;
        int total = 0;
        for (int n : actionSeconds.values()) total += n;
        log.info(String.format("DraconisCommander,summary,t=%.0f,shipControl=%s,attack=%d,regroup=%d,meanSpread=%s,isolatedShare=%s,"
                        + "freeShare=%s,slotShare=%s,backOffShare=%s,regroupShare=%s,vents=%d",
                t, shipControl, modeSeconds.getOrDefault(TaskForce.Mode.ATTACK, 0), modeSeconds.getOrDefault(TaskForce.Mode.REGROUP, 0),
                spreadN == 0 ? "-" : String.format("%.0f", spreadSum / spreadN),
                memberSeconds == 0 ? "-" : String.format("%.2f", isolatedSeconds / (float) memberSeconds),
                share(ShipControl.Action.FREE, total), share(ShipControl.Action.SLOT, total),
                share(ShipControl.Action.BACK_OFF, total), share(ShipControl.Action.REGROUP, total), vents));
    }

    private String share(ShipControl.Action action, int total) {
        return total == 0 ? "-" : String.format("%.2f", actionSeconds.getOrDefault(action, 0) / (float) total);
    }
}
