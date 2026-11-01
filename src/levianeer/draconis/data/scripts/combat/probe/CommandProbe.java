package levianeer.draconis.data.scripts.combat.probe;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.AdmiralAIPlugin;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.scripts.XLII_WarpInScript;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

/**
 * Phase 1 probes 3, 7 and 8, run by the {@code xlii_probe_command} mission on the AI side (owner 1):
 * <ol>
 *   <li><b>Admiral replacement and deployment.</b> On the first frame the side's admiral is replaced
 *   by {@link ProbeAdmiral}, which issues no orders and deploys whatever is still in reserve itself
 *   with {@code spawnFleetMember}. Logged: what was already deployed, whether any assignment or
 *   retreat still appears afterwards (it should not, with vanilla's admiral gone), each deployment.</li>
 *   <li><b>Commanded warp-in.</b> At {@link #HOLD_AT} the Alwaid and both Thubans are sent to the
 *   off-map hold through {@link XLII_WarpInScript#holdForCommand} (the Thubans get the script, which
 *   normally only capitals have); at {@link #ARRIVE_AT} they are commanded in - the Alwaid behind our
 *   line, the Thubans onto the enemy's flank. Logged: arrival time and position error.</li>
 *   <li><b>Transverse Jump trigger.</b> At {@link #RETREAT_AT} one ship is ordered out with
 *   {@code orderRetreat(member, false, true)} and another with {@code setRetreating(true, true)}.
 *   Logged: whether each counts as a direct retreat, whether it jumps out, and command points.</li>
 * </ol>
 * Removable once answered.
 */
public class CommandProbe extends BaseEveryFrameCombatPlugin {

    private static final Logger log = Global.getLogger(CommandProbe.class);

    private static final int SIDE = 1;
    private static final float HOLD_AT = 20f;
    private static final float ARRIVE_AT = 40f;
    private static final float RETREAT_AT = 80f;
    /** How far behind our centre the Alwaid is brought in, and how far to the enemy's side the Thubans. */
    private static final float BEHIND = 1500f;
    private static final float FLANK = 2500f;
    /** Telegraph length in {@link XLII_WarpInScript}; arrival is expected this long after the command. */
    private static final float TELEGRAPH = 9f;

    private final IntervalUtil tick = new IntervalUtil(1f, 1f);
    private boolean admiralSet;
    private boolean holdDone;
    private boolean arriveDone;
    private boolean retreatDone;
    private ProbeAdmiral admiral;
    /** Ship -> commanded point, until it arrives. */
    private final Map<ShipAPI, Vector2f> commandedTo = new IdentityHashMap<>();
    private final Map<ShipAPI, String> retreating = new IdentityHashMap<>();
    private boolean reported;

