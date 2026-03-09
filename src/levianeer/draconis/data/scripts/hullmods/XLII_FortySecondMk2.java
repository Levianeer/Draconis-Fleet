package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;

/**
 * Upgrade hullmod for XLII_FortySecond.
 * When installed alongside XLII_FortySecond, increases its MISSILE_AFFECT_CHANCE
 * by XLII_FortySecond.UPGRADE_CHANCE_BONUS. Does nothing if XLII_FortySecond
 * is not present on the same ship.
 * <p>
 * The actual logic lives in XLII_FortySecond.advanceInCombat — this hullmod
 * is simply detected there by its ID to activate the bonus.
 */
public class XLII_FortySecondMk2 extends BaseHullMod {

    // Must match XLII_FortySecond.UPGRADE_HULLMOD_ID and the ID in hull_mods.csv
    @SuppressWarnings("unused")
    public static final String HULLMOD_ID = "XLII_fortysecond_mk2"; // Do not remove!

    private static final String FORTYSECOND_ID = "XLII_fortysecond"; // ID of the required hullmod

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // No independent stat effects — all logic is handled in XLII_FortySecond.advanceInCombat
    }

    @Override
    public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return false;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship,
                                          float width, boolean isForModSpec) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();

        boolean hasFortySecond = isForModSpec || (ship != null && ship.getVariant().hasHullMod(FORTYSECOND_ID));

        if (hasFortySecond) {
            tooltip.addPara(
                    "Increases the effectivness of the XLII Battlegroup missile interception systems, increasing missile defense chance from %s to %s.",
                    opad, h,
                    Math.round(XLII_FortySecond.MISSILE_AFFECT_CHANCE * 100f) + "%",
                    Math.round((XLII_FortySecond.MISSILE_AFFECT_CHANCE + XLII_FortySecond.UPGRADE_CHANCE_BONUS) * 100f) + "%"
            );
        } else {
            tooltip.addPara(
                    "Requires XLII Battlegroup Avionics to function. Has no effect without it.",
                    bad, opad
            );
        }
    }

    @Override
    public int getDisplaySortOrder() {
        return 2;
    }

    @Override
    public int getDisplayCategoryIndex() {
        return 0;
    }
}