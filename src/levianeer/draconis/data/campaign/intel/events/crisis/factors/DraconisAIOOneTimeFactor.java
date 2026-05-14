package levianeer.draconis.data.campaign.intel.events.crisis.factors;

import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseOneTimeFactor;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import levianeer.draconis.data.campaign.intel.events.crisis.core.DraconisAIOTracker;

/**
 * A one-time AIO tracker factor with a fixed description and point value.
 * Appears in the "Recent one-time factors" panel and expires after 30 days.
 * <p>
 * Use {@link DraconisAIOTracker#addOneTimeFactor} to add one of these.
 */
public class DraconisAIOOneTimeFactor extends BaseOneTimeFactor {

    private final String desc;
    private final String tooltipText;

    /**
     * @param points      positive = tracker advances, negative = tracker retreats
     * @param desc        short description shown in the factor table (e.g. "DDA raid repelled")
     * @param tooltipText longer explanation shown on hover, or null for none
     */
    public DraconisAIOOneTimeFactor(int points, String desc, String tooltipText) {
        super(points);
        this.desc = desc;
        this.tooltipText = tooltipText;
    }

    public DraconisAIOOneTimeFactor(int points, String desc) {
        this(points, desc, null);
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        return desc;
    }

    @Override
    public TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        if (tooltipText == null || tooltipText.isEmpty()) return null;
        return new BaseFactorTooltip() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara(tooltipText, 0f);
            }
        };
    }
}
