package levianeer.draconis.data.campaign.econ.conditions.aicore;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.intel.aicore.listener.DraconisAICoreTargetingMonitor;

import java.awt.*;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Market condition applied when Draconis intelligence detects AI cores
 * Makes the market slightly more vulnerable to raids by reducing stability
 */
public class DraconisAICoreDetectedCondition extends BaseMarketConditionPlugin {

    private static final float STABILITY_PENALTY = 1f;
    private static final float GROUND_DEFENSE_PENALTY = -0.2f; // -% ground defenses

    @Override
    public void apply(String id) {
        MarketAPI market = this.market;

        // Small stability penalty
        market.getStability().modifyFlat(id, -STABILITY_PENALTY, getName());

        // Slight ground defense reduction
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD)
                .modifyMult(id, 1f + GROUND_DEFENSE_PENALTY, getName());

        Global.getLogger(this.getClass()).info(
                String.format("Applied AI core detection condition to %s (-%d stability, %.0f%% ground defense)",
                        market.getName(), (int)STABILITY_PENALTY, GROUND_DEFENSE_PENALTY * 100)
        );
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

        tooltip.addTitle(condition.getName(), draconis);

        float opad = 10f;

        tooltip.addPara("Draconis Alliance intelligence has confirmed the emplacement of advanced AI core systems within the colony. " +
                "These high-value strategic assets designate this site as a priority objective " +
                "for Draconis operational planning.", opad, draconis);

        // NEW: Show strategic value if available
        if (market.getMemoryWithoutUpdate().contains(DraconisAICoreTargetingMonitor.AI_CORE_VALUE_FLAG)) {
            float strategicValue = market.getMemoryWithoutUpdate().getFloat(
                    DraconisAICoreTargetingMonitor.AI_CORE_VALUE_FLAG);
            int coreCount = market.getMemoryWithoutUpdate().getInt(
                    DraconisAICoreTargetingMonitor.AI_CORE_COUNT_FLAG);

            String threat = getThreatLevel(strategicValue);
            Color threatColor = getThreatColor(strategicValue);

            tooltip.addPara("Intelligence assessment: %s strategic priority (%s confirmed AI cores, value: %s)",
                    opad, threatColor, threat, String.valueOf(coreCount), String.format("%.0f", strategicValue));
        }

        tooltip.addPara("Clandestine operations have exposed exploitable vulnerabilities in the colony's defensive posture, " +
                        "degrading the efficacy of its security protocols, reducing effective security measures by %s.", opad,
                n, String.format("%.0f%%", Math.abs(GROUND_DEFENSE_PENALTY * 100)));

        tooltip.addPara("Local governance has detected heightened adversarial reconnaissance activities, " +
                        "resulting in elevated civil unrest among the populace, reducing stability by %s.", opad,
                n, String.format("-%d", (int)STABILITY_PENALTY));
    }

    private String getThreatLevel(float value) {
        if (value >= 40) return "Critical";
        if (value >= 25) return "High";
        if (value >= 15) return "Moderate";
        return "Low";
    }

    private Color getThreatColor(float value) {
        if (value >= 40) return Misc.getNegativeHighlightColor();
        if (value >= 25) return new Color(255, 150, 0);
        if (value >= 15) return Misc.getHighlightColor();
        return Misc.getTextColor();
    }
}