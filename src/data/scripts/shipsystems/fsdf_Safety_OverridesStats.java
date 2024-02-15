package data.scripts.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

public class fsdf_Safety_OverridesStats extends BaseShipSystemScript {

	public static final float MULT = 30f;

	public static final float ROF_BONUS = 1f;

	public static final float PEAK_MULT = 0.33f;
	public static final float FLUX_REDUCTION = 50f;
	
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {

		float ROF = 1f + ROF_BONUS * effectLevel;
		stats.getBallisticRoFMult().modifyMult(id, ROF);
		stats.getEnergyRoFMult().modifyMult(id, ROF);

		stats.getBallisticWeaponFluxCostMod().modifyMult(id, 1f - (FLUX_REDUCTION * 0.01f));
		stats.getEnergyWeaponFluxCostMod().modifyMult(id, 1f - (FLUX_REDUCTION * 0.01f));
		
		stats.getPeakCRDuration().modifyMult(id, PEAK_MULT);
	}

	public void unapply(MutableShipStatsAPI stats, String id) {

		stats.getBallisticRoFMult().unmodify(id);
		stats.getEnergyRoFMult().unmodify(id);

		stats.getBallisticWeaponFluxCostMod().unmodify(id);
		stats.getEnergyWeaponFluxCostMod().unmodify(id);
		
		stats.getPeakCRDuration().unmodify(id);
	}

	public StatusData getStatusData(int index, State state, float effectLevel) {
		
		if (index == 0) {
			return new StatusData("disabled safety protocols", false);
		}		
		return null;
	}
}