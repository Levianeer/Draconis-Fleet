package levianeer.draconis.data.campaign.intel.longsight.crisis;

import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;

/**
 * Always-visible monthly-factor row: the Office's unmodified rate of attempting to stand up new
 * Bastions. Shown first in {@link XLII_LongsightCrisisTrackerIntel}'s factor table so
 * {@link XLII_LongsightDestroyedBastionFactor}'s slowdown underneath it reads as a reduction FROM
 * this x1.0 baseline, not a standalone number. "xN.N" multiplier format, not a percentage - matches
 * the base game's own HAColonyDefensesFactor idiom, same as {@code DraconisAIORelationsFactor}
 * already uses for the AIO colony crisis.
 * <p>
 * Purely informational, same reasoning as that factor - see its own doc for why getProgress()
 * staying 0 is safe.
 */
public class XLII_LongsightBaseRateFactor extends BaseEventFactor {

    @Override
    public boolean shouldShow(BaseEventIntel intel) {
        return true;
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        return "Base rate";
    }

    @Override
    public String getProgressStr(BaseEventIntel intel) {
        return Strings.X + Misc.getRoundedValueMaxOneAfterDecimal(1f);
    }

    @Override
    public Color getProgressColor(BaseEventIntel intel) {
        return Misc.getHighlightColor();
    }

    @Override
    public TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        return new BaseFactorTooltip() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara("How often the Office checks whether to stand up a new base, "
                        + "before any modifiers below are applied.", 0f);
            }
        };
    }
}
