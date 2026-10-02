package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;

public class XLII_DraconisHull extends XLII_SystemHullModBase {

    public static final float DEGRADE_INCREASE_PERCENT = 50f;

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getCRLossPerSecondPercent().modifyPercent(id, DEGRADE_INCREASE_PERCENT);
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        return switch (index) {
            case 0 -> (int) getChargeTime(HullSize.FRIGATE) + "s";
            case 1 -> (int) getChargeTime(HullSize.DESTROYER) + "s";
            case 2 -> (int) getChargeTime(HullSize.CRUISER) + "s";
            case 3 -> (int) getChargeTime(HullSize.CAPITAL_SHIP) + "s";
            case 4 -> (int) CR_LOSS_ON_WARP + Strings.X;
            default -> null;
        };
    }

    @Override
    public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return false;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();

        tooltip.addPara("Draconis-built hulls have a number of shared properties.", opad);

        addTransverseJumpTooltipSection(tooltip, opad, h);

        tooltip.addSectionHeading("Maintenance", new Color(255,100,0) , new Color(105,40,0,175) , Alignment.MID, opad);
        tooltip.addPara("Draconis-built hulls require excessive maintenance and do not stand up well under the rigours of prolonged engagements.", opad);

        tooltip.addPara("Increases the rate of in-combat CR decay after peak performance time runs out by %s.",
                opad, h,
                (int) DEGRADE_INCREASE_PERCENT + "%");
    }

    @Override
    public int getDisplaySortOrder() {
        return 0;
    }

    @Override
    public int getDisplayCategoryIndex() {
        return 0;
    }
}
