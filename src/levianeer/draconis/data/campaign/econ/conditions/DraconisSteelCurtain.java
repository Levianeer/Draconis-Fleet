package levianeer.draconis.data.campaign.econ.conditions;

import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

public class DraconisSteelCurtain extends BaseMarketConditionPlugin {

    // Modifier values
    private static final float GROUND_DEFENSE_BONUS = 0.25f; // +25% ground defenses
    private static final float ACCESSIBILITY_PENALTY = -0.15f; // -15% accessibility

    @Override
    public void apply(String id) {
        // Increase ground defenses
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD)
                .modifyMult(id, 1f + GROUND_DEFENSE_BONUS, condition.getName());

        // Decrease accessibility
        market.getAccessibilityMod().modifyFlat(id, ACCESSIBILITY_PENALTY, condition.getName());
    }

    @Override
    public void unapply(String id) {
        // Remove ground defense modifier
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD)
                .unmodifyMult(id);

        // Remove accessibility modifier
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
        label.setHighlightColors(h, n);
    }
}