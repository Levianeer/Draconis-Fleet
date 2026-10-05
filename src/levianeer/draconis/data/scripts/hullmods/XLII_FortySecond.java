package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.Global;
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

public class XLII_FortySecond extends XLII_SystemHullModBase {

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

    public static float PROFILE_MULT = 0.9f;
    public static float MISSILE_AFFECT_CHANCE = 0.2f;
    public static final float DEGRADE_INCREASE_PERCENT = 100f;

    // ID and bonus for the upgrade hullmod (@XLII_FortySecondMk2)
    public static final String UPGRADE_HULLMOD_ID = "XLII_fortysecond_mk2";
    public static final float UPGRADE_CHANCE_BONUS = 0.3f;
    private static final Color JAMMER_COLOR = new Color(50, 50, 255, 155);
    private static final Color CONVERSION_COLOR = new Color(50, 255, 50, 155);

    // Track processed missiles per ship to avoid re-rolling every frame
    private static final Map<ShipAPI, Set<MissileAPI>> processedMissiles = new HashMap<>();
    private static CombatEngineAPI lastEngine_FortySecond;

    private static void checkClearProcessedMissiles() {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine != lastEngine_FortySecond) {
            lastEngine_FortySecond = engine;
            processedMissiles.clear();
        }
    }

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getSightRadiusMod().modifyFlat(id, combatMag.get(hullSize));
        stats.getSensorProfile().modifyMult(id, PROFILE_MULT);
        stats.getCRLossPerSecondPercent().modifyPercent(id, DEGRADE_INCREASE_PERCENT);
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        checkClearProcessedMissiles();

        if (ship == null || !ship.isAlive()) {
            processedMissiles.remove(ship);
            return;
        }

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;

        if (ship.getFluxTracker().isOverloaded() ||  // Ship is overloaded (can't use systems)
            ship.isPhased()                          // Ship is phased (can't interact with missiles)
        ) {
            return;
        }

        Float defenseRangeMult = missileDefenseRange.get(ship.getHullSize());
        if (defenseRangeMult == null) return;
        float defenseRange = ship.getCollisionRadius() * defenseRangeMult;

        // Cache ship location and owner to avoid repeated method calls
        Vector2f shipLocation = ship.getLocation();
        int owner = ship.getOwner();

        // Use LazyLib's optimized spatial query instead of iterating all missiles
        List<MissileAPI> nearbyMissiles = CombatUtils.getMissilesWithinRange(shipLocation, defenseRange);
        if (nearbyMissiles.isEmpty()) return;

        Set<MissileAPI> processed = processedMissiles.computeIfAbsent(ship, k -> new HashSet<>());

        // Clean up processed missiles - only check if fading (more efficient)
        processed.removeIf(MissileAPI::isFading);

        // Pre-square range for faster distance comparisons
        float defenseRangeSq = defenseRange * defenseRange;

        for (MissileAPI missile : nearbyMissiles) {
            if (processed.contains(missile)) continue;

            if (missile.isFading()) continue;

            // Skip SLAP-ER torps - defense interacting with them causes bugs in DDA mirror matches
            if ("XLII_SLAP-ER_torp".equals(missile.getProjectileSpecId())) continue;

            ShipAPI source = missile.getSource();
            if (source == null || source.getOwner() == owner) continue;

            // Check if missile is within range using squared distance (avoids expensive sqrt)
            float distSq = MathUtils.getDistanceSquared(shipLocation, missile.getLocation());
            if (distSq > defenseRangeSq) continue;

            // Mark as processed regardless of outcome
            processed.add(missile);

            // Roll chance to affect this missile (boosted if upgrade hullmod is also installed)
            float effectiveChance = MISSILE_AFFECT_CHANCE;
            if (ship.getVariant().hasHullMod(UPGRADE_HULLMOD_ID)) {
                effectiveChance = Math.min(1f, effectiveChance + UPGRADE_CHANCE_BONUS);
            }
            if (Math.random() > effectiveChance) continue;

            // Check if missile is guided - unguided missiles can't be retargeted
            boolean isGuided = missile.getUnwrappedMissileAI() instanceof GuidedMissileAI;

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

        spawnJamParticle(missile.getLocation());
    }

    private void convertMissile(MissileAPI missile, ShipAPI ship) {
        // Double-check that this is a guided missile (should already be checked in advance)
        if (!(missile.getUnwrappedMissileAI() instanceof GuidedMissileAI ai)) {
            jamMissile(missile);
            return;
        }

        ShipAPI originalSource = missile.getSource();
        if (originalSource == null || !originalSource.isAlive()) {
            jamMissile(missile);
            return;
        }

        missile.setOwner(ship.getOwner());
        missile.setSource(ship);

        // Set collision class to prevent friendly fire while still hitting enemies
        missile.setCollisionClass(CollisionClass.MISSILE_NO_FF);

        ai.setTarget(originalSource);

        // Set ECCM to help it reach the target
        missile.setEccmChanceOverride(1f);

        // Reset missile timer by extending max flight time by elapsed time
        // This gives the missile a fresh lifetime to reach its new target
        float elapsedTime = missile.getFlightTime();
        missile.setMaxFlightTime(missile.getMaxFlightTime() + elapsedTime);

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

    @Override
    public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return false;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();

        tooltip.addPara("XLII custom hulls have a number of shared properties.", opad);

        addTransverseJumpTooltipSection(tooltip, opad, h);

        tooltip.addSectionHeading("Sensors & Detection", Alignment.MID, opad);
        tooltip.addPara("Increases ship's in-combat vision range by %s/%s/%s/%s and reduces sensor profile by %s.",
                opad, h,
                combatMag.get(HullSize.FRIGATE).intValue() + "",
                combatMag.get(HullSize.DESTROYER).intValue() + "",
                combatMag.get(HullSize.CRUISER).intValue() + "",
                combatMag.get(HullSize.CAPITAL_SHIP).intValue() + "",
                Math.round((1f - PROFILE_MULT) * 100f) + "%");

        tooltip.addSectionHeading("Missile Defense", Alignment.MID, opad);
        tooltip.addPara("Ship has a %s chance to jam or convert incoming missiles within range. Jammed missiles are neutralized; guided missiles may instead be retargeted to their source.",
                opad, h,
                Math.round(MISSILE_AFFECT_CHANCE * 100f) + "%");

        tooltip.addSectionHeading("Maintenance", new Color(255,100,0) , new Color(105,40,0,175) , Alignment.MID, opad);
        tooltip.addPara("XLII custom hulls require excessive maintenance and do not stand up well under the rigours of prolonged engagements.", opad);

        tooltip.addPara("Increases the rate of in-combat CR decay after peak performance time runs out by %s.",
                opad, h,
                (int) DEGRADE_INCREASE_PERCENT + "%");
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