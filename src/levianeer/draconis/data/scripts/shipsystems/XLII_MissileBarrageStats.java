package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Missile Barrage ship system for Yunu
 * Toggle system that launches continuous missile volleys at the nearest enemy in forward arc.
 */
public class XLII_MissileBarrageStats extends BaseShipSystemScript {

    private static final Logger log = Global.getLogger(XLII_MissileBarrageStats.class);

    // ==================== TUNING PARAMETERS ====================

    private static final String WEAPON_ID = "XLII_sabre";
    private static final float LAUNCH_DELAY_PER_MISSILE = 0.25f; // 250ms between each missile for visual effect

    // Toggle system penalties
    private static final float SPEED_MULT = 0.9f; // 10% speed reduction

    // Toggle system buffs
    private static final float WEAPON_DAMAGE_BONUS = 1.25f; // 25% damage increase
    private static final float WEAPON_ROF_BONUS = 1.25f; // 25% fire rate increase

    // Volley timing - fires at nearest enemy in forward arc
    private static final float VOLLEY_INTERVAL = 9.0f; // Fixed interval between volleys
    private static final float FORWARD_ARC_DEGREES = 120.0f; // Targeting arc (60° each side of facing)

    // Visual effects
    private static final Color LAUNCH_ARC_COLOR = new Color(125, 125, 255, 205);

    // Track volley timer per ship
    private static final java.util.HashMap<String, Float> volleyTimers = new java.util.HashMap<>();

    // Cache missile range per ship
    private static final java.util.HashMap<String, Float> missileRangeCache = new java.util.HashMap<>();

    // ==================== MAIN SYSTEM LOGIC ====================

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (stats.getEntity() instanceof ShipAPI) ? (ShipAPI) stats.getEntity() : null;
        if (ship == null) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        String shipId = ship.getId();

        // Apply stat modifiers while active
        if (effectLevel > 0f) {
            // Apply speed reduction
            stats.getMaxSpeed().modifyMult(id, SPEED_MULT);
            stats.getAcceleration().modifyMult(id, SPEED_MULT);
            stats.getDeceleration().modifyMult(id, SPEED_MULT);

            // Apply weapon buffs
            stats.getBallisticWeaponDamageMult().modifyMult(id, WEAPON_DAMAGE_BONUS);
            stats.getEnergyWeaponDamageMult().modifyMult(id, WEAPON_DAMAGE_BONUS);
            stats.getBallisticRoFMult().modifyMult(id, WEAPON_ROF_BONUS);
            stats.getEnergyRoFMult().modifyMult(id, WEAPON_ROF_BONUS);

            // Handle volley launches
            if (!engine.isPaused()) {
                float timer = volleyTimers.getOrDefault(shipId, 0f);
                timer -= engine.getElapsedInLastFrame();

                if (timer <= 0f) {
                    // Fire volley at nearest target in forward arc
                    launchVolley(ship, engine);

                    // Reset to fixed interval
                    timer = VOLLEY_INTERVAL;
                }

                volleyTimers.put(shipId, timer);
            }
        } else {
            // Remove stat modifiers when not active
            stats.getHullDamageTakenMult().unmodify(id);
            stats.getArmorDamageTakenMult().unmodify(id);
            stats.getShieldDamageTakenMult().unmodify(id);
            stats.getMaxSpeed().unmodify(id);
            stats.getAcceleration().unmodify(id);
            stats.getDeceleration().unmodify(id);
            stats.getBallisticWeaponDamageMult().unmodify(id);
            stats.getEnergyWeaponDamageMult().unmodify(id);
            stats.getBallisticRoFMult().unmodify(id);
            stats.getEnergyRoFMult().unmodify(id);

            // Reset timer when system deactivates
            volleyTimers.put(shipId, 0f);
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        // Remove all stat modifiers
        stats.getHullDamageTakenMult().unmodify(id);
        stats.getArmorDamageTakenMult().unmodify(id);
        stats.getShieldDamageTakenMult().unmodify(id);
        stats.getMaxSpeed().unmodify(id);
        stats.getAcceleration().unmodify(id);
        stats.getDeceleration().unmodify(id);
        stats.getBallisticWeaponDamageMult().unmodify(id);
        stats.getEnergyWeaponDamageMult().unmodify(id);
        stats.getBallisticRoFMult().unmodify(id);
        stats.getEnergyRoFMult().unmodify(id);
    }

    @Override
    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        if (system.isOutOfAmmo()) return null;
        if (system.getState() == ShipSystemAPI.SystemState.ACTIVE) return "BARRAGING";
        if (system.getState() != ShipSystemAPI.SystemState.IDLE) return null;

