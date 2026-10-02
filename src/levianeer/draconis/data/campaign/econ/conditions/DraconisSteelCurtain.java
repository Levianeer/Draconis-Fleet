package levianeer.draconis.data.campaign.econ.conditions;

import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

public class DraconisSteelCurtain extends BaseMarketConditionPlugin {

    private static final float GROUND_DEFENSE_BONUS = 0.5f;
    private static final float ACCESSIBILITY_PENALTY = -0.2f;

    @Override
    public void apply(String id) {
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD)
                .modifyMult(id, 1f + GROUND_DEFENSE_BONUS, condition.getName());

        market.getAccessibilityMod().modifyFlat(id, ACCESSIBILITY_PENALTY, condition.getName());
    }

    @Override
    public void unapply(String id) {
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD)
                .unmodifyMult(id);

        market.getAccessibilityMod().unmodifyFlat(id);
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        float opad = 10f;
        Color h = Misc.getHighlightColor();
        Color n = Misc.getNegativeHighlightColor();

        String defenseBonus = "+" + (int)(GROUND_DEFENSE_BONUS * 100) + "%";
        String accessPenalty = (int)(ACCESSIBILITY_PENALTY * 100) + "%";

        String text = "Ground defense strength: " + defenseBonus + ", Accessibility: " + accessPenalty;
        LabelAPI label = tooltip.addPara(text, opad);
        label.setHighlight(defenseBonus, accessPenalty);
        label.setHighlightColors(h, h);
    }
}