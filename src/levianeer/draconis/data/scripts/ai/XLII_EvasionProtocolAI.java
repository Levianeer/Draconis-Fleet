package levianeer.draconis.data.scripts.ai;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lwjgl.util.vector.Vector2f;

public class XLII_EvasionProtocolAI implements ShipSystemAIScript {

    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipSystemAPI system;

    private final IntervalUtil tracker = new IntervalUtil(0.3f, 0.7f);
    private float lastActivationTime = 0f;
    private float bestOpportunityEver = 0f;

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.engine = engine;
        this.flags = flags;
        this.system = system;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (engine.isPaused()) return;

        tracker.advance(amount);
        lastActivationTime += amount;

        if (tracker.intervalElapsed()) {
            if (system.getCooldownRemaining() > 0) return;
            if (system.isActive()) return;

            float opportunity = calculateOpportunityValue(missileDangerDir, collisionDangerDir, target);

            if (opportunity > bestOpportunityEver) {
                bestOpportunityEver = opportunity;
            }

            // Check flux constraints
            float fluxLevel = ship.getFluxTracker().getFluxLevel();
            float remainingFluxCapacity = 1f - fluxLevel;
            float fluxCost = system.getFluxPerUse() / ship.getFluxTracker().getMaxFlux();

            if (fluxCost > remainingFluxCapacity) return;

            float fluxAfterUse = fluxLevel + fluxCost;
            if (fluxAfterUse > 0.85f && fluxCost > 0.1f) return;

            // Activation thresholds
            if (opportunity >= 0.6f) {
                ship.useSystem();
                lastActivationTime = 0f;
            } else if (opportunity >= 0.4f && lastActivationTime > 8f) {
                ship.useSystem();
                lastActivationTime = 0f;
            } else if (opportunity >= 0.3f && lastActivationTime > 15f) {
                ship.useSystem();
                lastActivationTime = 0f;
            }
        }
    }

    private float calculateOpportunityValue(Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        float opportunity = 0f;

        // Immediate threats - high value activation
        if (ship.getFluxTracker().isOverloaded()) opportunity += 0.8f;
        if (missileDangerDir != null) opportunity += 0.6f;
        if (collisionDangerDir != null) opportunity += 0.4f;

        // Offensive opportunities - positioning for advantage
        if (target != null) {
            float targetDistance = Vector2f.sub(ship.getLocation(), target.getLocation(), new Vector2f()).length();

            // Target is vulnerable - great time to reposition
            if (target.getFluxTracker().isOverloadedOrVenting()) {
                float vulnerabilityTime = Math.max(target.getFluxTracker().getOverloadTimeRemaining(),
                        target.getFluxTracker().getTimeToVent());
                if (vulnerabilityTime > 3f) {
                    opportunity += 0.7f;
                }
            }

            // We're at suboptimal range for our weapons
            float rangeOpportunity = calculateRangeOpportunity(targetDistance);
            opportunity += rangeOpportunity;
        }

        // AI flag-based opportunities
        if (flags.hasFlag(ShipwideAIFlags.AIFlags.PURSUING) && ship.getVelocity().length() < ship.getMaxSpeed() * 0.5f) {
            opportunity += 0.5f; // Boost pursuit speed
        }

        if (flags.hasFlag(ShipwideAIFlags.AIFlags.MAINTAINING_STRIKE_RANGE) && ship.getFluxTracker().getFluxLevel() > 0.6f) {
            opportunity += 0.4f; // Help maintain position under flux pressure
        }

        if (flags.hasFlag(ShipwideAIFlags.AIFlags.MOVEMENT_DEST) && ship.getVelocity().length() < ship.getMaxSpeed() * 0.3f) {
            opportunity += 0.3f; // Overcome movement impediments
        }

        // Tactical repositioning opportunities
        if (flags.hasFlag(ShipwideAIFlags.AIFlags.BACKING_OFF) && isGoodRepositioningMoment()) {
            opportunity += 0.5f;
        }

        if (flags.hasFlag(ShipwideAIFlags.AIFlags.MANEUVER_RANGE_FROM_TARGET)) {
            opportunity += 0.3f;
        }

        // Health-based urgency modifiers
        float healthMultiplier = 1f;
        if (ship.getHullLevel() < 0.5f) healthMultiplier = 1.3f;
        if (ship.getHullLevel() < 0.25f) healthMultiplier = 1.6f;

        // Flux-based urgency
        if (ship.getFluxTracker().getFluxLevel() > 0.7f) opportunity += 0.2f;

        return Math.min(opportunity * healthMultiplier, 1f);
    }

    private float calculateRangeOpportunity(float targetDistance) {
        float optimalRange = getOptimalWeaponRange();
        if (optimalRange <= 0f) return 0f;

        float deviation = Math.abs(targetDistance - optimalRange);
        float maxDeviation = optimalRange * 0.8f; // 80% deviation is max opportunity

        if (deviation > maxDeviation) return 0.3f; // Significant range problem
        if (deviation > optimalRange * 0.4f) return 0.2f; // Moderate range problem

        return 0f; // Range is acceptable
    }

    private float getOptimalWeaponRange() {
        float totalRange = 0f;
        float totalWeight = 0f;

        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (weapon.getSize() == WeaponAPI.WeaponSize.SMALL) continue; // Focus on main weapons
            if (weapon.isDisabled()) continue;

            float weight = weapon.getSize() == WeaponAPI.WeaponSize.MEDIUM ? 2f : 4f;
            float effectiveRange = weapon.getRange() * 0.8f; // Don't fight at max range

            totalRange += effectiveRange * weight;
            totalWeight += weight;
        }

        return totalWeight > 0f ? totalRange / totalWeight : 0f;
    }

    private boolean isGoodRepositioningMoment() {
        // Look for nearby enemies to see if repositioning would help
        int nearbyThreats = 0;
        for (ShipAPI enemy : engine.getShips()) {
            if (enemy.getOwner() == ship.getOwner()) continue;
            if (enemy.isHulk()) continue;

            float distance = Vector2f.sub(ship.getLocation(), enemy.getLocation(), new Vector2f()).length();
            if (distance < 800f) {
                nearbyThreats++;
            }
        }

        // If outnumbered or being focused, repositioning is valuable
        return nearbyThreats >= 2 || (nearbyThreats >= 1 && ship.getHullLevel() < 0.6f);
    }
}