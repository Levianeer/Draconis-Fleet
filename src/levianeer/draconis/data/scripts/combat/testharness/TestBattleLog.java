package levianeer.draconis.data.scripts.combat.testharness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import org.apache.log4j.Logger;

/**
 * Per-battle record for the test missions: whole-battle exchange, a deployment check, and the
 * result, written to the log and to {@link TestRunLedger}. Added by {@link TestMissions}, so it only
 * ever runs in a test mission.
 * <p>
 * Exchange counts armour and hull damage, not shield damage, and includes damage to fighters on
 * both sides (the author's yardstick: wings are hit points too). Damage is credited by the target's
 * side, so the rare friendly-fire hit counts as the enemy's.
 */
public class TestBattleLog extends BaseEveryFrameCombatPlugin implements DamageListener {

    private static final Logger log = Global.getLogger(TestBattleLog.class);

    private static final int DRACONIS = 1;
    private static final int OPPONENT = 0;
    /** Late enough for every ship to be deployed, so anything still in reserve was left out by the
     * battle size. */
    private static final float DEPLOY_CHECK_AT = 10f;

    private final String missionId;
    private final String arm;
    private final Set<ShipAPI> watched = Collections.newSetFromMap(new IdentityHashMap<ShipAPI, Boolean>());
    private float dealt;
    private float taken;
    private float hullDealt;
    private float hullTaken;
    private boolean deployChecked;
    private boolean recorded;

    public TestBattleLog(String missionId, String arm) {
        this.missionId = missionId;
        this.arm = arm;
    }

    /** Reset here rather than relying on a fresh instance, in case a restarted mission reuses this one. */
    @Override
    public void init(CombatEngineAPI engine) {
        watched.clear();
        dealt = taken = hullDealt = hullTaken = 0f;
        deployChecked = recorded = false;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;
        for (ShipAPI ship : engine.getShips()) {
            if (watched.add(ship)) ship.addListener(this);
        }
        float t = engine.getTotalElapsedTime(false);
        if (!deployChecked && t >= DEPLOY_CHECK_AT) {
            deployChecked = true;
            log.info(String.format("DraconisTest,deploy,mission=%s,t=%.0f,draconisReserve=%d,opponentReserve=%d",
                    missionId, t, ships(engine.getFleetManager(DRACONIS).getReservesCopy()),
                    ships(engine.getFleetManager(OPPONENT).getReservesCopy())));
        }
        if (!recorded && engine.isCombatOver()) {
            recorded = true;
            record(engine, t);
        }
    }

    @Override
    public void reportDamageApplied(Object source, CombatEntityAPI target, ApplyDamageResultAPI result) {
        if (!(target instanceof ShipAPI)) return;
        float hull = result.getDamageToHull();
        float damage = hull + result.getTotalDamageToArmor();
        int owner = target.getOwner();
        if (owner == OPPONENT) {
            dealt += damage;
            hullDealt += hull;
        } else if (owner == DRACONIS) {
            taken += damage;
            hullTaken += hull;
        }
    }

    private void record(CombatEngineAPI engine, float t) {
        CombatFleetManagerAPI ours = engine.getFleetManager(DRACONIS);
        CombatFleetManagerAPI theirs = engine.getFleetManager(OPPONENT);
        int winnerSide = engine.getWinningSideId();
        String winner = winnerSide == DRACONIS ? "draconis" : winnerSide == OPPONENT ? "opponent" : "none";
        float oursLost = lostDp(ours);
        float theirsLost = lostDp(theirs);
        log.info(String.format("DraconisTest,result,experiment=%s,mission=%s,arm=%s,winner=%s,t=%.0f,dealt=%.0f,taken=%.0f,"
                        + "ratio=%s,hullDealt=%.0f,hullTaken=%.0f,draconisDpLost=%.0f,opponentDpLost=%.0f,"
                        + "draconisRetreated=%d,opponentRetreated=%d",
                TestRunLedger.EXPERIMENT, missionId, arm, winner, t, dealt, taken,
                taken > 0f ? String.format("%.2f", dealt / taken) : "perfect", hullDealt, hullTaken,
                oursLost, theirsLost, ships(ours.getRetreatedCopy()), ships(theirs.getRetreatedCopy())));
        TestRunLedger.record(missionId, arm, winner, t, dealt, taken, oursLost, theirsLost);
    }

    /** A ship can be in both the disabled and the destroyed list, so each member is counted once by id. */
    private static float lostDp(CombatFleetManagerAPI fleet) {
        List<FleetMemberAPI> lost = new ArrayList<>(fleet.getDisabledCopy());
        lost.addAll(fleet.getDestroyedCopy());
        Set<String> counted = new HashSet<>();
        float dp = 0f;
        for (FleetMemberAPI member : lost) {
            if (!member.isFighterWing() && counted.add(member.getId())) dp += member.getDeploymentPointsCost();
        }
        return dp;
    }

    private static int ships(List<FleetMemberAPI> members) {
        int n = 0;
        for (FleetMemberAPI member : members) {
            if (!member.isFighterWing()) n++;
        }
        return n;
    }
}
