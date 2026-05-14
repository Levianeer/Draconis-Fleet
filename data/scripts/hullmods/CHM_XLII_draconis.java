package data.scripts.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

public class CHM_XLII_draconis extends BaseHullMod {

    public static final float DETECTION_RANGE_REDUCTION = 10f;

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getSensorProfile().modifyPercent(id, -DETECTION_RANGE_REDUCTION);
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return (int) DETECTION_RANGE_REDUCTION + "%";
        return null;
    }

    @Override
    public void addPostDescriptionSection(final TooltipMakerAPI tooltip, final ShipAPI.HullSize hullSize, final ShipAPI ship, final float width, final boolean isForModSpec) {
        tooltip.addPara("%s", 6f, Misc.getGrayColor(), Global.getSettings().getString("CHM", "chm_draconis0")).italicize();
        tooltip.addPara("%s", 6f, Misc.getGrayColor(), Global.getSettings().getString("CHM", "chm_draconis1"));
    }

    @Override
    public Color getBorderColor() {
        return new Color(130,157,209,255); // Draconis blue theme
    }

    @Override
    public Color getNameColor() {
        return new Color(170,197,249,255); // Brighter Draconis blue
    }
}