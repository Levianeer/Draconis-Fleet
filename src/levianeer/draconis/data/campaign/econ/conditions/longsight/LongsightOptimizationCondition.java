package levianeer.draconis.data.campaign.econ.conditions.longsight;

import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Marker condition applied to every Draconis market once the Pristine Nanoforge is installed
 * on Kori. Carries no stat effects of its own - {@link LongsightOptimizationMonitor} is what
 * actually does the work, picking a share of flagged markets each month and improving one of
 * their industries. This class exists purely to surface, via its tooltip, that Longsight is
 * watching a given colony.
 */
public class LongsightOptimizationCondition extends BaseMarketConditionPlugin {

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        tooltip.addPara("An unknown hyperintelligence is continuously auditing this colony's industry for " +
                "inefficiencies, redirecting surplus processing capacity to close the gaps " +
                "it finds. Each month, a fraction of its monitored holdings receive an " +
                "unprompted improvement.", 10f, Misc.getGrayColor(), Misc.getGrayColor());
    }
}
