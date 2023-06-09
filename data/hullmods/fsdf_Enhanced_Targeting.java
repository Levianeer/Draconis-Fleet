package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.WeaponAPI;

public class fsdf_Enhanced_Targeting extends BaseHullMod {

    public static float RANGE_BONUS = 100f;
	public static float RECOIL_BONUS = 15f;

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        MutableShipStatsAPI stats = ship.getMutableStats();
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (weapon.getType().equals(WeaponAPI.WeaponType.BALLISTIC)) {
                stats.getBallisticWeaponRangeBonus().modifyFlat(id, RANGE_BONUS);
                stats.getMaxRecoilMult().modifyMult(id, 1f - (0.01f * RECOIL_BONUS));
                stats.getRecoilPerShotMult().modifyMult(id, 1f - (0.01f * RECOIL_BONUS));
                stats.getRecoilDecayMult().modifyMult(id, 1f - (0.01f * RECOIL_BONUS));
            }
        }
    }
    
	public String getDescriptionParam(int index, HullSize hullSize) {
		if (index == 0) return "" + (int) RANGE_BONUS;
		if (index == 1) return "" + (int) RECOIL_BONUS + "%";
		return null;
	}
}