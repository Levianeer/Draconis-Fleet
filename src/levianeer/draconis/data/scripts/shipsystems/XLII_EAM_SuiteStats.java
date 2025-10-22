package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

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

    private RotatingRingPlugin activePlugin = null;

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
            if (activePlugin != null) {
                activePlugin.deactivate();
                activePlugin = null;
            }
            return;
        }
        wasOverloaded = isOverloaded;

        // Manage visual effect plugin
        // Check if plugin expired and needs recreation
        if (activePlugin != null && activePlugin.isExpired()) {
            activePlugin = null;
        }

        if (systemActive) {
            if (activePlugin == null) {
                activePlugin = new RotatingRingPlugin(ship);
                engine.addLayeredRenderingPlugin(activePlugin);
            } else {
                activePlugin.activate(); // Reactivate if it was deactivating
            }
        } else if (activePlugin != null) {
            activePlugin.deactivate();
            // Don't null it out - let it fade out naturally and get cleaned up when expired
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

        if (activePlugin != null) {
            activePlugin.deactivate();
            activePlugin = null;
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

    /**
     * Persistent rendering plugin for the rotating ring effect
     */
    private static class RotatingRingPlugin extends BaseCombatLayeredRenderingPlugin {
        private final ShipAPI ship;
        private SpriteAPI sprite;
        private float angle = 0f;
        private float fadeAlpha = 0f;
        private float scale = 0f;
        private boolean isDeactivating = false;
        private float deactivateTime = 0f;
        private float activateTime = 0f;

        private static final float ROTATION_SPEED = 5f;
        private static final float FADE_IN_TIME = 0.3f;
        private static final float FADE_OUT_TIME = 0.3f;
        private static final float SPRITE_SIZE = MAX_RANGE * 2f - 100; // Scale with effect range (1500 * 2 = 3000)
        private static final Color SPRITE_COLOR = new Color(255, 105, 90);
        private static final float SPRITE_ALPHA = 155f;

        public RotatingRingPlugin(ShipAPI ship) {
            this.ship = ship;
        }

        private SpriteAPI getSprite() {
            if (sprite == null) {
                try {
                    sprite = Global.getSettings().getSprite("fx", "XLII_jammer_ring_alt");
                    sprite.setAdditiveBlend();
                } catch (Exception e) {
                    // Sprite loading failed - silent fail
                }
            }
            return sprite;
        }

        public void activate() {
            isDeactivating = false;
            deactivateTime = 0f;
        }

        public void deactivate() {
            isDeactivating = true;
        }

        @Override
        public void advance(float amount) {
            if (Global.getCombatEngine().isPaused()) return;

            // More efficient angle wrapping
            angle += ROTATION_SPEED * amount;
            if (angle >= 360f) angle -= 360f;

            if (isDeactivating) {
                deactivateTime += amount;
                float progress = deactivateTime / FADE_OUT_TIME;
                fadeAlpha = Math.max(0f, 1f - progress);
                scale = Math.max(0f, 1f - progress);
            } else {
                activateTime += amount;
                float progress = Math.min(1f, activateTime / FADE_IN_TIME);
                fadeAlpha = progress;
                scale = progress;
            }
        }

        @Override
        public void render(CombatEngineLayers layer, ViewportAPI viewport) {
            if (!ship.isAlive() || fadeAlpha <= 0f || scale <= 0f) return;

            SpriteAPI spr = getSprite();
            if (spr == null) return;

            Vector2f loc = ship.getLocation();
            float scaledSize = SPRITE_SIZE * scale;

            // No manual viewport culling - let the engine handle it via getRenderRadius()
            // This prevents the effect from disappearing when partially off-screen
            spr.setSize(scaledSize, scaledSize);
            spr.setAngle(angle);
            spr.setAlphaMult(fadeAlpha * SPRITE_ALPHA);
            spr.setColor(SPRITE_COLOR);
            spr.renderAtCenter(loc.x, loc.y);
        }

        @Override
        public boolean isExpired() {
            return !ship.isAlive() || (isDeactivating && fadeAlpha <= 0f);
        }

        @Override
        public EnumSet<CombatEngineLayers> getActiveLayers() {
            return EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_LAYER);
        }

        @Override
        public float getRenderRadius() {
            return SPRITE_SIZE * 0.5f * scale;
        }
    }
}