        ShipAPI target = findNearestTargetInForwardArc(ship);
        if (target != null) {
            return "READY";
        }
        return "NO TARGET";
    }

    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        // System is always usable if not out of ammo
        return !system.isOutOfAmmo();
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        float speedPenalty = (1f - SPEED_MULT) * 100f;
        float damageBonus = (WEAPON_DAMAGE_BONUS - 1f) * 100f;
        float rofBonus = (WEAPON_ROF_BONUS - 1f) * 100f;
        return switch (index) {
            case 0 -> new StatusData("+" + "weapon damage +" + (int)damageBonus, false);
            case 1 -> new StatusData("+" + "rate of fire +" + (int)rofBonus, false);
            case 2 -> new StatusData("-" + (int)speedPenalty + "% speed", true);
            default -> null;
        };
    }

    // ==================== VOLLEY LAUNCH ====================

    /**
     * Launches a volley of missiles at nearest enemy in forward arc.
     * All missiles in a volley target the same enemy, fired sequentially with small delay.
     */
    private void launchVolley(ShipAPI ship, CombatEngineAPI engine) {
        // Get all SYSTEM weapon slots
        List<WeaponSlotAPI> systemSlots = getSystemWeaponSlots(ship);

        if (systemSlots.isEmpty()) {
            log.warn("Draconis: Missile Barrage - No SYSTEM weapon slots found on " + ship.getHullSpec().getHullId());
            return;
        }

        // Find nearest target in forward arc
        ShipAPI target = findNearestTargetInForwardArc(ship);
        if (target == null) return;

        log.info("Draconis: Missile Barrage - Firing volley: " + systemSlots.size() +
                 " missiles at " + target.getHullSpec().getHullName());

        // Fire all missiles sequentially at the target
        float delay = 0f;

        for (WeaponSlotAPI slot : systemSlots) {
            final float launchDelay = delay;
            scheduleDelayedLaunch(ship, slot, target, engine, launchDelay);

            delay += LAUNCH_DELAY_PER_MISSILE;
        }
    }

    /**
     * Schedules a delayed missile launch from a weapon slot.
     */
    private void scheduleDelayedLaunch(ShipAPI ship, WeaponSlotAPI slot, ShipAPI target,
                                       CombatEngineAPI engine, float delay) {
        if (delay <= 0.001f) {
            // Launch immediately
            launchMissileFromSlot(ship, slot, target, engine);
        } else {
            // Schedule for later using a simple combat script
            engine.addPlugin(new DelayedLaunchScript(ship, slot, target, delay));
        }
    }

    /**
     * Launches a single missile from a weapon slot.
     */
    private void launchMissileFromSlot(ShipAPI ship, WeaponSlotAPI slot, ShipAPI target,
                                       CombatEngineAPI engine) {
        Vector2f slotPos = slot.computePosition(ship);
        float slotAngle = slot.computeMidArcAngle(ship);

        // Calculate launch direction (toward target)
        float launchAngle = Misc.getAngleInDegrees(slotPos, target.getLocation());

        // Create a fresh fake weapon for THIS missile only (each missile gets full ammo)
        WeaponAPI freshWeapon = engine.createFakeWeapon(ship, WEAPON_ID);

        // Spawn the missile using the fresh weapon
        MissileAPI missile = (MissileAPI) engine.spawnProjectile(
            ship,
            freshWeapon,
            WEAPON_ID,
            slotPos,
            slotAngle,
            new Vector2f()
        );

        if (missile != null) {
            // Apply flux cost for firing the missile
            ship.getFluxTracker().increaseFlux(500f, true);

            // Set velocity toward target
            Vector2f velocity = Misc.getUnitVectorAtDegreeAngle(launchAngle);
            missile.getVelocity().set(velocity);

            // Set the missile's AI target to the volley's selected target
            // This ensures all missiles in a volley attack the same enemy
            if (missile.getMissileAI() instanceof GuidedMissileAI ai) {
                ai.setTarget(target);
            }

            // Visual effect: launch arc
            engine.spawnEmpArcVisual(
                ship.getLocation(),
                ship,
                slotPos,
                missile,
                50f,
                LAUNCH_ARC_COLOR,
                Color.WHITE
            );

            // Smoke effect at launch point
            for (int i = 0; i < 3; i++) {
                Vector2f smokeVel = Misc.getUnitVectorAtDegreeAngle(slotAngle + (float)(Math.random() * 40 - 20));
                smokeVel.scale(20f + (float)(Math.random() * 20f));

                engine.addSmokeParticle(
                    slotPos,
                    smokeVel,
                    30f + (float)(Math.random() * 10f),
                    0.8f,
                    1f + (float)(Math.random() * 0.5f),
                    new Color(170, 160, 150, 100)
                );
            }
        }
    }

    /**
     * Gets all SYSTEM type weapon slots from the ship.
     */
    private List<WeaponSlotAPI> getSystemWeaponSlots(ShipAPI ship) {
        List<WeaponSlotAPI> systemSlots = new ArrayList<>();

        for (WeaponSlotAPI slot : ship.getHullSpec().getAllWeaponSlotsCopy()) {
            if (slot.isSystemSlot()) {
                systemSlots.add(slot);
            }
        }

        return systemSlots;
    }

    // ==================== TARGET FINDING ====================

    /**
     * Finds the nearest valid enemy target within forward arc and missile range.
     * Returns null if no valid target found.
     */
    private ShipAPI findNearestTargetInForwardArc(ShipAPI ship) {
        float missileRange = getMissileRange(ship);
        CombatEngineAPI engine = Global.getCombatEngine();

        if (engine == null) return null;

        ShipAPI nearestTarget = null;
        float nearestDistance = Float.MAX_VALUE;
        float shipFacing = ship.getFacing();

        // Find nearest enemy in forward arc
        for (ShipAPI enemy : engine.getShips()) {
            if (enemy.getOwner() == ship.getOwner()) continue;
            if (enemy.isHulk()) continue;
            if (!enemy.isAlive()) continue;

            // Skip fighters - system targets larger ships only
            if (enemy.getHullSize() == ShipAPI.HullSize.FIGHTER) continue;

            // Check if in range
            float distance = Misc.getDistance(ship.getLocation(), enemy.getLocation());
            float radSum = ship.getCollisionRadius() + enemy.getCollisionRadius();

            if (distance - radSum > missileRange) continue;

            // Check if in forward arc
            float angleToTarget = Misc.getAngleInDegrees(ship.getLocation(), enemy.getLocation());
            float angleDiff = MathUtils.getShortestRotation(shipFacing, angleToTarget);

            if (Math.abs(angleDiff) <= FORWARD_ARC_DEGREES / 2f) {
                // Target is in arc and range
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestTarget = enemy;
                }
            }
        }

        return nearestTarget;
    }

    /**
     * Gets the missile weapon range from the weapon spec.
     * Caches the result per ship for performance.
     */
    private float getMissileRange(ShipAPI ship) {
        String shipId = ship.getId();

        if (missileRangeCache.containsKey(shipId)) {
            return missileRangeCache.get(shipId);
        }

        // Get range from weapon spec
        WeaponSpecAPI weaponSpec = Global.getSettings().getWeaponSpec(WEAPON_ID);
        float range = 1500f; // Default fallback

        if (weaponSpec != null) {
            range = weaponSpec.getMaxRange();
            log.info("Draconis: Missile Barrage - Missile range determined as " + range + " from weapon spec");
        } else {
            log.warn("Draconis: Missile Barrage - Could not find weapon spec for " + WEAPON_ID +
                     ", using default range " + range);
        }

        missileRangeCache.put(shipId, range);
        return range;
    }

    // ==================== DELAYED LAUNCH SCRIPT ====================

    /**
     * Simple combat plugin to handle delayed missile launches.
     */
    private class DelayedLaunchScript extends BaseEveryFrameCombatPlugin {
        private final ShipAPI ship;
        private final WeaponSlotAPI slot;
        private final ShipAPI target;
        private float timer;

        public DelayedLaunchScript(ShipAPI ship, WeaponSlotAPI slot, ShipAPI target, float delay) {
            this.ship = ship;
            this.slot = slot;
            this.target = target;
            this.timer = delay;
        }

        @Override
        public void advance(float amount, java.util.List<com.fs.starfarer.api.input.InputEventAPI> events) {
            if (Global.getCombatEngine().isPaused()) return;

            timer -= amount;
            if (timer <= 0f) {
                CombatEngineAPI engine = Global.getCombatEngine();
                if (engine != null && ship.isAlive() && !ship.isHulk() &&
                    target.isAlive() && !target.isHulk()) {
                    launchMissileFromSlot(ship, slot, target, engine);
                }
                assert Global.getCombatEngine() != null;
                Global.getCombatEngine().removePlugin(this);
            }
        }
    }
}