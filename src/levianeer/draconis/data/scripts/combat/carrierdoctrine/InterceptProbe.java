package levianeer.draconis.data.scripts.combat.carrierdoctrine;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

/**
 * Throwaway experiment: does a soft INTERCEPT on an enemy hull pull our hulls onto it and make them
 * shoot it, without pinning them the way forcibly-obeyed position orders did (hulls sitting at full
 * flux, unable to back off)? The adaptive admiral's "focus the flagship" depends on this.
 * <p>
 * Hulls alternate into two halves; every {@link #PHASE_SECONDS} one half holds INTERCEPT on the
 * target and the other holds nothing, then they swap. Carriers are included (FortySecond is mostly
 * carriers, so leaving them out left 1-3 hulls) and flagged per row so they can be split out. Grep starsector.log for {@code DraconisIntercept}. The admiral is
 * left running.
 */
public class InterceptProbe {

    private static final Logger log = Global.getLogger(InterceptProbe.class);

    private static final float PHASE_SECONDS = 40f;
    private static final CombatAssignmentType ORDER = CombatAssignmentType.INTERCEPT;
    private static final float HIGH_FLUX = 0.9f;

    private final int owner;
    private final IntervalUtil sample = new IntervalUtil(1f, 1f);
    private final Map<ShipAPI, Boolean> groupA = new IdentityHashMap<>();
    private final Map<ShipAPI, Float> lastHull = new IdentityHashMap<>();
    // [ordered, free][samples, distSum, aimingAtTarget, highFlux, hullLost, held]
    private final float[][] sums = new float[2][6];
    private ShipAPI target;
    private AssignmentInfo assignment;
    private String lastAssignState;
    private int recreations = 0;
    private float elapsed = 0f;
    private int phase = 0;

    public InterceptProbe(int owner) {
        this.owner = owner;
        log.info("DraconisIntercept,header,t,phase,ship,carrier,group,ordered,held,heldType,targetDist,aiming,flux,hull,target,targetHull");
    }

    public void advance(float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine.isPaused()) return;
        elapsed += amount;
        CombatTaskManagerAPI taskManager = engine.getFleetManager(owner).getTaskManager(false);

        int nowPhase = (int) (elapsed / PHASE_SECONDS);
        boolean phaseChanged = nowPhase != phase;
        if (phaseChanged) {
            logPhaseSummary();
            phase = nowPhase;
        }

        List<ShipAPI> hulls = new ArrayList<>();
        for (ShipAPI ship : engine.getShips()) {
            if (!ship.isAlive() || ship.isFighter() || ship.isDrone() || ship.isStationModule()) continue;
            if (ship.getOwner() == owner) hulls.add(ship);
        }

        ShipAPI picked = pickTarget(engine);
        // Swapping halves means a fresh assignment, not stripping it per hull: removing the whole task
        // frees the old half without issuing any other order to it.
        if (picked != target || phaseChanged || picked == null) {
            if (assignment != null) taskManager.removeAssignment(assignment);
            assignment = null;
            target = picked;
        }
        if (target != null) {
            // Looked up live every frame rather than trusting the stored reference: the admiral's
            // orderSearchAndDestroy() at 40-60s cancels all assignments, which left the first run
            // handing out a dead one for the rest of the phase.
            DeployedFleetMemberAPI member = findDeployed(engine, target);
            AssignmentInfo live = member == null ? null : taskManager.getAssignmentInfoForTarget(member);
            String reason = null;
            if (live == null && member != null) {
                reason = assignment == null ? "new" : "cancelled";
                live = taskManager.createAssignment(ORDER, member, false);
                recreations++;
            }
            String state = "target=" + target.getName() + ",member=" + (member != null) + ",created="
                    + (live != null) + ",type=" + (live == null ? "none" : live.getType());
            if (!state.equals(lastAssignState)) {
                log.info("DraconisIntercept,assign,t=" + Math.round(elapsed) + ",reason=" + reason + "," + state);
                lastAssignState = state;
            }
            assignment = live;
        }

        for (ShipAPI ship : hulls) {
            if (!groupA.containsKey(ship)) groupA.put(ship, groupA.size() % 2 == 0);
            if (assignment == null || !isOrdered(ship)) continue;
            DeployedFleetMemberAPI member = findDeployed(engine, ship);
            // Reapplied every frame, as the STRIKE work found an assignment can be silently dropped.
            if (member != null && !holdsOrder(taskManager, ship)) {
                taskManager.giveAssignment(member, assignment, false);
            }
        }

