package data.shipsystems.scripts;

import com.fs.starfarer.api.combat.FluxTrackerAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

public class fsdf_heatsinkStats extends BaseShipSystemScript {

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();

        // Vent Flux
        FluxTrackerAPI fluxTracker = ship.getFluxTracker();
        float currentFlux = fluxTracker.getCurrFlux();
        float fluxRemovalRate = (currentFlux * 0.3333f) / 3f;
        float fluxRemoval = fluxRemovalRate * effectLevel;
        fluxTracker.decreaseFlux(fluxRemoval);

        // Disable shields
        ShieldAPI shield = ship.getShield();
        if (shield != null) {
            shield.toggleOff();
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = (ShipAPI) stats.getEntity();

        // Re-enable shields
        ShieldAPI shield = ship.getShield();
        if (shield != null) {
            shield.toggleOn();
        }
    }

	public StatusData getStatusData(int index, State state, float effectLevel) {
		if (index == 0) {
            return new StatusData("shields disabled", true);
		}
		if (index == 1) {
            return new StatusData("venting flux", false);
		}
		return null;
	}
}