package levianeer.draconis.data.scripts.combat.carrierdoctrine;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import org.apache.log4j.Logger;

/**
 * The air group's posture for the opening exchange, which over 15 battles largely decided the
 * result: a split at ~1.07 on the first 30s of damage dealt over taken sorted 13 into the right
 * outcome, and by 60s winners and losers were indistinguishable.
 * <p>
 * The whole air group commits to one posture, chosen from strike-craft numbers. Unless we clearly
 * outnumber the enemy's craft - by {@link #STRIKE_MARGIN} - it defends: carriers cautious, so their
 * wings escort and fight beside the carriers' PD. A clear majority is the chance to strike: carriers
 * aggressive, plus a carrier STRIKE on the enemy cruiser or capital our wings are predicted to kill
 * fastest.
 * <p>
 * The posture is re-chosen each decision during the approach and locked at contact, because the
 * personality lever takes 5-10s to show and the opening is settled by t~10-40s. With nothing
 * observed yet the choice comes from loadouts, and the matchup prediction is used only as an
 * ordering of targets - the one use it measured as reliable. It hands back to the slide
 * {@link #HOLD_SECONDS} after contact.
 */
public class OpeningPlan {

    private static final Logger log = Global.getLogger(OpeningPlan.class);

    public enum Posture { STRIKE, DEFEND }

    /** Seconds after contact before carriers return to the normal slide. */
    public static final float HOLD_SECONDS = 40f;
    /** How far our strike craft must outnumber theirs before the air group strikes. Without it the
     * only STRIKE in the first series fired on 56 vs 54 - effectively parity - stripped the carriers
     * of escorts, and produced the worst opening of the four (0.23) and a battle with no kills. */
    public static final float STRIKE_MARGIN = 1.25f;

    private final int owner;
    private Posture posture;
    private ShipAPI strikeTarget;
    private boolean locked;
    private boolean over;
    private int ourCraft;
    private int enemyCraft;
    private int maxHolders;
    private String lastLogged;
    /** Carriers that hit the emergency during the opening; they stay out of the posture. */
    private final Set<ShipAPI> excused = Collections.newSetFromMap(new IdentityHashMap<ShipAPI, Boolean>());

    /** False runs the plan in observe-only mode: it still counts craft, picks a posture and a
     * target and logs them, but touches nothing. That records every battle's matchup, so runs with
     * the plan on and off can be compared like for like. */
    private final boolean acting;

    public OpeningPlan(int owner, boolean acting) {
        this.owner = owner;
        this.acting = acting;
    }

    /**
     * Once per decision. {@code carriers} are our Draconis carriers; {@code enemies} is every
     * deployed enemy hull, spotted or not - the same composition the player sees on the deployment
     * screen, and the opening is decided before sensors would reveal it.
     */
    public void decide(List<ShipAPI> carriers, List<ShipAPI> enemies, float elapsed, float contactAt,
                       MatchupModel model, ProfileCache profiles) {
        if (over) return;
        if (contactAt >= 0f && elapsed >= contactAt + HOLD_SECONDS) {
            end(elapsed, "hold over");
            return;
        }
        if (!locked) {
            ourCraft = craft(carriers);
            enemyCraft = craft(enemies);
            posture = ourCraft == 0 ? null
                    : ourCraft >= enemyCraft * STRIKE_MARGIN ? Posture.STRIKE : Posture.DEFEND;
            if (contactAt >= 0f) locked = true;
        }
        if (posture == Posture.STRIKE && (strikeTarget == null || !strikeTarget.isAlive() || strikeTarget.isHulk())) {
            strikeTarget = pickTarget(carriers, enemies, model, profiles);
        }
        logState(elapsed, locked ? "locked" : "planning");
    }

    public boolean active() {
        return posture != null && !over;
    }

    public boolean applies(ShipAPI carrier) {
        return acting && active() && !excused.contains(carrier);
    }

    public void excuse(ShipAPI carrier) {
        excused.add(carrier);
    }

    public PersonalitySlide.Step step() {
        return posture == Posture.STRIKE ? PersonalitySlide.Step.AGGRESSIVE : PersonalitySlide.Step.CAUTIOUS;
    }