        sample.advance(amount);
        if (!sample.intervalElapsed() || target == null) return;

        for (ShipAPI ship : hulls) {
            boolean ordered = isOrdered(ship);
            boolean held = holdsOrder(taskManager, ship);
            AssignmentInfo info = taskManager.getAssignmentFor(ship);
            String heldType = info == null ? "none" : String.valueOf(info.getType());
            float dist = Misc.getDistance(ship.getLocation(), target.getLocation());
            boolean aiming = ship.getShipTarget() == target;
            Float prev = lastHull.put(ship, ship.getHullLevel());
            float lost = prev == null ? 0f : Math.max(0f, prev - ship.getHullLevel());
            float[] s = sums[ordered ? 0 : 1];
            s[0]++;
            s[1] += dist;
            if (aiming) s[2]++;
            if (ship.getFluxLevel() >= HIGH_FLUX) s[3]++;
            s[4] += lost;
            if (held) s[5]++;
            log.info(String.format("DraconisIntercept,row,%.0f,%d,%s,%d,%s,%d,%d,%s,%.0f,%d,%.2f,%.2f,%s,%.2f",
                    elapsed, phase, ship.getName(), ship.getNumFighterBays() > 0 ? 1 : 0, groupA.get(ship) ? "A" : "B",
                    ordered ? 1 : 0, held ? 1 : 0, heldType, dist, aiming ? 1 : 0, ship.getFluxLevel(), ship.getHullLevel(), target.getName(),
                    target.getHullLevel()));
        }
    }

    /** Compared by type and target ship, not by AssignmentInfo identity: the first run read 0% held
     * on every hull, which identity comparison against a per-call wrapper would also produce. */
    private boolean holdsOrder(CombatTaskManagerAPI taskManager, ShipAPI ship) {
        AssignmentInfo info = taskManager.getAssignmentFor(ship);
        return info != null && info.getType() == ORDER && info.getTarget() instanceof DeployedFleetMemberAPI
                && ((DeployedFleetMemberAPI) info.getTarget()).getShip() == target;
    }

    /** Through the deployed list, as the legacy controller's working STRIKE did, rather than
     * {@code getDeployedFleetMember}, which the first run of this probe used and never saw held. */
    private static DeployedFleetMemberAPI findDeployed(CombatEngineAPI engine, ShipAPI ship) {
        for (DeployedFleetMemberAPI member : engine.getFleetManager(ship.getOwner()).getDeployedCopyDFM()) {
            if (member.getShip() == ship) return member;
        }
        return null;
    }

    private boolean isOrdered(ShipAPI ship) {
        return groupA.get(ship) == (phase % 2 == 0);
    }

    /** The player flagship (in the SIM harness, the AI-flown frigate), else the largest enemy hull. */
    private ShipAPI pickTarget(CombatEngineAPI engine) {
        ShipAPI flagship = engine.getPlayerShip();
        if (flagship != null && flagship.isAlive() && flagship.getOwner() != owner && !flagship.isFighter()) {
            return flagship;
        }
        ShipAPI best = null;
        for (ShipAPI ship : engine.getShips()) {
            if (!ship.isAlive() || ship.getOwner() == owner || ship.isFighter() || ship.isDrone()
                    || ship.isStationModule() || ship.getOwner() > 1) continue;
            if (best == null || ship.getHullSize().ordinal() > best.getHullSize().ordinal()
                    || (ship.getHullSize() == best.getHullSize() && ship.getMaxHitpoints() > best.getMaxHitpoints())) {
                best = ship;
            }
        }
        return best;
    }

    private void logPhaseSummary() {
        for (int g = 0; g < 2; g++) {
            float[] s = sums[g];
            float n = Math.max(1f, s[0]);
            log.info(String.format("DraconisIntercept,summary,phase=%d,%s,samples=%.0f,meanTargetDist=%.0f,aimingAtTarget=%.0f%%,highFlux=%.0f%%,hullLostPerShipSec=%.4f,held=%.0f%%,recreations=%d,target=%s",
                    phase, g == 0 ? "ORDERED" : "FREE", s[0], s[1] / n, 100f * s[2] / n, 100f * s[3] / n,
                    s[4] / n, 100f * s[5] / n, recreations, target == null ? "none" : target.getName()));
            sums[g] = new float[6];
        }
        recreations = 0;
    }
}
