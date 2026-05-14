package levianeer.draconis.data.campaign.intel.events.crisis.factors;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.intel.events.crisis.AIOStrings;
import levianeer.draconis.data.campaign.intel.events.crisis.core.DraconisAIOTracker;

import java.awt.Color;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Display-only multiplier factor showing how DDA relations speed up or slow down the monthly
 * crisis increment. Positive rep reduces the multiplier below 1 (slows advance); negative rep
 * raises it above 1 (accelerates advance). Both directions scale equally by the
 * draconisAIORelationsMaxReduction setting.
 * <p>
 * Follows the same pattern as the base game's HAColonyDefensesFactor:
 * contributes no additive progress, overrides getAllProgressMult() to return the active
 * multiplier, and shows it as "xN.N" in the factors table.
 * <p>
 * Note: getAllProgressMult() is not called by DraconisAIOTracker (which drives advancement
 * via calculateMonthlyIncrement() directly). This factor is purely for player visibility.
 */
public class DraconisAIORelationsFactor extends BaseEventFactor {

    @Override
    public float getAllProgressMult(BaseEventIntel intel) {
        float relMaxRed = getSetting("draconisAIORelationsMaxReduction", 0.7f);
        return DraconisAIOTracker.computeRelationsMultiplier(relMaxRed);
    }

    @Override
    public int getProgress(BaseEventIntel intel) {
        return 0;
    }

    @Override
    public String getProgressStr(BaseEventIntel intel) {
        float mult = getAllProgressMult(intel);
        return Strings.X + Misc.getRoundedValueMaxOneAfterDecimal(mult);
    }

    @Override
    public Color getProgressColor(BaseEventIntel intel) {
        float mult = getAllProgressMult(intel);
        if (mult > 1f) return Misc.getNegativeHighlightColor();
        if (mult < 1f) return Misc.getPositiveHighlightColor();
        return Misc.getHighlightColor();
    }

    @Override
    public boolean shouldShow(BaseEventIntel intel) {
        return true;
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        RepLevel level = Global.getSector().getPlayerFaction().getRelationshipLevel(DRACONIS);
        if (level != null) {
            return Misc.ucFirst(level.getDisplayName().toLowerCase());
        }
        return AIOStrings.FACTOR_RELATIONS_NAME_DEFAULT;
    }

    @Override
    public Color getDescColor(BaseEventIntel intel) {
        float mult = getAllProgressMult(intel);
        if (mult > 1f) return Misc.getNegativeHighlightColor();
        if (mult < 1f) return super.getDescColor(intel);
        return Misc.getGrayColor();
    }

    @Override
    public TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        return new BaseFactorTooltip() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                float opad = 10f;
                Color h = Misc.getHighlightColor();
                float relMaxRed = getSetting("draconisAIORelationsMaxReduction", 0.7f);

                tooltip.addPara(AIOStrings.FACTOR_RELATIONS_TIP_PARA1, 0f);
                tooltip.addPara(AIOStrings.FACTOR_RELATIONS_TIP_PARA2_FMT, opad, h,
                        AIOStrings.FACTOR_RELATIONS_TIP_PARA2_H1,
                        AIOStrings.FACTOR_RELATIONS_TIP_PARA2_H2);

                float relation = Global.getSector().getPlayerFaction().getRelationship(DRACONIS);
                Color base   = Global.getSector().getFaction(DRACONIS).getBaseUIColor();
                Color dark   = Global.getSector().getFaction(DRACONIS).getDarkUIColor();
                Color bright = Global.getSector().getFaction(DRACONIS).getBrightUIColor();

                tooltip.beginTable(base, dark, bright, 20f,
                        "Standing", 200f, "Multiplier", 100f);

                // Rows ordered low (hostile) to high (allied).
                // Negative thresholds produce multipliers > 1; positive produce < 1.
                float[][] rows = {
                        {-1.00f, 1f + relMaxRed},
                        {-0.50f, 1f + 0.50f * relMaxRed},
                        {-0.25f, 1f + 0.25f * relMaxRed},
                        { 0.00f, 1f},
                        { 0.25f, 1f - 0.25f * relMaxRed},
                        { 0.50f, 1f - 0.50f * relMaxRed},
                        { 1.00f, 1f - relMaxRed},
                };
                String[] labels = {
                        AIOStrings.FACTOR_RELATIONS_ROW_VENGEFUL,
                        AIOStrings.FACTOR_RELATIONS_ROW_HOSTILE,
                        AIOStrings.FACTOR_RELATIONS_ROW_SUSPICIOUS,
                        AIOStrings.FACTOR_RELATIONS_ROW_NEUTRAL,
                        AIOStrings.FACTOR_RELATIONS_ROW_FAVORABLE,
                        AIOStrings.FACTOR_RELATIONS_ROW_FRIENDLY,
                        AIOStrings.FACTOR_RELATIONS_ROW_ALLIED,
                };

                for (int i = 0; i < rows.length; i++) {
                    float threshold = rows[i][0];
                    float rowMult   = rows[i][1];
                    boolean active  = (relation >= threshold)
                            && (i == rows.length - 1 || relation < rows[i + 1][0]);
                    Color nameColor = active ? h : Misc.getGrayColor();
                    Color multColor;
                    if (!active) {
                        multColor = Misc.getGrayColor();
                    } else if (rowMult > 1f) {
                        multColor = Misc.getNegativeHighlightColor();
                    } else if (rowMult < 1f) {
                        multColor = Misc.getPositiveHighlightColor();
                    } else {
                        multColor = Misc.getHighlightColor();
                    }
                    String multStr = Strings.X + Misc.getRoundedValueMaxOneAfterDecimal(rowMult);
                    tooltip.addRow(nameColor, labels[i], multColor, multStr);
                }

                tooltip.addTable("None", 0, opad);
                tooltip.addSpacer(5f);

                float activeMult = 1f - Math.max(-1f, Math.min(1f, relation)) * relMaxRed;
                if (activeMult > 1f) {
                    tooltip.addPara(AIOStrings.FACTOR_RELATIONS_TIP_CURRENT_ACCEL_FMT, opad,
                            Misc.getNegativeHighlightColor(),
                            Strings.X + Misc.getRoundedValueMaxOneAfterDecimal(activeMult));
                } else if (activeMult < 1f) {
                    tooltip.addPara(AIOStrings.FACTOR_RELATIONS_TIP_CURRENT_FMT, opad, h,
                            Strings.X + Misc.getRoundedValueMaxOneAfterDecimal(activeMult));
                }
            }
        };
    }

    private float getSetting(String key, float def) {
        try { return Global.getSettings().getFloat(key); } catch (Exception e) { return def; }
    }
}