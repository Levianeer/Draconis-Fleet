package levianeer.draconis.data.scripts.ai;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

public class fsdf_EAM_SuiteAI implements ShipSystemAIScript {

    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipSystemAPI system;

    private final IntervalUtil tracker = new IntervalUtil(0.4f, 0.8f);
    private float lastToggleTime = 0f;
    private float bestValueEver = 0f;
    private static final float MIN_TOGGLE_INTERVAL = 1.5f;
    private static final float SYSTEM_RANGE = 1500f;

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
        lastToggleTime += amount;

        if (tracker.intervalElapsed()) {
            if (system.getCooldownRemaining() > 0) return;

            float currentValue = calculateSystemValue(target);

            if (currentValue > bestValueEver) {
                bestValueEver = currentValue;
            }

            boolean shouldBeActive = shouldActivateSystem(currentValue);
            boolean currentlyActive = system.isActive();

            if (shouldBeActive != currentlyActive && canToggleNow()) {
                ship.useSystem();
                lastToggleTime = 0f;
            }
        }
    }

    private boolean canToggleNow() {
        return lastToggleTime >= MIN_TOGGLE_INTERVAL;
    }

    private boolean shouldActivateSystem(float systemValue) {
        // Don't use if we're too vulnerable
        if (isTooVulnerable()) return false;

        // High value situations - activate immediately
        if (systemValue >= 0.7f) return true;

        // Medium value with time gate
        if (systemValue >= 0.5f && lastToggleTime > 6f) return true;

        // Low value, long cooldown
        if (systemValue >= 0.3f && lastToggleTime > 12f) return true;

        return false;
    }

    private float calculateSystemValue(ShipAPI target) {
        float value = 0f;
        Vector2f shipLoc = ship.getLocation();

        int enemiesInRange = 0;
        int alliesInRange = 0;
        float totalEnemyStrength = 0f;
        float totalAllyStrength = 0f;

        // Count ships in range and calculate relative strength
        for (ShipAPI other : engine.getShips()) {
            if (other.isHulk() || other == ship) continue;

            float distance = Misc.getDistance(shipLoc, other.getLocation());
            if (distance > SYSTEM_RANGE) continue;

            float shipStrength = getShipStrength(other);

            if (other.getOwner() != ship.getOwner()) {
                enemiesInRange++;
                totalEnemyStrength += shipStrength;
            } else {
                alliesInRange++;
                totalAllyStrength += shipStrength;
            }
        }

        // No enemies in range = no value
        if (enemiesInRange == 0) return 0f;

        // Base value from enemy strength affected
        value += totalEnemyStrength * 0.15f;

        // Bonus for multiple targets
        if (enemiesInRange >= 2) value += 0.3f;
        if (enemiesInRange >= 3) value += 0.2f;

        // Major bonus if we have ally support (they benefit from enemy vulnerability)
        if (alliesInRange > 0) {
            float supportBonus = Math.min(totalAllyStrength * 0.2f, 0.4f);
            value += supportBonus;
        }

        // Penalty if we're heavily outnumbered (taking 25% more damage is dangerous)
        if (enemiesInRange > alliesInRange + 1) {
            value *= 0.7f;
        }

        // AI flag considerations
        if (flags.hasFlag(ShipwideAIFlags.AIFlags.BACKING_OFF)) {
            value *= 0.4f; // Much less valuable when retreating
        }

        if (flags.hasFlag(ShipwideAIFlags.AIFlags.PURSUING)) {
            value *= 1.2f; // More valuable when attacking
        }

        if (flags.hasFlag(ShipwideAIFlags.AIFlags.NEEDS_HELP) && alliesInRange > 0) {
            value *= 1.3f; // Help nearby allies finish enemies
        }

        // Target-specific bonuses
        if (target != null) {
            float targetDistance = Misc.getDistance(shipLoc, target.getLocation());
            if (targetDistance <= SYSTEM_RANGE) {
                // High value targets
                if (isHighValueTarget(target)) {
                    value += 0.25f;
                }

                // Vulnerable targets (already damaged/high flux)
                if (target.getFluxTracker().getFluxLevel() > 0.7f || target.getHullLevel() < 0.5f) {
                    value += 0.2f;
                }
            }
        }

        return Math.min(value, 1f);
    }

    private boolean isTooVulnerable() {
        // Don't activate if we're in critical danger (25% more damage is too risky)
        if (ship.getFluxTracker().isOverloaded()) return true;
        if (ship.getFluxTracker().getFluxLevel() > 0.85f) return true;
        if (ship.getHullLevel() < 0.25f) return true;

        // Don't activate if AI is trying to run away
        if (flags.hasFlag(ShipwideAIFlags.AIFlags.RUN_QUICKLY)) return true;

        return false;
    }

    private float getShipStrength(ShipAPI ship) {
        float baseStrength = switch (ship.getHullSize()) {
            case DESTROYER -> 2f;
            case CRUISER -> 4f;
            case CAPITAL_SHIP -> 8f;
            default -> 1f; // Frigate
        };

        // Reduce strength based on damage
        baseStrength *= ship.getHullLevel();

        // Reduce strength if ship is impaired
        if (ship.getFluxTracker().isOverloaded()) baseStrength *= 0.3f;
        if (ship.getEngineController().isDisabled()) baseStrength *= 0.5f;

        return baseStrength;
    }

    private boolean isHighValueTarget(ShipAPI target) {
        return target.getHullSize().ordinal() >= 2 || // Cruiser or larger
                target.isCapital() ||
                hasValuableWeapons(target);
    }

    private boolean hasValuableWeapons(ShipAPI target) {
        for (WeaponAPI weapon : target.getAllWeapons()) {
            if (weapon.getSize() == WeaponAPI.WeaponSize.LARGE) {
                return true;
            }
        }
        return false;
    }
}