package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

public class fsdf_Safety_OverridesStats extends BaseShipSystemScript {

	public static final float SPEED_MULT = 50f;
	public static final float PEAK_MULT = 0.33f;
	public static final float FLUX_DISSIPATION_MULT = 2f;
	
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {

		stats.getMaxSpeed().modifyFlat(id, SPEED_MULT);
		stats.getAcceleration().modifyFlat(id, SPEED_MULT * 2f);
		stats.getDeceleration().modifyFlat(id, SPEED_MULT * 2f);
		stats.getZeroFluxMinimumFluxLevel().modifyMult(id, 2f);
		stats.getFluxDissipation().modifyMult(id, FLUX_DISSIPATION_MULT);
		stats.getPeakCRDuration().modifyMult(id, PEAK_MULT);
		stats.getVentRateMult().modifyMult(id, 0f);
	}

	public void unapply(MutableShipStatsAPI stats, String id) {

		stats.getMaxSpeed().unmodify(id);
		stats.getAcceleration().unmodify(id);
		stats.getDeceleration().unmodify(id);
		stats.getZeroFluxMinimumFluxLevel().unmodify(id);
		stats.getFluxDissipation().unmodify(id);
		stats.getPeakCRDuration().unmodify(id);
		stats.getVentRateMult().unmodify(id);
	}

	public StatusData getStatusData(int index, State state, float effectLevel) {
		
		if (index == 0) {
			return new StatusData("safety protocols overridden", false);
		}		
		return null;
	}
}