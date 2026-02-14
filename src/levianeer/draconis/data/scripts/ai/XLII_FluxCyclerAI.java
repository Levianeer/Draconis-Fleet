// NO LONGER USED
package levianeer.draconis.data.scripts.ai;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * AI for the Flux Cycler ship system.
 * Both the deactivation threshold and reactivation interval scale with vent
 * count: fewer vents means a higher deactivation threshold (longer active
 * window) with a longer recovery gap; more vents means a lower threshold
 * (shorter active window) with a shorter recovery gap.
 * Probably unneeded now.
 */
public class XLII_FluxCyclerAI implements ShipSystemAIScript {

    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipSystemAPI system;

    private final IntervalUtil tracker = new IntervalUtil(0.25f, 0.4f);
    private float systemStateChangeTime = 0f;
    private boolean wasActive = false;

    // ==================== TUNING PARAMETERS ====================

    // Flux level below which the AI may activate the system
    private static final float ACTIVATE_FLUX_THRESHOLD = 0.4f;

    // How long the system must remain active before the AI can toggle it off.
    // Prevents immediately deactivating on minor flux spikes.
    private static final float ACTIVATION_COMMITMENT = 2.0f;

    // Vent-based scaling ranges - cruiser max is 35 vents.
    // High-vent ships drain flux faster but recover faster; low-vent ships do the opposite.
    private static final int   MAX_VENTS_CRUISER = 35;
    private static final float DEACTIVATE_MIN = 0.7f; // high-vent end: deactivate sooner, recover fast
    private static final float DEACTIVATE_MAX = 0.8f; // low-vent end: run longer, recover slow
    private static final float REACTIVATE_MIN = 2.5f;  // high-vent end: short wait before re-activating
    private static final float REACTIVATE_MAX = 4.5f;  // low-vent end: long wait before re-activating

    // Computed once at init from the ship's actual vent count
    private float ventDeactivateThreshold;
    private float ventReactivateInterval;

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.engine = engine;
        this.flags = flags;
        this.system = system;
        this.systemStateChangeTime = engine.getTotalElapsedTime(false);

        float ventRatio = Math.min(1f, (float) ship.getVariant().getNumFluxVents() / MAX_VENTS_CRUISER);
        ventDeactivateThreshold = DEACTIVATE_MAX - ventRatio * (DEACTIVATE_MAX - DEACTIVATE_MIN);
        ventReactivateInterval  = REACTIVATE_MAX - ventRatio * (REACTIVATE_MAX - REACTIVATE_MIN);
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (engine.isPaused()) return;

        tracker.advance(amount);
        if (!tracker.intervalElapsed()) return;

        // Detect Stats-script forced deactivation and sync the reactivation timer
        boolean isActive = system.isActive();
        if (wasActive && !isActive && system.getCooldownRemaining() > 0f) {
            systemStateChangeTime = engine.getTotalElapsedTime(false);
        }
        wasActive = isActive;

        // System cooldown (from .system file) gates re-activation for player and AI alike
        if (system.getCooldownRemaining() > 0f) return;

        // The engine-provided target can be null. Fall back to the nearest enemy so
        // activation isn't gated on the tactical AI having assigned a target.
        ShipAPI effectiveTarget = (target != null) ? target : findNearestEnemy();

        if (shouldToggleSystem(effectiveTarget)) {
            ship.useSystem();
            systemStateChangeTime = engine.getTotalElapsedTime(false);
        }
    }

    // ==================== CORE DECISION LOGIC ====================

    private boolean shouldToggleSystem(ShipAPI target) {
        boolean isActive = system.isActive();
        if (!canToggleNow(isActive)) return false;
        return isActive ? shouldDeactivate(target) : shouldActivate(target);
    }

    private boolean canToggleNow(boolean isActive) {
        float timeSinceChange = engine.getTotalElapsedTime(false) - systemStateChangeTime;
        return isActive
                ? timeSinceChange >= ACTIVATION_COMMITMENT
                : timeSinceChange >= ventReactivateInterval;
    }

    private boolean shouldActivate(ShipAPI target) {
        if (hasHardBlock()) return false;
        if (ship.getFluxTracker().getFluxLevel() >= ACTIVATE_FLUX_THRESHOLD) return false;
        return target != null && !outTargetWeaponsRange(target);
    }

    private boolean shouldDeactivate(ShipAPI target) {
        if (ship.getFluxTracker().isOverloadedOrVenting()) return true;
        if (ship.getFluxTracker().getFluxLevel() >= ventDeactivateThreshold) return true;
        return target == null || outTargetWeaponsRange(target);
    }

    // ==================== CONDITION CHECKS ====================

    private boolean hasHardBlock() {
        if (ship.getFluxTracker().isOverloadedOrVenting()) return true;
        return flags.hasFlag(ShipwideAIFlags.AIFlags.RUN_QUICKLY);
    }

    /**
     * Returns true if the target is outside the maximum range of any active weapon.
     * A 20% range buffer avoids toggling off due to minor distance fluctuations.
     */
    private boolean outTargetWeaponsRange(ShipAPI target) {
        float distToTarget = MathUtils.getDistance(ship.getLocation(), target.getLocation());
        float maxRange = 0f;

        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (weapon.isDisabled() || weapon.isDecorative()) continue;
            float range = weapon.getRange();
            if (range > maxRange) maxRange = range;
        }

        return maxRange <= 0f || distToTarget > maxRange * 1.2f;
    }

    /**
     * Returns the nearest living, non-hulk, non-fighter enemy ship.
     * Used as a fallback when the engine-provided target parameter is null.
     */
    private ShipAPI findNearestEnemy() {
        ShipAPI nearest = null;
        float nearestDist = Float.MAX_VALUE;
        for (ShipAPI other : engine.getShips()) {
            if (other.getOwner() == ship.getOwner()) continue;
            if (other.isHulk() || !other.isAlive()) continue;
            if (other.getHullSize() == ShipAPI.HullSize.FIGHTER) continue;
            float dist = MathUtils.getDistance(ship.getLocation(), other.getLocation());
            if (dist < nearestDist) { nearestDist = dist; nearest = other; }
        }
        return nearest;
    }

}