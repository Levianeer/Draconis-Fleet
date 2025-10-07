package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import java.awt.Color;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static com.fs.starfarer.api.impl.combat.EntropyAmplifierStats.KEY_TARGET;

public class XLII_EAM_SuiteStats extends BaseShipSystemScript {

    private static final float MAX_RANGE = 1500f;
    private static final float DAMAGE_VULNERABILITY = 1.25f;
    private static final float SELF_DAMAGE_VULNERABILITY = 1.25f;
    private static final Color TEXT_COLOR = new Color(200, 200, 200, 200);
    private static final Color ARC_COLOR_CORE = new Color(35, 105, 155, 255);
    private static final Color ARC_COLOR_FRINGE = new Color(255, 255, 255, 255);

    private static final float EFFECT_COOLDOWN = 1.0f;
    private static final float CLEANUP_INTERVAL = 2f;
    private static final float EFFECT_RADIUS_SCALE = 0.4f;

    // EMP Arc constants
    private static final float EMP_ARC_INTERVAL = 1f;
    private static final float BASE_EMP_DAMAGE = 100f;
    private static final float MAX_EMP_DAMAGE = 2000f;
    private static final float EMP_RAMP_TIME = 9.0f;

    private final Map<ShipAPI, Float> affectedShips = new HashMap<>();
    private final Map<ShipAPI, Float> lastStatusUpdate = new HashMap<>();
    private final Map<ShipAPI, Float> lastEmpArcTime = new HashMap<>();

    private float lastCleanupTime = 0f;
    private boolean wasOverloaded = false;

    // Plugin for persistent rendering (replaces all the manual sprite code)
    private RotatingRingPlugin activePlugin = null;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        CombatEngineAPI engine = Global.getCombatEngine();

        if (ship == null || ship.getLocation() == null || engine == null) return;

        float currentTime = engine.getTotalElapsedTime(false);
        boolean isOverloaded = ship.getFluxTracker().isOverloaded();
        boolean systemActive = !isOverloaded && effectLevel > 0f && !ship.isRetreating();

        // Handle overload state change
        if (isOverloaded && !wasOverloaded) {
            cleanupAllEffects(ship, id);
            wasOverloaded = true;

            // Deactivate visual effect when overloaded
            if (activePlugin != null) {
                activePlugin.deactivate();
                activePlugin = null;
            }
            return;
        }
        wasOverloaded = isOverloaded;

        // Manage visual effect plugin (replaces handleToggleSpriteRendering)
        if (systemActive && activePlugin == null) {
            activePlugin = new RotatingRingPlugin(ship);
            engine.addLayeredRenderingPlugin(activePlugin);
        } else if (!systemActive && activePlugin != null) {
            activePlugin.deactivate();
            activePlugin = null;
        }

        if (!systemActive) {
            cleanupAllEffects(ship, id);
            return;
        }

        // Periodic cleanup
        if (currentTime - lastCleanupTime > CLEANUP_INTERVAL) {
            cleanupDeadShips();
            lastCleanupTime = currentTime;
        }

        float vulnerabilityMult = 1f + ((DAMAGE_VULNERABILITY - 1f) * effectLevel);
        float selfVulnerabilityMult = 1f + ((SELF_DAMAGE_VULNERABILITY - 1f) * effectLevel);

        // Apply self-vulnerability
        applySelfVulnerability(ship, id, selfVulnerabilityMult);