    /**
     * Every frame while striking. The STRIKE goes on the carrier because that is the only thing
     * measured to steer wings (~35% of wing-ticks onto the target, against a ~20% chance floor);
     * {@code setShipTarget} does not steer them at all. Looked up live rather than trusted, because
     * the vanilla admiral's {@code orderSearchAndDestroy()} cancels every assignment, repeatedly.
     */
    public void reapply(List<ShipAPI> carriers, List<ShipAPI> ours) {
        if (!acting || !active() || posture != Posture.STRIKE || strikeTarget == null) return;
        CombatEngineAPI engine = Global.getCombatEngine();
        CombatTaskManagerAPI tasks = engine.getFleetManager(owner).getTaskManager(false);
        DeployedFleetMemberAPI target = findDeployed(engine, strikeTarget);
        if (target == null) return;
        AssignmentInfo strike = tasks.getAssignmentInfoForTarget(target);
        if (strike == null || strike.getType() != CombatAssignmentType.STRIKE) {
            strike = tasks.createAssignment(CombatAssignmentType.STRIKE, target, false);
            if (strike == null) return;
        }
        for (ShipAPI carrier : carriers) {
            if (excused.contains(carrier) || holdsStrike(tasks, carrier)) continue;
            DeployedFleetMemberAPI member = findDeployed(engine, carrier);
            if (member != null) tasks.giveAssignment(member, strike, false);
        }
        // The task manager staffs a target assignment with hulls it was never given to - INTERCEPT
        // spread to 46-76% of unordered hulls in probe 3 - so count who actually ends up on it.
        int holders = 0;
        for (ShipAPI ship : ours) if (holdsStrike(tasks, ship)) holders++;
        maxHolders = Math.max(maxHolders, holders);
    }

    /** Tears the STRIKE down and hands every carrier back to the slide. */
    public void end(float elapsed, String why) {
        if (over) return;
        over = true;
        if (acting && strikeTarget != null) {
            CombatEngineAPI engine = Global.getCombatEngine();
            CombatTaskManagerAPI tasks = engine.getFleetManager(owner).getTaskManager(false);
            DeployedFleetMemberAPI target = findDeployed(engine, strikeTarget);
            AssignmentInfo strike = target == null ? null : tasks.getAssignmentInfoForTarget(target);
            if (strike != null && strike.getType() == CombatAssignmentType.STRIKE) tasks.removeAssignment(strike);
        }
        logState(elapsed, "ended: " + why);
    }

    /** For the once-per-battle opening line, so the outcome can be read against the posture. */
    public String summary() {
        return String.format("posture=%s,mode=%s,ourCraft=%d,enemyCraft=%d,target=%s,strikeHolders=%d",
                posture, mode(), ourCraft, enemyCraft, strikeTarget == null ? "none" : strikeTarget.getName(),
                maxHolders);
    }

    private String mode() {
        return acting ? "acting" : "observing";
    }

    private boolean holdsStrike(CombatTaskManagerAPI tasks, ShipAPI ship) {
        AssignmentInfo info = tasks.getAssignmentFor(ship);
        return info != null && info.getType() == CombatAssignmentType.STRIKE
                && info.getTarget() instanceof DeployedFleetMemberAPI
                && ((DeployedFleetMemberAPI) info.getTarget()).getShip() == strikeTarget;
    }

    /**
     * Lowest predicted time-to-kill from all our carriers together. The absolute seconds are
     * meaningless (measured 69-668x out); only the ordering is used. The profile mixes a carrier's
     * guns with its wings, which is acceptable for ranking targets against each other.
     */
    private static ShipAPI pickTarget(List<ShipAPI> carriers, List<ShipAPI> enemies,
                                      MatchupModel model, ProfileCache profiles) {
        ShipAPI best = null;
        float bestTtk = Float.POSITIVE_INFINITY;
        float[] scratch = new float[2];
        for (ShipAPI enemy : enemies) {
            if (enemy.getHullSize() != HullSize.CRUISER && enemy.getHullSize() != HullSize.CAPITAL_SHIP) continue;
            float shieldDps = 0f;
            float hullDps = 0f;
            for (ShipAPI carrier : carriers) {
                model.effectiveDps(carrier, profiles.get(carrier), enemy, scratch);
                shieldDps += scratch[0];
                hullDps += scratch[1];
            }
            float ttk = model.timeToKill(shieldDps, hullDps, enemy);
            if (ttk < bestTtk) {
                bestTtk = ttk;
                best = enemy;
            }
        }
        return best;
    }

    /** Full strike-craft complement from the loadout. Wings that have not launched yet do not exist
     * as ships, and the posture is chosen before most of them have. */
    private static int craft(List<ShipAPI> ships) {
        int count = 0;
        for (ShipAPI ship : ships) {
            for (String wingId : ship.getVariant().getFittedWings()) {
                FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(wingId);
                if (spec != null) count += spec.getNumFighters();
            }
        }
        return count;
    }

    /** Through the deployed list, which is how both working assignment paths in this project
     * resolved members. */
    private static DeployedFleetMemberAPI findDeployed(CombatEngineAPI engine, ShipAPI ship) {
        for (DeployedFleetMemberAPI member : engine.getFleetManager(ship.getOwner()).getDeployedCopyDFM()) {
            if (member.getShip() == ship) return member;
        }
        return null;
    }

    private void logState(float elapsed, String state) {
        String line = "posture=" + posture + ",mode=" + mode() + ",ourCraft=" + ourCraft + ",enemyCraft=" + enemyCraft
                + ",target=" + (strikeTarget == null ? "none" : strikeTarget.getName()) + ",state=" + state;
        if (line.equals(lastLogged)) return;
        lastLogged = line;
        log.info(String.format("DraconisAdmiral,posture,t=%.0f,%s", elapsed, line));
    }
}
