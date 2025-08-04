package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

import java.util.HashMap;
import java.util.Map;

public class fsdf_Safety_OverridesStats extends BaseShipSystemScript {

	private static final Map speed = new HashMap();
	static {
		speed.put(ShipAPI.HullSize.FRIGATE, 50f);
		speed.put(ShipAPI.HullSize.DESTROYER, 40f);
		speed.put(ShipAPI.HullSize.CRUISER, 30f);
		speed.put(ShipAPI.HullSize.CAPITAL_SHIP, 20f);
	}

	public static final float FLUX_DISSIPATION_MULT = 2f;

	@Override
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
		ShipAPI ship = null;
		if (stats.getEntity() instanceof ShipAPI) {
			ship = (ShipAPI) stats.getEntity();
		}
		if (ship == null) return;

		ShipAPI.HullSize hullSize = ship.getHullSize();

		stats.getMaxSpeed().modifyFlat(id, (Float) speed.get(hullSize));
		stats.getAcceleration().modifyFlat(id, (Float) speed.get(hullSize) * 2f);
		stats.getDeceleration().modifyFlat(id, (Float) speed.get(hullSize) * 2f);
		stats.getZeroFluxMinimumFluxLevel().modifyFlat(id, 2f);

		stats.getFluxDissipation().modifyMult(id, FLUX_DISSIPATION_MULT);
		stats.getVentRateMult().modifyMult(id, 0f);
	}

	public void unapply(MutableShipStatsAPI stats, String id) {

		stats.getMaxSpeed().unmodify(id);
		stats.getAcceleration().unmodify(id);
		stats.getDeceleration().unmodify(id);
		stats.getZeroFluxMinimumFluxLevel().unmodify(id);
		stats.getFluxDissipation().unmodify(id);
		stats.getVentRateMult().unmodify(id);
	}

	public StatusData getStatusData(int index, State state, float effectLevel) {
		
		if (index == 0) {
			return new StatusData("safety protocols overridden", false);
		}
		return null;
	}
}