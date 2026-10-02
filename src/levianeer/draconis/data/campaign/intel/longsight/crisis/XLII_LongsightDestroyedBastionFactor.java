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
 * Monthly-factor row for {@link XLII_LongsightCrisisTrackerIntel}: surfaces the spawn-rate
 * slowdown from recently destroyed Bastions as an "xN.N" multiplier on
 * {@link XLII_LongsightBaseRateFactor}'s baseline, not a percentage - matches the base game's own
 * HAColonyDefensesFactor idiom, same as {@code DraconisAIORelationsFactor} already uses for the AIO
 * colony crisis.
 * <p>
 * Purely informational - {@link #getProgress} always returns 0, so {@code BaseEventIntel}'s
 * automatic monthly economy-tick progress application (which sums every factor's
 * {@code getProgress()}) stays a harmless no-op. The real mechanic lives entirely in
 * {@link XLII_LongsightCrisisManager#getIntervalRateMult()} /
 * {@link XLII_LongsightCrisisManager#getSlowdownFraction()}; this class only renders it.
 */
public class XLII_LongsightDestroyedBastionFactor extends BaseEventFactor {

    @Override
    public boolean shouldShow(BaseEventIntel intel) {
        XLII_LongsightCrisisManager manager = XLII_LongsightCrisisManager.get();
        return manager != null && manager.getSlowdownFraction() > 0f;
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        return "Destroyed Bases";
    }

    @Override
    public String getProgressStr(BaseEventIntel intel) {
        XLII_LongsightCrisisManager manager = XLII_LongsightCrisisManager.get();
        float frac = manager != null ? manager.getSlowdownFraction() : 0f;
        return Strings.X + Misc.getRoundedValueMaxOneAfterDecimal(1f - frac);
    }

    @Override
    public Color getProgressColor(BaseEventIntel intel) {
        return Misc.getPositiveHighlightColor();
    }

    @Override
    public TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        return new BaseFactorTooltip() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara("Every base the player destroys slows the next spawn check "
                        + "temporarily, fading back to nothing over time. Destroying several in "
                        + "quick succession stacks the slowdown further.", 0f);
            }
        };
    }
}
