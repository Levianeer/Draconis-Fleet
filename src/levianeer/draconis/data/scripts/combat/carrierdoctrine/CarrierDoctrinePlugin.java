package levianeer.draconis.data.scripts.combat.carrierdoctrine;

import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import org.apache.log4j.Logger;

/**
 * Installed once per battle by XLII_FortySecond (the built-in hullmod every FortySecond-skinned
 * hull carries) the first time any FortySecond ship enters the engine. Only ever drives owner 1 -
 * by design, this doctrine is exclusive to AI-controlled fleets and never touches the player's
 * own side (owner 0), following the same owner-numbering convention vanilla's
 * ThreatCombatStrategyAI relies on.
 * <p>
 * Picks between the {@link TaskForceAI} rewrite and the legacy {@link CarrierDoctrineAI} on
 * {@link #TASK_FORCE_SETTING_ID}, so the two can be A/B compared in the simulator by flipping one
 * settings.json value rather than swapping builds. See .claude/systems/carrier-doctrine.md.
 */
public class CarrierDoctrinePlugin extends BaseEveryFrameCombatPlugin {

    private static final Logger log = Global.getLogger(CarrierDoctrinePlugin.class);

    public static final int AI_OWNER = 1;
    public static final String TASK_FORCE_SETTING_ID = "draconisCarrierDoctrineTaskForce";
    /** false installs no controller at all and records the battle anyway, to measure what this
     * fleet does with the vanilla admiral AI alone. Absent key reads true, so a missing setting
     * leaves the controller running. */
    public static final String ENABLED_SETTING_ID = "draconisCarrierDoctrineEnabled";
    /** Takes precedence over {@link #TASK_FORCE_SETTING_ID} while DraconisAdmiralAI is being
     * built out; it will replace that switch's "true" branch once TaskForceAI is deleted. */
    public static final String ADMIRAL_SETTING_ID = "draconisAdmiralAI";

    protected DoctrineController ai;
    protected PersonalityProbe probe;
    protected InterceptProbe interceptProbe;
    /** Outcome-only record for the legacy controller, so a like-for-like A/B series is possible.
     * Nothing in this project has measured the legacy controller with the same numbers used to
     * judge the rewrite - every comparison between them has been an impression. The legacy class
     * itself is not touched; this observes from outside it. */
    protected TaskForceBattleLog legacyLog;
    protected final IntervalUtil legacyTick = new IntervalUtil(0.8f, 1.2f);
    protected float elapsed = 0f;
    protected boolean installed = false;

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (Global.getCurrentState() != GameState.COMBAT) return;

        if (!installed) {
            installed = true;
            if (settingOn(ADMIRAL_SETTING_ID)) {
                ai = new DraconisAdmiralAI(AI_OWNER);
                if (battleLogEnabled()) legacyLog = new TaskForceBattleLog(AI_OWNER, "admiral", true);
                log.info("Draconis: DraconisAdmiralAI installed (step 1, observation only)");
            } else if (settingOn("draconisInterceptProbe")) {
                interceptProbe = new InterceptProbe(AI_OWNER);
                if (battleLogEnabled()) legacyLog = new TaskForceBattleLog(AI_OWNER, "intercept", true);
                log.info("Draconis: intercept probe installed, no controller");
            } else if (probeEnabled()) {
                probe = new PersonalityProbe(AI_OWNER);
                if (battleLogEnabled()) legacyLog = new TaskForceBattleLog(AI_OWNER, "probe", true);
                log.info("Draconis: personality probe installed, no controller");
            } else if (!controllerEnabled()) {
                // No controller, no overlay, no orders - just the record. The exchange ratio this
                // produces is the number every controller has to beat to be worth having.
                if (battleLogEnabled()) legacyLog = new TaskForceBattleLog(AI_OWNER, "baseline", true);
                log.info("Draconis: carrier doctrine disabled, recording baseline only");
            } else if (useTaskForce()) {
                TaskForceAI taskForce = new TaskForceAI(AI_OWNER);
                ai = taskForce;
                Global.getCombatEngine().addLayeredRenderingPlugin(new TaskForceDebugOverlay(taskForce));
            } else {
                CarrierDoctrineAI legacy = new CarrierDoctrineAI(AI_OWNER);
                ai = legacy;
                Global.getCombatEngine().addLayeredRenderingPlugin(new CarrierDoctrineDebugOverlay(legacy));
                if (battleLogEnabled()) legacyLog = new TaskForceBattleLog(AI_OWNER, "legacy", true);
            }
        }
        if (ai != null) ai.advance(amount);
        if (probe != null) probe.advance(amount);
        if (interceptProbe != null) interceptProbe.advance(amount);

        if (legacyLog != null) {
            elapsed += amount;
            legacyTick.advance(amount);
            if (legacyTick.intervalElapsed()) {
                CombatEngineAPI engine = Global.getCombatEngine();
                legacyLog.tickOutcomeOnly(elapsed, engine.getFleetManager(AI_OWNER),
                        engine.getFleetManager(0)); // AI_OWNER is 1 by definition; 0 is the player
            }
        }
    }

    protected static boolean battleLogEnabled() {
        try {
            return Global.getSettings().getBoolean("draconisCarrierDoctrineBattleLog");
        } catch (Exception e) {
            return false;
        }
    }

    protected static boolean probeEnabled() {
        return settingOn("draconisPersonalityProbe");
    }

    protected static boolean settingOn(String id) {
        try {
            return Global.getSettings().getBoolean(id);
        } catch (Exception e) {
            return false;
        }
    }

    protected static boolean controllerEnabled() {
        try {
            return Global.getSettings().getBoolean(ENABLED_SETTING_ID);
        } catch (Exception e) {
            log.warn("Draconis: failed to read " + ENABLED_SETTING_ID + ", leaving the controller on", e);
            return true;
        }
    }

    protected static boolean useTaskForce() {
        try {
            return Global.getSettings().getBoolean(TASK_FORCE_SETTING_ID);
        } catch (Exception e) {
            log.warn("Draconis: failed to read " + TASK_FORCE_SETTING_ID + ", using the task force controller", e);
            return true;
        }
    }
}
