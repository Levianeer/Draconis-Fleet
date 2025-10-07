package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipSystemAPI;

public class XLII_IncomprehensibleHorrors extends BaseHullMod {

    private static final float COOLDOWN_REDUCTION = -35f;
    private static final float HULL_RESTORE_PERCENT = 0.025f; // % of max hull

    private boolean wasSystemActive = false;
    private float hullRestorePerSecond = 0f;

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getSystemCooldownBonus().modifyPercent(id, COOLDOWN_REDUCTION);
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null) return;

        ShipSystemAPI system = ship.getSystem();
        if (system == null) return;

        boolean isSystemActive = system.isActive();

        // System just activated - calculate restoration rate only once per activation
        if (isSystemActive && !wasSystemActive) {
            float duration = system.getSpecAPI().getIn();
            if (duration > 0) {
                float totalHullToRestore = ship.getMaxHitpoints() * HULL_RESTORE_PERCENT;
                hullRestorePerSecond = totalHullToRestore / duration;
            } else {
                hullRestorePerSecond = 0f;
            }
        }

        // System deactivated - reset restoration rate
        if (!isSystemActive && wasSystemActive) {
            hullRestorePerSecond = 0f;
        }

        // Restore hull while system is active
        if (isSystemActive && hullRestorePerSecond > 0) {
            float hullToRestore = hullRestorePerSecond * amount;
            ship.setHitpoints(Math.min(ship.getHitpoints() + hullToRestore, ship.getMaxHitpoints()));
        }

        wasSystemActive = isSystemActive;
    }
}