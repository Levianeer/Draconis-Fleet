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
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.scripts.XLII_MistCloudsPlugin;
import org.apache.log4j.Logger;
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

    private static final Logger log = Global.getLogger(XLII_FortySecond.class);

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

        // Register Mist Clouds plugin once per combat (check engine custom data)
        if (!engine.getCustomData().containsKey("XLII_MIST_CLOUDS_PLUGIN")) {
            registerMistCloudsPlugin(engine);
        }

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

    /**
     * Register the Mist Clouds combat plugin once per battle
     */
    private void registerMistCloudsPlugin(CombatEngineAPI engine) {
        try {
            XLII_MistCloudsPlugin plugin = new XLII_MistCloudsPlugin();
            engine.addPlugin(plugin);
            engine.getCustomData().put("XLII_MIST_CLOUDS_PLUGIN", plugin);
            log.info("Draconis: Mist Clouds plugin registered successfully");
        } catch (Exception e) {
            log.error("Draconis: Failed to register Mist Clouds plugin: " + e.getMessage(), e);
        }
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

    @Override
    public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return false;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        Color t = Misc.getTextColor();

        tooltip.addPara("Ships equipped with XLII Battlegroup avionics and tactical systems gain advanced capabilities.", opad);

        tooltip.addSectionHeading("Sensors & Detection", Alignment.MID, opad);
        tooltip.addPara("Sight radius increased by %s/%s/%s/%s. Sensor profile reduced by %s.",
                opad, h,
                combatMag.get(HullSize.FRIGATE).intValue() + "",
                combatMag.get(HullSize.DESTROYER).intValue() + "",
                combatMag.get(HullSize.CRUISER).intValue() + "",
                combatMag.get(HullSize.CAPITAL_SHIP).intValue() + "",
                Math.round((1f - PROFILE_MULT) * 100f) + "%");

        tooltip.addSectionHeading("Missile Defense", Alignment.MID, opad);
        tooltip.addPara("Has %s chance to affect incoming missiles within range. Affected missiles are either %s (neutralized) or %s (retargeted to their source). Only guided missiles can be converted.",
                opad, h,
                Math.round(MISSILE_AFFECT_CHANCE * 100f) + "%",
                "jammed",
                "converted");

        tooltip.addPara("The defense's range scales with hull size, multiplied by %s/%s/%s/%s.",
                opad, h,
                missileDefenseRange.get(HullSize.FRIGATE).intValue() + "x",
                missileDefenseRange.get(HullSize.DESTROYER).intValue() + "x",
                missileDefenseRange.get(HullSize.CRUISER).intValue() + "x",
                missileDefenseRange.get(HullSize.CAPITAL_SHIP).intValue() + "x");

        tooltip.addSectionHeading("Nanomist Unit", Alignment.MID, opad);
        tooltip.addPara("Fitting XLII Battlegroup doctrine, multiple large cruise missiles are deployed in a circuit formation behind engagement lines - these missiles are then sent targets periodically during combat.",
                opad, h);

        tooltip.addPara("Deploys SLAP-ER Cruise Missiles that spread tactical nanomist swarms throughout combat. Clouds have %s radius and fade after 60 - 90 seconds.",
                opad, h,
                "1200",
                "60",
                "90"
                );

        tooltip.addPara("Allied ships in clouds gain %s hull repair per second when below maximum hull. Enemy ships suffer %s hull damage per second. Total healing or damage per ship is capped at %s hull points or %s of maximum hull, whichever is higher. Phased ships are immune to mist damage.",
                opad, h,
                "+0.5%",
                "-0.5%",
                "2000",
                "50%");

        tooltip.addPara("SLAP-ER missile deployment requires a minimum of %s XLII ships in the fleet AND a minimum of %s total deployment points. Both thresholds must be met for missiles to spawn.",
                opad, h,
                "25%",
                "50");
    }

    @Override
    public int getDisplaySortOrder() {
        return 1;
    }

    @Override
    public int getDisplayCategoryIndex() {
        return 0;
    }
}