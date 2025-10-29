package levianeer.draconis.data.campaign.econ.conditions.aicore;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.intel.aicore.scanner.DraconisSingleTargetScanner;

import java.awt.*;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Applied to the single highest-value AI core target marked by Draconis intelligence
 */
public class DraconisHighValueTargetCondition extends BaseMarketConditionPlugin {

    private static final float STABILITY_PENALTY = 1f;
    private static final float GROUND_DEFENSE_PENALTY = -0.2f;

    @Override
    public void apply(String id) {
        MarketAPI market = this.market;

        // Stability penalty from being marked as priority target
        market.getStability().modifyFlat(id, -STABILITY_PENALTY, getName());

        // Ground defense reduction from intelligence infiltration
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD)
                .modifyMult(id, 1f + GROUND_DEFENSE_PENALTY, getName());
    }

    @Override
    public void unapply(String id) {
        market.getStability().unmodifyFlat(id);
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyMult(id);
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        Color h = Misc.getHighlightColor();
        Color n = Misc.getNegativeHighlightColor();
        Color draconis = Global.getSector().getFaction(DRACONIS).getBaseUIColor();

        float opad = 10f;

        tooltip.addPara(
                "Draconis Alliance intelligence has designated this colony as their highest-priority AI core acquisition target. " +
                        "Deep cover operatives have compromised security protocols and are actively preparing for extraction operations.",
                opad, draconis
        );

        // Show target details if available
        if (market.getMemoryWithoutUpdate().contains(DraconisSingleTargetScanner.TARGET_CORE_VALUE_FLAG)) {
            float strategicValue = market.getMemoryWithoutUpdate().getFloat(
                    DraconisSingleTargetScanner.TARGET_CORE_VALUE_FLAG);
            int alphaCount = market.getMemoryWithoutUpdate().getInt(
                    DraconisSingleTargetScanner.TARGET_ALPHA_COUNT_FLAG);
            int betaCount = market.getMemoryWithoutUpdate().getInt(
                    DraconisSingleTargetScanner.TARGET_BETA_COUNT_FLAG);
            int gammaCount = market.getMemoryWithoutUpdate().getInt(
                    DraconisSingleTargetScanner.TARGET_GAMMA_COUNT_FLAG);
            int totalCores = alphaCount + betaCount + gammaCount;

            tooltip.addPara(
                    "Intelligence assessment: %s AI cores confirmed (Alpha: %s, Beta: %s, Gamma: %s). " +
                            "Combined strategic value: %s.",
                    opad, h,
                    String.valueOf(totalCores),
                    String.valueOf(alphaCount),
                    String.valueOf(betaCount),
                    String.valueOf(gammaCount),
                    String.format("%.0f", strategicValue)
            );
        }

        tooltip.addPara(
                "Infiltration operations have degraded defensive readiness, reducing effective ground defenses by %s.",
                opad, n,
                String.format("%.0f%%", Math.abs(GROUND_DEFENSE_PENALTY * 100))
        );

        tooltip.addPara(
                "The population is aware of the increased threat level, causing elevated civil unrest and reducing stability by %s.",
                opad, n,
                String.format("-%d", (int)STABILITY_PENALTY)
        );

        tooltip.addPara(
                "A Draconis raid fleet may be dispatched at any time to seize these assets.",
                opad, Misc.getNegativeHighlightColor()
        );
    }
}