    @Override
    public void init(CombatEngineAPI engine) {
        admiralSet = holdDone = arriveDone = retreatDone = reported = false;
        admiral = null;
        commandedTo.clear();
        retreating.clear();
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;
        float t = engine.getTotalElapsedTime(false);
        CombatFleetManagerAPI fleet = engine.getFleetManager(SIDE);
        CombatTaskManagerAPI tasks = fleet.getTaskManager(false);

        if (!admiralSet) {
            admiralSet = true;
            log.info(String.format("DraconisProbe3,start,t=%.2f,deployed=%d,reserves=%d,cp=%d,vanillaAdmiral=%s",
                    t, ships(fleet.getDeployedCopy()), ships(fleet.getReservesCopy()), tasks.getCommandPointsLeft(),
                    fleet.getAdmiralAI() == null ? "null" : fleet.getAdmiralAI().getClass().getName()));
            admiral = new ProbeAdmiral(fleet);
            fleet.setAdmiralAI(admiral);
        }

        List<ShipAPI> ours = new ArrayList<>();
        List<ShipAPI> theirs = new ArrayList<>();
        for (ShipAPI ship : engine.getShips()) {
            if (ship.isFighter() || ship.isStationModule() || !ship.isAlive() || ship.isHulk()) continue;
            if (ship.getOwner() == SIDE) ours.add(ship);
            else if (ship.getOwner() == 1 - SIDE) theirs.add(ship);
        }

        if (!holdDone && t >= HOLD_AT) {
            holdDone = true;
            for (ShipAPI ship : ours) {
                String hull = ship.getHullSpec().getBaseHullId();
                if (!hull.startsWith("XLII_alwaid") && !hull.startsWith("XLII_thuban")) continue;
                XLII_WarpInScript warp = warpScript(ship);
                warp.holdForCommand();
                log.info(String.format("DraconisProbe3,hold,t=%.1f,ship=%s", t, ship.getName()));
            }
        }

        if (holdDone && !arriveDone && t >= ARRIVE_AT && !onMap(ours).isEmpty() && !theirs.isEmpty()) {
            arriveDone = true;
            Vector2f usC = centroid(onMap(ours));
            Vector2f themC = centroid(theirs);
            Vector2f axis = Misc.getUnitVector(usC, themC);
            float side = 1f;
            for (ShipAPI ship : ours) {
                List<XLII_WarpInScript> scripts = ship.getListeners(XLII_WarpInScript.class);
                if (scripts.isEmpty() || !scripts.get(0).isHolding()) continue;
                Vector2f point;
                float facing;
                if (ship.getHullSpec().getBaseHullId().startsWith("XLII_alwaid")) {
                    point = new Vector2f(usC.x - axis.x * BEHIND, usC.y - axis.y * BEHIND);
                    facing = Misc.getAngleInDegrees(axis);
                } else {
                    point = new Vector2f(themC.x - axis.y * FLANK * side, themC.y + axis.x * FLANK * side);
                    facing = Misc.getAngleInDegrees(point, themC);
                    side = -side;
                }
                scripts.get(0).commandArrival(point, facing);
                commandedTo.put(ship, point);
                log.info(String.format("DraconisProbe3,command,t=%.1f,ship=%s,x=%.0f,y=%.0f,facing=%.0f",
                        t, ship.getName(), point.x, point.y, facing));
            }
        }

        if (!retreatDone && t >= RETREAT_AT) {
            retreatDone = true;
            boolean viaOrder = true;
            for (ShipAPI ship : ours) {
                if (commandedTo.containsKey(ship) || ship.isRetreating()) continue;
                int cpBefore = tasks.getCommandPointsLeft();
                if (viaOrder) {
                    DeployedFleetMemberAPI member = fleet.getDeployedFleetMember(ship);
                    if (member == null) continue;
                    tasks.orderRetreat(member, false, true);
                } else {
                    ship.setRetreating(true, true);
                }
                String how = viaOrder ? "orderRetreat" : "setRetreating";
                retreating.put(ship, how);
                log.info(String.format("DraconisProbe3,retreat,t=%.1f,ship=%s,how=%s,retreating=%s,direct=%s,cpBefore=%d,cpAfter=%d",
                        t, ship.getName(), how, ship.isRetreating(), ship.isDirectRetreat(), cpBefore, tasks.getCommandPointsLeft()));
                if (!viaOrder) break;
                viaOrder = false;
            }
        }

        tick.advance(amount);
        if (tick.intervalElapsed()) {
            for (Map.Entry<ShipAPI, Vector2f> entry : new ArrayList<>(commandedTo.entrySet())) {
                ShipAPI ship = entry.getKey();
                List<XLII_WarpInScript> scripts = ship.getListeners(XLII_WarpInScript.class);
                if (!scripts.isEmpty() && scripts.get(0).isHolding()) continue;
                log.info(String.format("DraconisProbe3,arrived,t=%.1f,ship=%s,expectedAt=%.1f,err=%.0f,alive=%s",
                        t, ship.getName(), ARRIVE_AT + TELEGRAPH, Misc.getDistance(ship.getLocation(), entry.getValue()), ship.isAlive()));
                commandedTo.remove(ship);
            }
            for (Map.Entry<ShipAPI, String> entry : new ArrayList<>(retreating.entrySet())) {
                ShipAPI ship = entry.getKey();
                if (engine.isEntityInPlay(ship) && ship.isAlive()) continue;
                log.info(String.format("DraconisProbe3,gone,t=%.1f,ship=%s,how=%s,alive=%s,cp=%d,retreatedTotal=%d",
                        t, ship.getName(), entry.getValue(), ship.isAlive(), tasks.getCommandPointsLeft(),
                        ships(fleet.getRetreatedCopy())));
                retreating.remove(ship);
            }
            if ((int) t % 5 == 0) {
                int assigned = 0;
                int retreatingNow = 0;
                for (ShipAPI ship : ours) {
                    if (tasks.getAssignmentFor(ship) != null) assigned++;
                    if (ship.isRetreating()) retreatingNow++;
                }
                log.info(String.format("DraconisProbe3,state,t=%.0f,deployed=%d,reserves=%d,assigned=%d,retreating=%d,fullRetreat=%s,cp=%d,admiralCalls=%d,admiralIsOurs=%s",
                        t, ships(fleet.getDeployedCopy()), ships(fleet.getReservesCopy()), assigned, retreatingNow,
                        tasks.isInFullRetreat(), tasks.getCommandPointsLeft(), admiral.calls, fleet.getAdmiralAI() == admiral));
            }
        }

        if (!reported && engine.isCombatOver()) {
            reported = true;
            log.info(String.format("DraconisProbe3,end,t=%.0f,stillCommanded=%d,stillRetreating=%d,admiralCalls=%d",
                    t, commandedTo.size(), retreating.size(), admiral.calls));
        }
    }

