package levianeer.draconis.data.campaign.intel.events.crisis.factors;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.intel.events.crisis.AIOStrings;
import levianeer.draconis.data.campaign.intel.events.crisis.core.DraconisAIOTracker;

import java.awt.Color;

/**
 * Monthly factor: the floor contribution of the AIO tracker.
 * Only non-zero when the AI core contribution falls below the baseline floor.
 * Hidden while no AI cores are installed anywhere - the floor only exists for AI core users,
 * and with zero cores the tracker holds its value instead of advancing.
 */
public class DraconisAIOBaselineFactor extends BaseEventFactor {

    /** Mirrors DraconisAIOTracker.calculateMonthlyIncrement(): zero cores means zero escalation. */
    private boolean hasAICoresInstalled() {
        float aiCoreRate = getSetting("draconisAIOAICoreRate", 0.4f);
        return DraconisAIOTracker.computeAICoreContrib(aiCoreRate) > 0f;
    }

    @Override
    public int getProgress(BaseEventIntel intel) {
        if (!(intel instanceof DraconisAIOTracker tracker)) return 0;
        if (tracker.isCommissioned()) return 0;
        if (!hasAICoresInstalled()) return 0;

        float baseFloor = getSetting("draconisAIOBaseFloor", 0.5f);
        float aiCoreRate = getSetting("draconisAIOAICoreRate", 0.4f);
        float relMaxRed = getSetting("draconisAIORelationsMaxReduction", 0.7f);

        float raw = DraconisAIOTracker.computeAICoreContrib(aiCoreRate)
                * DraconisAIOTracker.computeRelationsMultiplier(relMaxRed);

        int floorInt = Math.max(1, Math.round(baseFloor));
        int rawInt = Math.round(raw);
        return Math.max(0, floorInt - rawInt);
    }

    @Override
    public boolean shouldShow(BaseEventIntel intel) {
        return hasAICoresInstalled(); // hidden with zero cores - the tracker is holding, not advancing
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        return AIOStrings.FACTOR_BASELINE_DESC;
    }

    @Override
    public String getProgressStr(BaseEventIntel intel) {
        int p = getProgress(intel);
        if (p == 0) {
            float baseFloor = getSetting("draconisAIOBaseFloor", 0.5f);
            return "+" + Math.max(1, Math.round(baseFloor));
        }
        return "+" + p;
    }

    @Override
    public Color getProgressColor(BaseEventIntel intel) {
        int p = getProgress(intel);
        if (p == 0) return Misc.getHighlightColor();
        return intel.getProgressColor(p);
    }

    @Override
    public TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        return new BaseFactorTooltip() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                float opad = 10f;
                tooltip.addPara(AIOStrings.FACTOR_BASELINE_TIP_PARA1, 0f);
                tooltip.addPara(AIOStrings.FACTOR_BASELINE_TIP_PARA2, opad);
            }
        };
    }

    private float getSetting(String key, float def) {
        try { return Global.getSettings().getFloat(key); } catch (Exception e) { return def; }
    }
}
