package levianeer.draconis.data.scripts.ai;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * AI for the Breach Jammer
 * Activates whenever allies have enough firepower on enemies in the aura to
 * benefit from the damage-taken debuff, regardless of enemy return fire.
 * Self-preservation is handled separately via hard blocks (flux, hull, torpedoes).
 */
public class XLII_EAM_SuiteAI implements ShipSystemAIScript {

    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipSystemAPI system;

    // Evaluation timing
    private final IntervalUtil tracker = new IntervalUtil(0.3f, 0.5f);

    // State tracking
    private float systemStateChangeTime = 0f;

    // Constants
    private static final float MIN_TOGGLE_INTERVAL = 2.5f;
    private static final float ACTIVATION_COMMITMENT = 2.0f;
    private static final float SYSTEM_RANGE = 1800f;
    private static final float DEBUFF_MULTIPLIER = 1.25f;
    private static final float TORPEDO_THREAT_ANGLE = 0.85f; // Dot product threshold for torpedo threat
    private static final float MIN_ALLIED_FP_TO_ACTIVATE = 10f;
    private static final float MIN_ALLIED_FP_TO_DEACTIVATE = 5f; // Lower than activation threshold to avoid flicker

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.engine = engine;
        this.flags = flags;
        this.system = system;
        this.systemStateChangeTime = engine.getTotalElapsedTime(false);
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (engine.isPaused()) return;

        tracker.advance(amount);

