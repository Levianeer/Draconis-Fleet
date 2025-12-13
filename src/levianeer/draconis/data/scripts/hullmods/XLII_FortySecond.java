package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.GuidedMissileAI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class XLII_FortySecond extends BaseHullMod {

    // Ready for pain? 'bout to make it RAIN
    private static final Map<HullSize, Float> combatMag = new HashMap<>();
    static {
        combatMag.put(HullSize.FRIGATE, 250f);
        combatMag.put(HullSize.DESTROYER, 500f);
        combatMag.put(HullSize.CRUISER, 750f);
        combatMag.put(HullSize.CAPITAL_SHIP, 1000f);
    }

    private static final Map<HullSize, Float> missileDefenseRange = new HashMap<>();
    // Missile defense range = collision radius * this multiplier
    static {
        missileDefenseRange.put(HullSize.FRIGATE, 5f);
        missileDefenseRange.put(HullSize.DESTROYER, 4f);
        missileDefenseRange.put(HullSize.CRUISER, 3f);
        missileDefenseRange.put(HullSize.CAPITAL_SHIP, 2f);
    }

    public static float PROFILE_MULT = 0.75f;
    public static float MISSILE_AFFECT_CHANCE = 0.5f; // % chance to affect each missile
    private static final Color JAMMER_COLOR = new Color(50, 50, 255, 155);
    private static final Color CONVERSION_COLOR = new Color(50, 255, 50, 155);

    // Track processed missiles per ship to avoid re-rolling every frame
    private static final Map<ShipAPI, Set<MissileAPI>> processedMissiles = new HashMap<>();

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getSightRadiusMod().modifyFlat(id, combatMag.get(hullSize));
        stats.getSensorProfile().modifyMult(id, PROFILE_MULT);
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive()) {
            processedMissiles.remove(ship);
            return;
        }

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        // Disable missile defense when ship is in abnormal states
        if (ship.getFluxTracker().isOverloaded() ||  // Ship is overloaded (can't use systems)
            ship.isPhased()                          // Ship is phased (can't interact with missiles)
        ) {
            return;
        }

        // Get effective defense range based on ship collision radius plus hull-size bonus
        float defenseRange = (ship.getCollisionRadius() * missileDefenseRange.get(ship.getHullSize()));

        // Cache ship location and owner to avoid repeated method calls
        Vector2f shipLocation = ship.getLocation();
        int owner = ship.getOwner();

        // Use LazyLib's optimized spatial query instead of iterating all missiles
        List<MissileAPI> nearbyMissiles = CombatUtils.getMissilesWithinRange(shipLocation, defenseRange);
        if (nearbyMissiles.isEmpty()) return;  // Early exit if no missiles nearby

        // Get or create the set of processed missiles for this ship
        Set<MissileAPI> processed = processedMissiles.computeIfAbsent(ship, k -> new HashSet<>());

        // Clean up processed missiles - only check if fading (more efficient)
        processed.removeIf(MissileAPI::isFading);

        // Pre-square range for faster distance comparisons
        float defenseRangeSq = defenseRange * defenseRange;

        // Check only nearby missiles
        for (MissileAPI missile : nearbyMissiles) {
            // Skip if already processed
            if (processed.contains(missile)) continue;

            // Skip if missile is already fading
            if (missile.isFading()) continue;

            // Skip if not a hostile missile
            ShipAPI source = missile.getSource();
            if (source == null || source.getOwner() == owner) continue;

            // Check if missile is within range using squared distance (avoids expensive sqrt)
            float distSq = MathUtils.getDistanceSquared(shipLocation, missile.getLocation());
            if (distSq > defenseRangeSq) continue;

            // Mark as processed regardless of outcome
            processed.add(missile);

            // Roll chance to affect this missile
            if (Math.random() > MISSILE_AFFECT_CHANCE) continue;

            // Check if missile is guided - unguided missiles can't be retargeted
            boolean isGuided = missile.getAI() instanceof GuidedMissileAI;

            // 50/50 chance: jam or convert (but only convert if guided)
            boolean shouldJam = Math.random() < 0.5f;

            if (shouldJam || !isGuided) {
                // Jam and disable the missile (always jam unguided missiles)
                jamMissile(missile);
            } else {
                // Convert to friendly and retarget (only for guided missiles)
                convertMissile(missile, ship);
            }
        }
    }

    private void jamMissile(MissileAPI missile) {
        // Disable missile like ECM Suite
        missile.setDamageAmount(0);
        missile.setOwner(100); // Neutral owner
        missile.setMissileAI(null);
        missile.setCollisionClass(CollisionClass.NONE);
        missile.flameOut();

        // Visual feedback
        spawnJamParticle(missile.getLocation());
    }

    private void convertMissile(MissileAPI missile, ShipAPI ship) {
        // Double-check that this is a guided missile (should already be checked in advance)
        if (!(missile.getAI() instanceof GuidedMissileAI ai)) {
            jamMissile(missile);
            return;
        }

        ShipAPI originalSource = missile.getSource();
        if (originalSource == null || !originalSource.isAlive()) {
            // If original source is dead, just jam it
            jamMissile(missile);
            return;
        }

        // Change ownership to friendly
        missile.setOwner(ship.getOwner());
        missile.setSource(ship);

        // Set collision class to prevent friendly fire while still hitting enemies
        missile.setCollisionClass(CollisionClass.MISSILE_NO_FF);

        // Retarget to the original firing ship using GuidedMissileAI
        ai.setTarget(originalSource);

        // Set ECCM to help it reach the target
        missile.setEccmChanceOverride(1f);

        // Reset missile timer by extending max flight time by elapsed time
        // This gives the missile a fresh lifetime to reach its new target
        float elapsedTime = missile.getFlightTime();
        missile.setMaxFlightTime(missile.getMaxFlightTime() + elapsedTime);

        // Visual feedback
        spawnConversionParticle(missile.getLocation());
    }

    private void spawnJamParticle(Vector2f location) {
        Global.getCombatEngine().addHitParticle(
                location,
                new Vector2f(0f, 0f),
                10f,
                1f,
                0.15f,
                JAMMER_COLOR
        );
    }

    private void spawnConversionParticle(Vector2f location) {
        Global.getCombatEngine().addHitParticle(
                location,
                new Vector2f(0f, 0f),
                10f,
                1f,
                0.15f,
                CONVERSION_COLOR
        );
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return "" + combatMag.get(HullSize.FRIGATE).intValue();
        if (index == 1) return "" + combatMag.get(HullSize.DESTROYER).intValue();
        if (index == 2) return "" + combatMag.get(HullSize.CRUISER).intValue();
        if (index == 3) return "" + combatMag.get(HullSize.CAPITAL_SHIP).intValue();
        if (index == 4) return Math.round((1f - PROFILE_MULT) * 100f) + "%";
        if (index == 5) return Math.round(MISSILE_AFFECT_CHANCE * 100f) + "%";
        return null;
    }
}