package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.scripts.shipsystems.XLII_PhaseShuntStats;

import java.awt.Color;

public class XLII_PhaseShuntCoils extends BaseHullMod {

    @Override
    public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return false;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();

        int minRange = Math.round(XLII_PhaseShuntStats.BASE_EMP_RANGE * (0.5f + 0.5f * XLII_PhaseShuntStats.MIN_FLUX_SCALING));
        int maxRange = Math.round(XLII_PhaseShuntStats.BASE_EMP_RANGE * (0.5f + 0.5f * XLII_PhaseShuntStats.MAX_FLUX_SCALING));
        int minDamage = Math.round(XLII_PhaseShuntStats.BASE_EMP_AMOUNT * XLII_PhaseShuntStats.MIN_FLUX_SCALING);
        int maxDamage = Math.round(XLII_PhaseShuntStats.BASE_EMP_AMOUNT * XLII_PhaseShuntStats.MAX_FLUX_SCALING);
        int arcs = XLII_PhaseShuntStats.SHIP_EMP_ARCS;

        tooltip.addPara(
                "Modified coil array that accumulates flux over the course of a cloaked transit instead of venting it gradually.",
                opad);

        tooltip.addPara(
                "The instant the ship drops out of phase, the stored charge releases as a localised electromagnetic storm: %s arcs of energy damage strike every enemy hull within range, each arc dealing up to %s damage, while nearby ordnance loses guidance coherence. Both blast radius and damage scale with the flux banked at the moment of return, from %s su and %s damage per arc on a clean exit up to %s su and %s damage per arc under heavy strain.",
                opad, h,
                "" + arcs, "" + maxDamage, "" + minRange, "" + minDamage, "" + maxRange, "" + maxDamage);
    }
}