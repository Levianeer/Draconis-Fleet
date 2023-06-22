package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;

public class fsdf_Enhanced_Targeting extends BaseHullMod {

	public static final float RANGE_BONUS = 100f;
	public static final float RECOIL_BONUS = 25f;

	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getBallisticWeaponRangeBonus().modifyFlat(id, RANGE_BONUS);

        stats.getMaxRecoilMult().modifyMult(id, 1f - (0.01f * RECOIL_BONUS));
        stats.getRecoilPerShotMult().modifyMult(id, 1f - (0.01f * RECOIL_BONUS));
        stats.getRecoilDecayMult().modifyMult(id, 1f - (0.01f * RECOIL_BONUS));
    }
    
	public String getDescriptionParam(int index, HullSize hullSize) {
		if (index == 0) return "" + (int) RANGE_BONUS;
		if (index == 1) return "" + (int) RECOIL_BONUS + "%";
		return null;
	}

}