    private static XLII_WarpInScript warpScript(ShipAPI ship) {
        List<XLII_WarpInScript> scripts = ship.getListeners(XLII_WarpInScript.class);
        if (!scripts.isEmpty()) return scripts.get(0);
        XLII_WarpInScript script = new XLII_WarpInScript(ship);
        ship.addListener(script);
        return script;
    }

    /** Ships actually inside the map, not waiting in the off-map hold. */
    private static List<ShipAPI> onMap(List<ShipAPI> ships) {
        CombatEngineAPI engine = Global.getCombatEngine();
        List<ShipAPI> result = new ArrayList<>();
        for (ShipAPI ship : ships) {
            Vector2f loc = ship.getLocation();
            if (Math.abs(loc.x) <= engine.getMapWidth() / 2f && Math.abs(loc.y) <= engine.getMapHeight() / 2f) result.add(ship);
        }
        return result;
    }

    private static Vector2f centroid(List<ShipAPI> ships) {
        float x = 0f;
        float y = 0f;
        for (ShipAPI ship : ships) {
            x += ship.getLocation().x;
            y += ship.getLocation().y;
        }
        return new Vector2f(x / ships.size(), y / ships.size());
    }

    private static int ships(List<FleetMemberAPI> members) {
        int n = 0;
        for (FleetMemberAPI member : members) {
            if (!member.isFighterWing()) n++;
        }
        return n;
    }

    /** Issues no orders; deploys anything left in reserve along our own edge. */
    static class ProbeAdmiral implements AdmiralAIPlugin {

        private final CombatFleetManagerAPI fleet;
        int calls;

        ProbeAdmiral(CombatFleetManagerAPI fleet) {
            this.fleet = fleet;
        }

        @Override
        public void preCombat() {
            log.info("DraconisProbe3,preCombat");
        }

        @Override
        public void advance(float amount) {
            calls++;
            List<FleetMemberAPI> reserves = fleet.getReservesCopy();
            if (reserves.isEmpty()) return;
            CombatEngineAPI engine = Global.getCombatEngine();
            float y = engine.getMapHeight() / 2f - 1500f;
            float x = -2000f;
            for (FleetMemberAPI member : reserves) {
                if (member.isFighterWing()) continue;
                ShipAPI ship = fleet.spawnFleetMember(member, new Vector2f(x, y), 270f, 0f);
                log.info(String.format("DraconisProbe3,deploy,t=%.1f,member=%s,x=%.0f,y=%.0f,ship=%s",
                        engine.getTotalElapsedTime(false), member.getShipName(), x, y, ship == null ? "null" : ship.getName()));
                x += 800f;
            }
        }
    }
}
