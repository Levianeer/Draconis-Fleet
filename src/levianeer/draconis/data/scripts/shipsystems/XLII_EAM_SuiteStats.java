package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.*;
import java.util.*;

import static com.fs.starfarer.api.impl.combat.EntropyAmplifierStats.KEY_TARGET;

public class XLII_EAM_SuiteStats extends BaseShipSystemScript {

    private static final float MAX_RANGE = 1500f;
    private static final float DAMAGE_VULNERABILITY = 1.25f;
    private static final float SELF_DAMAGE_VULNERABILITY = 1.25f;
    private static final Color TEXT_COLOR = new Color(200, 200, 200, 200);
    private static final Color ARC_COLOR_CORE = new Color(35, 105, 155, 255);
    private static final Color ARC_COLOR_FRINGE = new Color(255, 255, 255, 255);

    // Visual effect intervals
    private static final float VISUAL_ARC_INTERVAL = 0.5f; // How often to show arcs on affected ships
    private static final float EFFECT_RADIUS_SCALE = 0.4f;

    // Ring FX constants
    private static final float ROTATION_SPEED = 5f; // degrees per second
    private static final float SPRITE_ALIGNMENT_SCALE = 512f / 448f;
    private static final float SPRITE_SIZE = MAX_RANGE * 2f * SPRITE_ALIGNMENT_SCALE;
    private static final Color RING_COLOR = new Color(255, 105, 90, 155);

    // Optimization: cache and update intervals
    private static final float TARGET_UPDATE_INTERVAL = 0.2f; // Only update target list 5 times per second
    private static final float CLEANUP_INTERVAL = 2f;

    // Simplified tracking - only need one map now
    private final Map<ShipAPI, Float> affectedShips = new HashMap<>();
    private final Set<ShipAPI> cachedValidTargets = new HashSet<>();

    private float lastTargetUpdateTime = 0f;
    private float lastCleanupTime = 0f;
    private float lastVisualUpdateTime = 0f;
    private boolean wasOverloaded = false;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        CombatEngineAPI engine = Global.getCombatEngine();

        if (ship == null || engine == null) return;

        float currentTime = engine.getTotalElapsedTime(false);
        boolean isOverloaded = ship.getFluxTracker().isOverloaded();
        boolean systemActive = !isOverloaded && effectLevel > 0f && !ship.isRetreating();

        // Handle overload state change
        if (isOverloaded && !wasOverloaded) {
            cleanupAllEffects(ship, id);
            wasOverloaded = true;
            return;
        }
        wasOverloaded = isOverloaded;

        // Ring FX: drawn once per frame while active, no lifecycle management needed.
        // effectLevel is 0->1 during IN and 1->0 during OUT, so scaling by it gives
        // a natural grow-in / shrink-out animation for free.
        if (systemActive) {
            float angle = engine.getTotalElapsedTime(false) * ROTATION_SPEED;
            float scaledSize = SPRITE_SIZE * effectLevel;
            SpriteAPI ringSprite = Global.getSettings().getSprite("fx", "XLII_jammer_ring_alt");
            MagicRender.singleframe(
                    ringSprite,
                    ship.getLocation(),
                    new Vector2f(scaledSize, scaledSize),
                    angle,
                    RING_COLOR,
                    true
            );
        }

        if (!systemActive) {
            cleanupAllEffects(ship, id);
            return;
        }

        // Periodic cleanup of dead ships
        if (currentTime - lastCleanupTime > CLEANUP_INTERVAL) {
            cleanupDeadShips();
            lastCleanupTime = currentTime;
        }

        // Performance: Only update target list periodically, not every frame
        if (currentTime - lastTargetUpdateTime > TARGET_UPDATE_INTERVAL) {
            updateTargetCache(ship, engine);
            lastTargetUpdateTime = currentTime;
        }

        // Calculate vulnerability multipliers
        float vulnerabilityMult = 1f + ((DAMAGE_VULNERABILITY - 1f) * effectLevel);
        float selfVulnerabilityMult = 1f + ((SELF_DAMAGE_VULNERABILITY - 1f) * effectLevel);

        // Apply self-vulnerability
        applySelfVulnerability(ship, id, selfVulnerabilityMult);

