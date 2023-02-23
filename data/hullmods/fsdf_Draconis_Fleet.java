package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class fsdf_Draconis_Fleet extends BaseHullMod {

	public static final float PROFILE_MULT = 0.75f;
	
	// I don't know what am I doing.

	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
		stats.getSensorProfile().modifyMult(id, PROFILE_MULT);
	}
	public String getDescriptionParam(int index, HullSize hullSize) {
		if (index == 0) return "" + (int) ((1f - PROFILE_MULT) * 100f) + "%";
		return null;
	}
}