        // Apply enemy vulnerability and EMP effects
        applyEnemyEffects(ship, engine, id, vulnerabilityMult, currentTime, effectLevel);
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship != null) {
            cleanupAllEffects(ship, id);
        }

        // Clean up visual effect plugin
        if (activePlugin != null) {
            activePlugin.deactivate();
            activePlugin = null;
        }
    }

    private void applySelfVulnerability(ShipAPI ship, String id, float selfVulnerabilityMult) {
        String selfId = id + "_self";

        ship.getMutableStats().getHullDamageTakenMult().modifyMult(selfId, selfVulnerabilityMult);
        ship.getMutableStats().getArmorDamageTakenMult().modifyMult(selfId, selfVulnerabilityMult);
        ship.getMutableStats().getShieldDamageTakenMult().modifyMult(selfId, selfVulnerabilityMult);

        if (ship == Global.getCombatEngine().getPlayerShip()) {
            String vulnerabilityText = String.format("%.0f%% more damage taken",
                    (selfVulnerabilityMult - 1f) * 100f);

            Global.getCombatEngine().maintainStatusForPlayerShip(
                    KEY_TARGET + "_self",
                    ship.getSystem().getSpecAPI().getIconSpriteName(),
                    "Breach Jammer (Self)",
                    vulnerabilityText,
                    true
            );
        }
    }

    private void applyEnemyEffects(ShipAPI ship, CombatEngineAPI engine, String id,
                                   float vulnerabilityMult, float currentTime, float effectLevel) {

        for (ShipAPI target : engine.getShips()) {
            if (!isValidTarget(target, ship)) continue;

            float distance = Misc.getDistance(ship.getLocation(), target.getLocation());

            if (distance > MAX_RANGE) {
                removeTargetFromSystem(target, id);
                continue;
            }

            applyVulnerabilityToTarget(target, id, vulnerabilityMult);

            if (target == Global.getCombatEngine().getPlayerShip()) {
                showPlayerStatus(ship, vulnerabilityMult);
            }

            handleTargetEffects(target, ship, engine, currentTime, effectLevel);
        }
    }

    private boolean isValidTarget(ShipAPI target, ShipAPI ship) {
        return target != null && target != ship &&
                target.getOwner() != ship.getOwner() &&
                !target.isHulk() && !target.isFighter() && !target.isPhased();
    }

    private void removeTargetFromSystem(ShipAPI target, String id) {
        if (affectedShips.containsKey(target)) {
            removeVulnerabilityFromTarget(target, id);
            affectedShips.remove(target);
            lastEmpArcTime.remove(target);
        }
    }

    private void applyVulnerabilityToTarget(ShipAPI target, String id, float vulnerabilityMult) {
        target.getMutableStats().getHullDamageTakenMult().modifyMult(id, vulnerabilityMult);
        target.getMutableStats().getArmorDamageTakenMult().modifyMult(id, vulnerabilityMult);
        target.getMutableStats().getShieldDamageTakenMult().modifyMult(id, vulnerabilityMult);
    }

    private void showPlayerStatus(ShipAPI ship, float vulnerabilityMult) {
        String vulnerabilityText = String.format("%.0f%% more damage taken",
                (vulnerabilityMult - 1f) * 100f);

        Global.getCombatEngine().maintainStatusForPlayerShip(
                KEY_TARGET,
                ship.getSystem().getSpecAPI().getIconSpriteName(),
                ship.getSystem().getDisplayName(),
                vulnerabilityText,
                true
        );
    }

    private void handleTargetEffects(ShipAPI target, ShipAPI source, CombatEngineAPI engine,
                                     float currentTime, float effectLevel) {
        if (!affectedShips.containsKey(target)) {
            affectedShips.put(target, currentTime);
            lastEmpArcTime.put(target, currentTime - EMP_ARC_INTERVAL);
            engine.addFloatingText(target.getLocation(), "System Disruption!",
                    24f, TEXT_COLOR, target, 0.5f, 0.5f);
            spawnVulnerabilityEffect(engine, target);
        } else {
            float lastTriggered = affectedShips.get(target);
            if (currentTime - lastTriggered >= EFFECT_COOLDOWN) {
                affectedShips.put(target, currentTime);
                spawnVulnerabilityEffect(engine, target);
            }
        }

        handleEmpArcEffects(target, source, engine, currentTime, effectLevel);
    }

    private void handleEmpArcEffects(ShipAPI target, ShipAPI source, CombatEngineAPI engine,
                                     float currentTime, float effectLevel) {
        Float lastEmpTime = lastEmpArcTime.get(target);
        if (lastEmpTime == null) {
            lastEmpTime = currentTime - EMP_ARC_INTERVAL;
        }

        if (currentTime - lastEmpTime >= EMP_ARC_INTERVAL) {
            Float firstAffectedTime = affectedShips.get(target);
            if (firstAffectedTime != null) {
                float timeInEffect = currentTime - firstAffectedTime;

                float empDamageScale = Math.min(timeInEffect / EMP_RAMP_TIME, 1.0f);
                float empDamage = BASE_EMP_DAMAGE + (MAX_EMP_DAMAGE - BASE_EMP_DAMAGE) * empDamageScale;

                empDamage *= effectLevel;

                engine.spawnEmpArcPierceShields(
                        source,
                        source.getLocation(),
                        target,
                        target,
                        DamageType.ENERGY,
                        100f,
                        empDamage,
                        10000f,
                        "EMP Disruptor",
                        15f,
                        ARC_COLOR_FRINGE,
                        ARC_COLOR_CORE
                );

                lastEmpArcTime.put(target, currentTime);

                if (timeInEffect > 1.0f) {
                    String empText = String.format("EMP: %.0f", empDamage);
                    engine.addFloatingText(target.getLocation(), empText,
                            16f, ARC_COLOR_FRINGE, target, 0.3f, 0.3f);
                }
            }
        }
    }

    private void cleanupAllEffects(ShipAPI ship, String id) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        for (ShipAPI s : engine.getShips()) {
            if (s != null && !s.isHulk()) {
                removeVulnerabilityFromTarget(s, id);
            }
        }

        String selfId = id + "_self";
        ship.getMutableStats().getHullDamageTakenMult().unmodify(selfId);
        ship.getMutableStats().getArmorDamageTakenMult().unmodify(selfId);
        ship.getMutableStats().getShieldDamageTakenMult().unmodify(selfId);

        affectedShips.clear();
        lastStatusUpdate.clear();
        lastEmpArcTime.clear();
    }

    private void removeVulnerabilityFromTarget(ShipAPI target, String id) {
        if (target == null) return;

        target.getMutableStats().getHullDamageTakenMult().unmodify(id);
        target.getMutableStats().getArmorDamageTakenMult().unmodify(id);
        target.getMutableStats().getShieldDamageTakenMult().unmodify(id);
    }

    private void cleanupDeadShips() {
        CombatEngineAPI engine = Global.getCombatEngine();

        removeDeadShipsFromMap(affectedShips, engine);
        removeDeadShipsFromMap(lastStatusUpdate, engine);
        removeDeadShipsFromMap(lastEmpArcTime, engine);
    }

    private void removeDeadShipsFromMap(Map<ShipAPI, Float> map, CombatEngineAPI engine) {
        Iterator<Map.Entry<ShipAPI, Float>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            ShipAPI ship = it.next().getKey();
            if (ship == null || ship.isHulk() || !engine.isEntityInPlay(ship)) {
                it.remove();
            }
        }
    }

    private void spawnVulnerabilityEffect(CombatEngineAPI engine, ShipAPI target) {
        if (target == null || target.getLocation() == null) return;

        int numEffects = MathUtils.getRandomNumberInRange(2, 4);
        float radius = target.getCollisionRadius() * EFFECT_RADIUS_SCALE;

        for (int i = 0; i < numEffects; i++) {
            Vector2f point1 = MathUtils.getRandomPointInCircle(target.getLocation(), radius);
            Vector2f point2 = MathUtils.getRandomPointInCircle(target.getLocation(), radius);

            engine.spawnEmpArcVisual(point1, target, point2, target, 15f, ARC_COLOR_FRINGE, ARC_COLOR_CORE);
        }
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        return (index == 0 && effectLevel > 0f) ?
                new StatusData("Jamming Defence System!", false) : null;
    }

    // ========================================================================
    // Persistent rendering plugin for the rotating ring effect
    // This replaces all the manual sprite rendering code
    // ========================================================================

    private static class RotatingRingPlugin extends BaseCombatLayeredRenderingPlugin {
        private final ShipAPI ship;
        private SpriteAPI sprite;
        private float angle = 0f;
        private float fadeAlpha = 0f;
        private boolean isDeactivating = false;
        private float deactivateTime = 0f;

        // Rendering constants
        private static final float ROTATION_SPEED = 5f;
        private static final float FADE_IN_TIME = 0.2f;
        private static final float FADE_OUT_TIME = 0.2f;
        private static final float SPRITE_SIZE = 3050f; // MAX_RANGE * 2 + 550
        private static final Color SPRITE_COLOR = new Color(255, 105, 90);
        private static final float SPRITE_ALPHA = 155f;

        public RotatingRingPlugin(ShipAPI ship) {
            this.ship = ship;
        }

        // Lazy load the sprite (only once)
        private SpriteAPI getSprite() {
            if (sprite == null) {
                try {
                    sprite = Global.getSettings().getSprite("fx", "XLII_jammer_ring_alt");
                    sprite.setSize(SPRITE_SIZE, SPRITE_SIZE);
                    sprite.setAdditiveBlend();
                } catch (Exception e) {
                    // Sprite loading failed - will return null
                }
            }
            return sprite;
        }

        // Called from ship system to trigger fade-out
        public void deactivate() {
            isDeactivating = true;
        }

        // Called automatically by engine every frame
        @Override
        public void advance(float amount) {
            if (Global.getCombatEngine().isPaused()) return;

            // Rotate continuously
            angle += ROTATION_SPEED * amount;
            angle = angle % 360f; // Keep within 0-360

            // Handle fade in/out
            if (isDeactivating) {
                deactivateTime += amount;
                fadeAlpha = Math.max(0f, 1f - (deactivateTime / FADE_OUT_TIME));
            } else {
                // Fade in when activating
                fadeAlpha = Math.min(1f, fadeAlpha + (amount / FADE_IN_TIME));
            }
        }

        // Called automatically by engine to render
        @Override
        public void render(CombatEngineLayers layer, ViewportAPI viewport) {
            if (!ship.isAlive() || fadeAlpha <= 0f) return;

            SpriteAPI spr = getSprite();
            if (spr == null) return;

            Vector2f loc = ship.getLocation();

            // CRITICAL: Viewport culling for performance
            // Don't render if ship is off-screen
            if (!viewport.isNearViewport(loc, SPRITE_SIZE / 2f)) return;

            // Set sprite properties and render
            spr.setAngle(angle);
            spr.setAlphaMult(fadeAlpha * SPRITE_ALPHA);
            spr.setColor(SPRITE_COLOR);
            spr.renderAtCenter(loc.x, loc.y);
        }

        // Tell engine when to remove this plugin
        @Override
        public boolean isExpired() {
            // Remove when ship dies OR fade-out completes
            return !ship.isAlive() || (isDeactivating && fadeAlpha <= 0f);
        }

        // Which render layer to use
        @Override
        public EnumSet<CombatEngineLayers> getActiveLayers() {
            return EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_LAYER);
        }

        // Helps engine optimize rendering
        @Override
        public float getRenderRadius() {
            return SPRITE_SIZE / 2f;
        }
    }
}