        // Apply effects to cached targets
        applyEnemyEffects(ship, engine, id, vulnerabilityMult, currentTime);
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship != null) {
            cleanupAllEffects(ship, id);
        }
    }

    /**
     * Performance optimization: Cache valid targets and only update periodically
     * Uses squared distance to avoid expensive sqrt calculations
     */
    private void updateTargetCache(ShipAPI ship, CombatEngineAPI engine) {
        cachedValidTargets.clear();
        Vector2f shipLoc = ship.getLocation();
        float maxRangeSq = MAX_RANGE * MAX_RANGE;

        for (ShipAPI target : engine.getShips()) {
            if (!isValidTarget(target, ship)) continue;

            // Manual squared distance calculation to avoid sqrt
            Vector2f targetLoc = target.getLocation();
            float dx = targetLoc.x - shipLoc.x;
            float dy = targetLoc.y - shipLoc.y;
            float distSq = dx * dx + dy * dy;

            if (distSq <= maxRangeSq) {
                cachedValidTargets.add(target);
            }
        }
    }

    private void applySelfVulnerability(ShipAPI ship, String id, float selfVulnerabilityMult) {
        String selfId = id + "_self";

        ship.getMutableStats().getHullDamageTakenMult().modifyMult(selfId, selfVulnerabilityMult);
        ship.getMutableStats().getArmorDamageTakenMult().modifyMult(selfId, selfVulnerabilityMult);
        ship.getMutableStats().getShieldDamageTakenMult().modifyMult(selfId, selfVulnerabilityMult);

        if (ship == Global.getCombatEngine().getPlayerShip()) {
            Global.getCombatEngine().maintainStatusForPlayerShip(
                    KEY_TARGET + "_self",
                    ship.getSystem().getSpecAPI().getIconSpriteName(),
                    "Breach Jammer (Self)",
                    String.format("%.0f%% more damage taken", (selfVulnerabilityMult - 1f) * 100f),
                    true
            );
        }
    }

    private void applyEnemyEffects(ShipAPI ship, CombatEngineAPI engine, String id,
                                   float vulnerabilityMult, float currentTime) {

        // Remove ships no longer in range
        affectedShips.keySet().retainAll(cachedValidTargets);

        // Apply effects to all cached valid targets
        for (ShipAPI target : cachedValidTargets) {
            applyVulnerabilityToTarget(target, id, vulnerabilityMult);

            // Show player status
            if (target == Global.getCombatEngine().getPlayerShip()) {
                Global.getCombatEngine().maintainStatusForPlayerShip(
                        KEY_TARGET,
                        ship.getSystem().getSpecAPI().getIconSpriteName(),
                        ship.getSystem().getDisplayName(),
                        String.format("%.0f%% more damage taken", (vulnerabilityMult - 1f) * 100f),
                        true
                );
            }

            // First time affecting this ship - show notification
            if (!affectedShips.containsKey(target)) {
                affectedShips.put(target, currentTime);
                engine.addFloatingText(target.getLocation(), "System Disruption!",
                        24f, TEXT_COLOR, target, 0.5f, 0.5f);
            }
        }

        // Visual effects - spawn periodically, not every frame
        if (currentTime - lastVisualUpdateTime > VISUAL_ARC_INTERVAL) {
            spawnVisualArcsOnTargets(engine);
            lastVisualUpdateTime = currentTime;
        }
    }

    private boolean isValidTarget(ShipAPI target, ShipAPI ship) {
        return target != null && target != ship &&
                target.getOwner() != ship.getOwner() &&
                !target.isHulk() && !target.isFighter() && !target.isPhased();
    }

    private void applyVulnerabilityToTarget(ShipAPI target, String id, float vulnerabilityMult) {
        target.getMutableStats().getHullDamageTakenMult().modifyMult(id, vulnerabilityMult);
        target.getMutableStats().getArmorDamageTakenMult().modifyMult(id, vulnerabilityMult);
        target.getMutableStats().getShieldDamageTakenMult().modifyMult(id, vulnerabilityMult);
    }

    /**
     * Spawn visual arcs on all affected targets to show they're being debuffed
     * These are purely cosmetic and deal no damage
     */
    private void spawnVisualArcsOnTargets(CombatEngineAPI engine) {
        for (ShipAPI target : affectedShips.keySet()) {
            if (target == null || !target.isAlive()) continue;

            // Spawn a few small arcs on the ship itself to show the debuff
            int numArcs = MathUtils.getRandomNumberInRange(1, 2);
            float radius = target.getCollisionRadius() * EFFECT_RADIUS_SCALE;

            for (int i = 0; i < numArcs; i++) {
                Vector2f point1 = MathUtils.getRandomPointInCircle(target.getLocation(), radius);
                Vector2f point2 = MathUtils.getRandomPointInCircle(target.getLocation(), radius);

                // Pure visual arcs - no damage component
                engine.spawnEmpArcVisual(point1, target, point2, target,
                        10f, ARC_COLOR_FRINGE, ARC_COLOR_CORE);
            }
        }
    }

    private void cleanupAllEffects(ShipAPI ship, String id) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        // Remove vulnerability from all affected ships
        for (ShipAPI target : affectedShips.keySet()) {
            if (target != null) {
                removeVulnerabilityFromTarget(target, id);
            }
        }

        // Remove self vulnerability
        String selfId = id + "_self";
        ship.getMutableStats().getHullDamageTakenMult().unmodify(selfId);
        ship.getMutableStats().getArmorDamageTakenMult().unmodify(selfId);
        ship.getMutableStats().getShieldDamageTakenMult().unmodify(selfId);

        affectedShips.clear();
        cachedValidTargets.clear();
    }

    private void removeVulnerabilityFromTarget(ShipAPI target, String id) {
        if (target == null) return;

        target.getMutableStats().getHullDamageTakenMult().unmodify(id);
        target.getMutableStats().getArmorDamageTakenMult().unmodify(id);
        target.getMutableStats().getShieldDamageTakenMult().unmodify(id);
    }

    private void cleanupDeadShips() {
        // Modern Java collection cleanup - more efficient
        affectedShips.keySet().removeIf(ship ->
                ship == null || ship.isHulk() || !Global.getCombatEngine().isEntityInPlay(ship));

        cachedValidTargets.removeIf(ship ->
                ship == null || ship.isHulk() || !Global.getCombatEngine().isEntityInPlay(ship));
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        return (index == 0 && effectLevel > 0f) ?
                new StatusData("Jamming Defence System!", false) : null;
    }

}