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
 * Picks between {@link DraconisAdmiralAI} and the legacy {@link CarrierDoctrineAI} on
 * {@link #ADMIRAL_SETTING_ID}, so the two can be A/B compared in the simulator by flipping one
 * settings.json value rather than swapping builds. See .claude/systems/adaptive-admiral.md.
 */
public class CarrierDoctrinePlugin extends BaseEveryFrameCombatPlugin {

    private static final Logger log = Global.getLogger(CarrierDoctrinePlugin.class);

    public static final int AI_OWNER = 1;
    public static final String ADMIRAL_SETTING_ID = "draconisAdmiralAI";
    /** false installs no controller at all and records the battle anyway, to measure what this
     * fleet does with the vanilla admiral AI alone. Absent key reads true, so a missing setting
     * leaves the controller running. */
    public static final String ENABLED_SETTING_ID = "draconisCarrierDoctrineEnabled";

    protected DoctrineController ai;
    /** Outcome record taken from outside the controller, so the admiral, the legacy controller and
     * no controller at all are judged on the same numbers. */
    protected TaskForceBattleLog battleLog;
    protected final IntervalUtil logTick = new IntervalUtil(0.8f, 1.2f);
    protected float elapsed = 0f;
    protected boolean installed = false;

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (Global.getCurrentState() != GameState.COMBAT) return;

        if (!installed) {
            installed = true;
            if (!controllerEnabled()) {
                // No controller, no overlay, no orders - just the record. The exchange ratio this
                // produces is the number every controller has to beat to be worth having.
                if (battleLogEnabled()) battleLog = new TaskForceBattleLog(AI_OWNER, "baseline");
                log.info("Draconis: carrier doctrine disabled, recording baseline only");
            } else if (useAdmiral()) {
                ai = new DraconisAdmiralAI(AI_OWNER);
                if (battleLogEnabled()) battleLog = new TaskForceBattleLog(AI_OWNER, "admiral");
            } else {
                CarrierDoctrineAI legacy = new CarrierDoctrineAI(AI_OWNER);
                ai = legacy;
                Global.getCombatEngine().addLayeredRenderingPlugin(new CarrierDoctrineDebugOverlay(legacy));
                if (battleLogEnabled()) battleLog = new TaskForceBattleLog(AI_OWNER, "legacy");
            }
        }
        if (ai != null) ai.advance(amount);

        if (battleLog != null) {
            elapsed += amount;
            logTick.advance(amount);
            if (logTick.intervalElapsed()) {
                CombatEngineAPI engine = Global.getCombatEngine();
                battleLog.tickOutcomeOnly(elapsed, engine.getFleetManager(AI_OWNER),
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

    protected static boolean controllerEnabled() {
        try {
            return Global.getSettings().getBoolean(ENABLED_SETTING_ID);
        } catch (Exception e) {
            log.warn("Draconis: failed to read " + ENABLED_SETTING_ID + ", leaving the controller on", e);
            return true;
        }
    }

    protected static boolean useAdmiral() {
        try {
            return Global.getSettings().getBoolean(ADMIRAL_SETTING_ID);
        } catch (Exception e) {
            log.warn("Draconis: failed to read " + ADMIRAL_SETTING_ID + ", using DraconisAdmiralAI", e);
            return true;
        }
    }
}