        if (tracker.intervalElapsed()) {
            if (system.getCooldownRemaining() > 0) return;

            if (shouldToggleSystem()) {
                ship.useSystem();
                systemStateChangeTime = engine.getTotalElapsedTime(false);
            }
        }
    }

    // ==================== CORE DECISION LOGIC ====================

    private boolean shouldToggleSystem() {
        boolean isActive = system.isActive();

        if (!canToggleNow(isActive)) return false;

        List<ShipAPI> enemiesInAura = getEnemiesInAura();

        if (isActive) {
            return shouldDeactivate(enemiesInAura);
        } else {
            return shouldActivate(enemiesInAura);
        }
    }

    private boolean canToggleNow(boolean isActive) {
        float timeSinceChange = engine.getTotalElapsedTime(false) - systemStateChangeTime;
        if (isActive) {
            // Must stay active for commitment period
            return timeSinceChange >= ACTIVATION_COMMITMENT;
        } else {
            // Cooldown between toggles
            return timeSinceChange >= MIN_TOGGLE_INTERVAL;
        }
    }

    private boolean shouldDeactivate(List<ShipAPI> enemiesInAura) {
        if (hasHardBlock()) return true;
        if (hasIncomingTorpedoes()) return true;
        if (enemiesInAura.isEmpty()) return true;

        // Allies have stopped exploiting the debuff - no longer worth the self-vulnerability
        float alliedFP = calculateAlliedFirepowerOnAura(enemiesInAura);
        return alliedFP < MIN_ALLIED_FP_TO_DEACTIVATE;
    }

    private boolean shouldActivate(List<ShipAPI> enemiesInAura) {
        if (hasHardBlock()) return false;
        if (hasIncomingTorpedoes()) return false;
        if (enemiesInAura.isEmpty()) return false;
        if (is1v1Scenario(enemiesInAura)) return false;

        // The debuff amplifies allied damage - activate whenever allies have enough
        // firepower on these enemies to benefit, regardless of enemy return fire
        // (hard blocks above already cover self-preservation).
        float alliedFP = calculateAlliedFirepowerOnAura(enemiesInAura);
        return alliedFP >= MIN_ALLIED_FP_TO_ACTIVATE;
    }

    // ==================== HARD BLOCKS ====================

    private boolean hasHardBlock() {
        if (ship.getHullLevel() < 0.5f) return true;
        if (ship.getFluxTracker().isOverloaded()) return true;
        if (ship.getFluxTracker().isVenting()) return true;
        if (ship.getFluxTracker().getFluxLevel() > 0.8f && isUnderFire()) return true;
        if (flags.hasFlag(ShipwideAIFlags.AIFlags.RUN_QUICKLY)) return true;
        return flags.hasFlag(ShipwideAIFlags.AIFlags.MANEUVER_TARGET) &&
                ship.getFluxTracker().getFluxLevel() > 0.6f;
    }

    // ==================== THREAT DETECTION ====================

    private boolean isUnderFire() {
        return ship.getFluxTracker().getFluxLevel() > 0.3f &&
               ship.getFluxTracker().getTimeToVent() > 0f;
    }

    private boolean hasIncomingTorpedoes() {
        Vector2f shipLoc = ship.getLocation();

        for (MissileAPI missile : engine.getMissiles()) {
            if (missile.getSource() == null) continue;
            if (missile.getSource().getOwner() == ship.getOwner()) continue;
            if (missile.isFading() || missile.didDamage()) continue;

            float distance = MathUtils.getDistance(shipLoc, missile.getLocation());
            if (distance > SYSTEM_RANGE) continue;

            if (isTorpedo(missile)) {
                Vector2f toShip = Vector2f.sub(shipLoc, missile.getLocation(), new Vector2f());
                if (toShip.length() > 0f) {
                    toShip.normalise();
                    Vector2f missileVel = new Vector2f(missile.getVelocity());
                    if (missileVel.length() > 0f) {
                        missileVel.normalise();
                        float dot = Vector2f.dot(toShip, missileVel);
                        // Stricter check - must be heading nearly directly at us
                        if (dot > TORPEDO_THREAT_ANGLE) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean isTorpedo(MissileAPI missile) {
        if (missile.getWeaponSpec() == null) return false;
        return missile.getWeaponSpec().getSize() == WeaponAPI.WeaponSize.LARGE ||
               missile.getDamageAmount() > 500f;
    }

    // ==================== ENEMY DETECTION ====================

    private List<ShipAPI> getEnemiesInAura() {
        List<ShipAPI> enemies = new ArrayList<>();
        Vector2f shipLoc = ship.getLocation();
        float rangeSq = SYSTEM_RANGE * SYSTEM_RANGE;

        for (ShipAPI other : engine.getShips()) {
            if (other == ship || other.getOwner() == ship.getOwner()) continue;
            if (other.isHulk() || other.isFighter() || other.isPhased()) continue;

            // Squared distance check (faster)
            Vector2f otherLoc = other.getLocation();
            float dx = otherLoc.x - shipLoc.x;
            float dy = otherLoc.y - shipLoc.y;
            float distSq = dx * dx + dy * dy;

            if (distSq <= rangeSq) {
                enemies.add(other);
            }
        }

        return enemies;
    }

    // ==================== ALLIED ANALYSIS ====================

    private float calculateAlliedFirepowerOnAura(List<ShipAPI> enemiesInAura) {
        float totalFP = 0f;
        List<ShipAPI> allies = getAlliesInRange();

        for (ShipAPI ally : allies) {
            for (WeaponAPI weapon : ally.getAllWeapons()) {
                if (weapon.isDisabled()) continue;

                for (ShipAPI enemy : enemiesInAura) {
                    float distance = MathUtils.getDistance(ally.getLocation(), enemy.getLocation());

                    if (weapon.getSpec().getMaxRange() >= distance) {
                        float angleToEnemy = VectorUtils.getAngle(ally.getLocation(), enemy.getLocation());
                        float arcFacing = weapon.getCurrAngle();
                        float arcWidth = weapon.getArc();

                        if (arcWidth >= 360f || Math.abs(MathUtils.getShortestRotation(arcFacing, angleToEnemy)) <= arcWidth / 2f) {
                            totalFP += estimateWeaponDPS(weapon);
                            break; // Count weapon once even if it can hit multiple enemies
                        }
                    }
                }
            }
        }

        return totalFP * DEBUFF_MULTIPLIER;
    }

    private List<ShipAPI> getAlliesInRange() {
        List<ShipAPI> allies = new ArrayList<>();
        Vector2f shipLoc = ship.getLocation();
        float rangeSq = (float) 2250.0 * (float) 2250.0;

        for (ShipAPI other : engine.getShips()) {
            if (other == ship || other.getOwner() != ship.getOwner()) continue;
            if (other.isHulk() || other.isFighter()) continue;

            Vector2f otherLoc = other.getLocation();
            float dx = otherLoc.x - shipLoc.x;
            float dy = otherLoc.y - shipLoc.y;
            float distSq = dx * dx + dy * dy;

            if (distSq <= rangeSq) {
                allies.add(other);
            }
        }

        return allies;
    }

    // ==================== UTILITY METHODS ====================

    private boolean is1v1Scenario(List<ShipAPI> enemiesInAura) {
        if (enemiesInAura.size() != 1) return false;

        // Check if we have nearby allies
        List<ShipAPI> allies = getAlliesInRange();
        return allies.isEmpty();
    }

    private float estimateWeaponDPS(WeaponAPI weapon) {
        if (weapon.getSpec() == null) return 0f;

        float damage = weapon.getSpec().getDerivedStats().getDps();

        // Adjust for weapon size
        return switch (weapon.getSize()) {
            case SMALL -> damage * 0.5f;
            case MEDIUM -> damage;
            case LARGE -> damage * 2.0f;
        };
    }
}