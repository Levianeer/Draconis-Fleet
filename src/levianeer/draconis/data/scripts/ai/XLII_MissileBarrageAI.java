package levianeer.draconis.data.scripts.ai;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

public class XLII_MissileBarrageAI implements ShipSystemAIScript {

    private static final float SYSTEM_RANGE = 2000f; // Matches missile search range
    private static final float FLUX_THRESHOLD = 0.8f;

    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipSystemAPI system;

    private final IntervalUtil tracker = new IntervalUtil(0.3f, 0.5f);

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

            boolean isActive = system.isActive();

            if (isActive) {
                if (shouldDeactivate()) {
                    system.deactivate();
                }
            } else {
                if (shouldActivate()) {
                    ship.useSystem();
                }
            }
        }
    }

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

        int enemiesInRange = countEnemiesInRange();
        return enemiesInRange == 0; // Turn off when moving/traveling with no threats
    }

    private boolean shouldActivate() {
        float fluxLevel = ship.getFluxTracker().getFluxLevel();
        if (fluxLevel > FLUX_THRESHOLD) {
            return false;
        }

        if (ship.getFluxTracker().isOverloadedOrVenting()) {
            return false;
        }

        int enemiesInRange = countEnemiesInRange();
        return enemiesInRange > 0;
    }

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