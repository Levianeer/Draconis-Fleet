package data.hullmods;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.hullmods.BaseLogisticsHullMod;

public class fsdf_Draconis_Fleet extends BaseLogisticsHullMod {

	public static final float PROFILE_MULT = 0.75f;
	
	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
		stats.getSensorProfile().modifyMult(id, PROFILE_MULT);
	}
	public String getDescriptionParam(int index, HullSize hullSize) {
		if (index == 0) return "" + (int) ((1f - PROFILE_MULT) * 100f) + "%";
		return null;
	}
}