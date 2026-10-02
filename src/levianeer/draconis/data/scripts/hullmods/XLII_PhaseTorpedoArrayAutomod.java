package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;

/**
 * Built-in hidden hull mod for the Phase Torpedo Array system: ensures system charges are always
 * even, for symmetrical torpedo spawning. Uses applyEffectsAfterShipCreation() (not before) so it
 * runs after officer skill bonuses and other stat mods are applied, in refit, campaign, and combat.
 */
public class XLII_PhaseTorpedoArrayAutomod extends BaseHullMod {

    private static final String PHASE_TORPEDO_SYSTEM_ID = "XLII_phasetorparray";

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ShipSystemAPI system = ship.getSystem();
        if (system == null || !PHASE_TORPEDO_SYSTEM_ID.equals(system.getId())) {
            return;
        }

        // Get base max ammo (includes officer skills like Systems Expertise, other hull mods, etc.)
        int baseMaxAmmo = system.getMaxAmmo();

        float roundingBonus = (baseMaxAmmo % 2 == 0) ? 0f : 1f;

        if (roundingBonus > 0) {
            ship.getMutableStats().getSystemUsesBonus().modifyFlat(id, roundingBonus);
        }
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        // Hidden mod - no description needed
        return null;
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return ship != null && ship.getSystem() != null &&
               PHASE_TORPEDO_SYSTEM_ID.equals(ship.getSystem().getId());
    }
}
