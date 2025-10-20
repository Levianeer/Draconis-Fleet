package data.scripts.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class CHM_XLII_draconis extends BaseHullMod {

    private static final Map crReduction = new HashMap();
    static {
        crReduction.put(HullSize.FIGHTER, 0f);
        crReduction.put(HullSize.FRIGATE, 0.15f);
        crReduction.put(HullSize.DESTROYER, 0.15f);
        crReduction.put(HullSize.CRUISER, 0.15f);
        crReduction.put(HullSize.CAPITAL_SHIP, 0.15f);
        crReduction.put(HullSize.DEFAULT, 0f);
    }

    public static final float ECCM_BONUS = 10f; // 10% ECCM effectiveness bonus
    public static final float CR_REDUCTION_PERCENT = 15f; // For description only

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // Apply CR loss reduction
        float crBonus = (Float) crReduction.get(hullSize);
        if (crBonus > 0) {
            stats.getCRLossPerSecondPercent().modifyMult(id, 1f - crBonus);
        }

        // Apply ECCM effectiveness bonus
        stats.getEccmChance().modifyFlat(id, ECCM_BONUS / 100f);
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // Add compatibility with other mods if needed
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return (int) CR_REDUCTION_PERCENT + "%";
        if (index == 1) return (int) ECCM_BONUS + "%";
        return null;
    }

    @Override
    public Color getBorderColor() {
        return new Color(90, 165, 255, 0); // Draconis blue theme
    }

    @Override
    public Color getNameColor() {
        return new Color(100, 180, 255, 255); // Brighter Draconis blue
    }
}