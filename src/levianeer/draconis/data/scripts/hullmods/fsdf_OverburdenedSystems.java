package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class fsdf_OverburdenedSystems extends BaseHullMod {

    public static final float RECHARGE_MULT = 2f;
    public static final float RANGE_DEBUFF = -20f;
    public static final float AMMO_BONUS = 100f;

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getMissileWeaponRangeBonus().modifyPercent(id, RANGE_DEBUFF);
        stats.getMissileAmmoRegenMult().modifyMult(id, RECHARGE_MULT);
        stats.getMissileRoFMult().modifyMult(id, RECHARGE_MULT);
        stats.getMissileAmmoBonus().modifyPercent(id, AMMO_BONUS);
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return (int) RECHARGE_MULT + "x";
        if (index == 1) return (int) Math.abs(RANGE_DEBUFF) + "%";
        if (index == 2) return (int) AMMO_BONUS + "%";
        return null;
    }
}