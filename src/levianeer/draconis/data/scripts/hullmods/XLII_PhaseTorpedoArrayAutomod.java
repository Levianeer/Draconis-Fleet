package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;

/**
 * Built-in hidden hull mod for Phase Torpedo Array system.
 * Ensures ship system charges are always even (for symmetrical torpedo spawning).
 * <p>
 * This is applied via applyEffectsAfterShipCreation() so it works in refit screen,
 * campaign, and combat. It handles dynamic officer skill bonuses by being
 * called after all other stat modifications are applied.
 */
public class XLII_PhaseTorpedoArrayAutomod extends BaseHullMod {

    private static final String PHASE_TORPEDO_SYSTEM_ID = "XLII_phasetorparray";

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // Only apply to ships with the Phase Torpedo Array system
        ShipSystemAPI system = ship.getSystem();
        if (system == null || !PHASE_TORPEDO_SYSTEM_ID.equals(system.getId())) {
            return;
        }

        // Get base max ammo (includes officer skills like Systems Expertise, other hull mods, etc.)
        int baseMaxAmmo = system.getMaxAmmo();

        // Calculate rounding bonus (0 if even, 1 if odd)
        // This ensures charges are always even for symmetrical torpedo spawning
        float roundingBonus = (baseMaxAmmo % 2 == 0) ? 0f : 1f;

        // Apply the rounding bonus if needed
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
        // Only applicable to ships with Phase Torpedo Array system
        return ship != null && ship.getSystem() != null &&
               PHASE_TORPEDO_SYSTEM_ID.equals(ship.getSystem().getId());
    }
}
