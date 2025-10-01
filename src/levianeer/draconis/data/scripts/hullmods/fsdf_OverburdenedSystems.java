package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class fsdf_OverburdenedSystems extends BaseHullMod {

    public static final float RECHARGE_MULT = 1.5f;
    public static final float RANGE_DEBUFF = -25f;

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getMissileWeaponRangeBonus().modifyPercent(id, RANGE_DEBUFF);
        stats.getMissileAmmoRegenMult().modifyMult(id, RECHARGE_MULT);
        stats.getMissileRoFMult().modifyMult(id, RECHARGE_MULT);
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return (int) RECHARGE_MULT + ".5x";
        if (index == 1) return (int) Math.abs(RANGE_DEBUFF) + "%";
        return null;
    }
}