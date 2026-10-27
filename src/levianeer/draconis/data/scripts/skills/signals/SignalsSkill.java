package levianeer.draconis.data.scripts.skills.signals;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.skills.ElectronicWarfareScript;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCBaseSkillPlugin;

/**
 * Every Signals effect scales with the ECM penalty the owning fleet inflicts on the enemy, as
 * computed by vanilla's ElectronicWarfareScript. See .claude/systems/signals-aptitude.md.
 */
public abstract class SignalsSkill extends SCBaseSkillPlugin {

    @Override
    public String getAffectsString() {
        return "all ships in the fleet";
    }

    // Effects depend on both fleets' live ECM, so they're applied by SignalsCombatManager instead of
    // through SiC's per-ship hooks; this only tells the manager which side has the skill.
    @Override
    public void applyEffectsAfterShipCreation(SCData data, ShipAPI ship, ShipVariantAPI variant, String id) {
        if (Global.getCurrentState() != GameState.COMBAT) return;
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || ship == null) return;
        SignalsCombatManager.getOrCreate(engine).register(ship, this);
    }

    /** True if the effect lands on the opposing fleet, scaled by the owner's advantage after each ship's ECCM. */
    public boolean affectsEnemy() {
        return false;
    }

    /** Called every update with the current advantage, including 0, so modifiers fall off as it drops. */
    public void applyAdvantage(MutableShipStatsAPI stats, float advantage, float maximum) {
    }

    // Tooltips quote each effect at vanilla's 10% cap rather than per point of advantage. The sign is
    // part of the value so it's highlighted with it, as in SiC's own tooltips.
    protected static String atCap(String sign, float perAdvantage) {
        return sign + Math.round(perAdvantage * ElectronicWarfareScript.BASE_MAXIMUM) + "%";
    }

    protected static void addScalingHeader(TooltipMakerAPI tooltip) {
        tooltip.addPara("Scales with the fleet's ECM advantage*, up to:", 0f, Misc.getHighlightColor(), Misc.getHighlightColor());
    }

    protected static void addEffect(TooltipMakerAPI tooltip, String format, String... values) {
        tooltip.addPara("   - " + format, 0f, Misc.getTextColor(), Misc.getHighlightColor(), values);
    }

    // Wording follows SiC's own Electronic Warfare footnote.
    protected void addAdvantageNote(TooltipMakerAPI tooltip) {
        String max = (int) ElectronicWarfareScript.BASE_MAXIMUM + "%";
        String eccm = affectsEnemy() ? " Effects on enemy ships are reduced by their ECCM, as the range penalty is." : "";
        tooltip.addSpacer(10f);
        tooltip.addPara("*ECM advantage is the enemy weapon range penalty from your fleet's ECM rating, "
                + "up to a maximum of %s. Listed values are at the maximum." + eccm + " Does not apply to fighters.",
                0f, Misc.getGrayColor(), Misc.getHighlightColor(), max);
    }
}
