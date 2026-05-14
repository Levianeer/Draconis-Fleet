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
 * Monthly factor: raw contribution from AI cores slotted into player industries.
 * Higher-tier cores raise the rate faster (Alpha=3x, Beta=2x, Gamma=1x).
 * Does not include the DDA relations multiplier - that is shown separately by
 * DraconisAIORelationsFactor so the player can see both values independently.
 */
public class DraconisAIOAICoreFactor extends BaseEventFactor {

    @Override
    public int getProgress(BaseEventIntel intel) {
        if (!(intel instanceof DraconisAIOTracker tracker)) return 0;
        if (tracker.isCommissioned()) return 0;

        float aiCoreRate = getSetting("draconisAIOAICoreRate", 0.4f);
        float unit = getSetting("draconisAIOProgressUnit", 10f);
        float mult = getSetting("draconisAIOProgressMult", 0.75f);
        float raw = DraconisAIOTracker.computeAICoreContrib(aiCoreRate);

        float rem = raw;
        float adjusted = 0f;
        while (rem > unit) {
            adjusted += unit;
            rem -= unit;
            rem *= mult;
        }
        adjusted += rem;

        int progress = Math.round(adjusted);
        if (raw > 0 && progress < 1) progress = 1;
        return progress;
    }

    @Override
    public boolean shouldShow(BaseEventIntel intel) {
        if (!(intel instanceof DraconisAIOTracker tracker)) return false;
        return !tracker.isCommissioned();
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        return AIOStrings.FACTOR_AI_CORE_DESC;
    }

    @Override
    public TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        return new BaseFactorTooltip() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                float opad = 10f;
                Color h = Misc.getHighlightColor();
                tooltip.addPara(AIOStrings.FACTOR_AI_CORE_TIP_PARA1, 0f);
                tooltip.addPara(AIOStrings.FACTOR_AI_CORE_TIP_PARA2, opad, h, "+1");
            }
        };
    }

    private float getSetting(String key, float def) {
        try { return Global.getSettings().getFloat(key); } catch (Exception e) { return def; }
    }
}
