package levianeer.draconis.data.scripts.combat.carrierdoctrine;

import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.input.InputEventAPI;

/**
 * Installed once per battle by XLII_FortySecond (the built-in hullmod every FortySecond-skinned
 * hull carries) the first time any FortySecond ship enters the engine. Only ever drives owner 1 -
 * by design, this doctrine is exclusive to AI-controlled fleets and never touches the player's
 * own side (owner 0), following the same owner-numbering convention vanilla's
 * ThreatCombatStrategyAI relies on. See .claude/systems/carrier-doctrine.md.
 */
public class CarrierDoctrinePlugin extends BaseEveryFrameCombatPlugin {

    public static final int AI_OWNER = 1;

    protected CarrierDoctrineAI ai;

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (Global.getCurrentState() != GameState.COMBAT) return;

        if (ai == null) {
            ai = new CarrierDoctrineAI(AI_OWNER);
        }
        ai.advance(amount);
    }
}
