package levianeer.draconis.data.scripts.ai;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

/**
 * AI script for Missile Barrage ship system.
 * Manages toggle activation/deactivation based on tactical situation.
 */
public class XLII_MissileBarrageAI implements ShipSystemAIScript {

    private static final float SYSTEM_RANGE = 2000f; // Matches missile search range
    private static final float FLUX_THRESHOLD = 0.8f; // Deactivate if flux > 80%

    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipSystemAPI system;

    private final IntervalUtil tracker = new IntervalUtil(0.3f, 0.5f); // Check more frequently for toggle

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.engine = engine;
        this.system = system;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (engine.isPaused()) return;

        tracker.advance(amount);

        if (tracker.intervalElapsed()) {
            if (system.isOutOfAmmo()) return;
            if (system.getCooldownRemaining() > 0) return;

            // Check if system is currently active
            boolean isActive = system.isActive();

            if (isActive) {
                // DEACTIVATION LOGIC
                if (shouldDeactivate()) {
                    system.deactivate();
                }
            } else {
                // ACTIVATION LOGIC
                if (shouldActivate()) {
                    ship.useSystem();
                }
            }
        }
    }

    /**
     * Determines if the system should be deactivated.
     * Simple logic: Turn off if high flux, venting, or no enemies in range.
     */
    private boolean shouldDeactivate() {
        // Deactivate if high flux to prevent overload (especially with flux cost per missile)
        float fluxLevel = ship.getFluxTracker().getFluxLevel();
        if (fluxLevel > FLUX_THRESHOLD) {
            return true;
        }

        // Deactivate if venting or overloaded (can't fire anyway)
        if (ship.getFluxTracker().isOverloadedOrVenting()) {
            return true;
        }

        // Deactivate if no enemies in range (not under threat)
        int enemiesInRange = countEnemiesInRange();
        return enemiesInRange == 0; // Turn off when moving/traveling with no threats
    }

    /**
     * Determines if the system should be activated.
     * Simple logic: Turn on if enemies in range and flux/vent OK.
     */
    private boolean shouldActivate() {
        // Don't activate if flux too high
        float fluxLevel = ship.getFluxTracker().getFluxLevel();
        if (fluxLevel > FLUX_THRESHOLD) {
            return false;
        }

        // Don't activate if venting or overloaded
        if (ship.getFluxTracker().isOverloadedOrVenting()) {
            return false;
        }

        // Only activate if there are enemies in range (under threat)
        int enemiesInRange = countEnemiesInRange();
        return enemiesInRange > 0;
    }

    /**
     * Counts the number of valid enemies within system range.
     * Excludes fighters since the system doesn't target them.
     */
    private int countEnemiesInRange() {
        int count = 0;
        for (ShipAPI enemy : engine.getShips()) {
            if (enemy.getOwner() == ship.getOwner()) continue;
            if (enemy.isHulk() || !enemy.isAlive()) continue;

            // Skip fighters - system doesn't target them
            if (enemy.getHullSize() == ShipAPI.HullSize.FIGHTER) continue;

            float distance = Misc.getDistance(ship.getLocation(), enemy.getLocation());
            float radSum = ship.getCollisionRadius() + enemy.getCollisionRadius();

            if (distance - radSum <= SYSTEM_RANGE) {
                count++;
            }
        }
        return count;
    }

}