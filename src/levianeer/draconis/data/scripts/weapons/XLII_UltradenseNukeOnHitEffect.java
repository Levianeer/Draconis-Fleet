package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.EmpArcEntityAPI.EmpArcParams;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.opengl.GL14;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicLensFlare;
import org.magiclib.util.MagicRender;

import java.awt.*;
import java.util.*;
import java.util.List;

public class XLII_UltradenseNukeOnHitEffect implements OnHitEffectPlugin {

    // Colors
    private static final Color FLASH_CORE     = new Color(255, 255, 255, 225);
    private static final Color FLASH_FRINGE    = new Color(255, 220, 100, 255);
    private static final Color PARTICLE_INNER  = new Color(255, 200, 80,  255);
    private static final Color PARTICLE_OUTER  = new Color(255, 100, 20,  200);

    private static final Color FRAG_FRINGE = new Color(185, 128, 110, 255);
    private static final Color FRAG_MID    = new Color(255,  75,  35, 220);
    private static final Color FRAG_CORE   = new Color(255, 188, 137, 235);

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target,
                      Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult,
                      CombatEngineAPI engine) {

        if (target != null && !(target instanceof ShipAPI)) return;

        ShipAPI source = projectile.getSource();
        float damage   = projectile.getDamageAmount();
        float startAngle = MathUtils.getRandomNumberInRange(0f, 360f);

        // Damaging Explosion
        engine.spawnDamagingExplosion(createExplosionSpec(), source, point);

        // Visual Explosion
        engine.spawnDamagingExplosion(createVisualSpec(), source, point);

        // Dark smoke aftermath
        MagicRender.battlespace(
                Global.getSettings().getSprite("graphics/fx/explosion3.png"),
                new Vector2f(point), new Vector2f(0f, 0f),
                new Vector2f(500f * SCALE, 500f * SCALE), new Vector2f(50f * SCALE, 50f * SCALE),
                startAngle, 8f, new Color(40, 35, 30, 180), false,
                0f, 0f, 0f, 0f, 0f,
                1.2f, 2.0f, 3.0f,
                CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);

        // Multi-phase custom visual plugin
        CombatEntityAPI entity = engine.addLayeredRenderingPlugin(new SuperNukePlugin(point, engine));
        entity.getLocation().set(point);

        // Shockwave ring: delayed (0.3s), medium
        engine.addPlugin(new ShockwaveDelayPlugin(point));

        // Lens flares
        spawnLensFlares(engine, source, point);

        // Explosion flash
        float fragAngle = 360f * (float) Math.random();
        MagicRender.battlespace(
                Global.getSettings().getSprite("fx", "XLII_explosion"),
                point,
                new Vector2f(),
                new Vector2f(640 * SCALE, 640 * SCALE),
                new Vector2f(400 * SCALE, 400 * SCALE),
                fragAngle, 0, FRAG_FRINGE, true,
                0f, 0.1f, 0.3f);
        MagicRender.battlespace(
                Global.getSettings().getSprite("fx", "XLII_explosion"),
                point,
                new Vector2f(),
                new Vector2f(320 * SCALE, 320 * SCALE),
                new Vector2f(200 * SCALE, 200 * SCALE),
                fragAngle, 0, FRAG_MID, true,
                0.2f, 0.1f, 0.5f);
        MagicRender.battlespace(
                Global.getSettings().getSprite("fx", "XLII_explosion"),
                point, new Vector2f(),
                new Vector2f(960 * SCALE, 960 * SCALE), new Vector2f(100 * SCALE, 100 * SCALE),
                fragAngle, 0, FRAG_CORE, true,
                0.4f, 0.1f, 1.0f);
        engine.addHitParticle(point, new Vector2f(), 125 * SCALE, 0.1f, 1f, FRAG_FRINGE);
        engine.addSmoothParticle(point, new Vector2f(), 175 * SCALE, 2f, 0.25f, Color.white);
        engine.addSmoothParticle(point, new Vector2f(), 250 * SCALE, 2f, 0.1f, Color.white);

        // Shield pierce + DoT to every ship in the blast radius
        Map<ShipAPI, Float> tracker = applyAreaEffects(projectile, (ShipAPI) target, point, shieldHit, engine, source, damage);
        engine.addPlugin(new RadiationLingerPlugin(point, projectile, damage, tracker));
    }

    private static final float SCALE                        = 1.75f;
    private static final float BLAST_RADIUS                = 600f  * SCALE;
    private static final float SHIELD_PIERCE_FRACTION      = 0.25f;
    private static final float PIERCE_ARC_WIDTH_PRIMARY    = 25f   * SCALE;
    private static final float PIERCE_ARC_WIDTH_SECONDARY  = 12f   * SCALE;

    /**
     * Walks inward from the ship's facing edge toward the blast point and returns
     * the first world-space position that maps to a valid armor grid cell.
     * Falls back to ship center if no cell is found (fighters, edge cases).
     */
    private static Vector2f getFacingHitPoint(ShipAPI ship, Vector2f blastPoint) {
        ArmorGridAPI grid = ship.getArmorGrid();
        Vector2f dir = Vector2f.sub(blastPoint, ship.getLocation(), new Vector2f());
        float len = dir.length();
        if (len < 0.01f) return new Vector2f(ship.getLocation());
        dir.scale(1f / len);

        float radius = ship.getCollisionRadius();
        float step = grid.getCellSize();
        for (float dist = radius; dist > -radius; dist -= step) {
            Vector2f candidate = new Vector2f(
                    ship.getLocation().x + dir.x * dist,
                    ship.getLocation().y + dir.y * dist
            );
            if (grid.getCellAtLocation(candidate) != null) return candidate;
        }
        return new Vector2f(ship.getLocation());
    }

    /**
     * Returns a map used by RadiationLingerPlugin:
     *   value < 0  -> DoT already applied, skip in linger
     *   value > 0  -> shield-blocked; this is the intended dotDamage to apply if overloaded
     */
    private static Map<ShipAPI, Float> applyAreaEffects(DamagingProjectileAPI projectile, ShipAPI primaryTarget,
                                                         Vector2f point, boolean primaryShieldHit,
                                                         CombatEngineAPI engine, ShipAPI source, float damage) {
        Map<ShipAPI, Float> tracker = new HashMap<>();
        for (ShipAPI ship : CombatUtils.getShipsWithinRange(point, BLAST_RADIUS)) {
            if (!ship.isAlive()) continue;

            boolean isPrimary = (ship == primaryTarget);
            boolean isShieldHit;
            float scaledDamage;
            float dotDamage;

            if (isPrimary) {
                isShieldHit  = primaryShieldHit;
                scaledDamage = damage;
                dotDamage    = damage;
            } else {
                isShieldHit  = ship.getShield() != null
                        && ship.getShield().isOn()
                        && ship.getShield().isWithinArc(point);
                float dist   = Misc.getDistance(point, ship.getLocation());
                float distFraction = (float) Math.sqrt(Math.max(0f, 1f - dist / BLAST_RADIUS));
                scaledDamage = damage * distFraction;
                dotDamage    = damage * distFraction;
            }

            if (scaledDamage <= 0f) continue;

            // Hit point on the facing side of the ship - primary uses the actual impact point,
            // secondaries use the armor cell closest to the blast on the exposed face.
            Vector2f hitPoint = isPrimary ? point : getFacingHitPoint(ship, point);

            // Main blast damage - primary already took this from the projectile hit.
            if (!isPrimary) {
                engine.applyDamage(ship, hitPoint, scaledDamage, DamageType.ENERGY,
                        scaledDamage, false, false, source, false);
            }

            // Shield pierce
            if (isShieldHit) {
                ship.setHitpoints(Math.max(0f, ship.getHitpoints() - scaledDamage * SHIELD_PIERCE_FRACTION));
                engine.spawnEmpArcPierceShields(source, point, ship, ship,
                        DamageType.ENERGY, 0f, 0f, 1200f * SCALE, "system_emp_emitter_impact",
                        isPrimary ? PIERCE_ARC_WIDTH_PRIMARY : PIERCE_ARC_WIDTH_SECONDARY,
                        new Color(255, 100, 255), new Color(255, 255, 255));
            }

            // Radiation DoT - anchored at the facing hit point.
            // If the shield is blocking, deal the radiation damage to it all at once instead.
            if (isShieldHit) {
                float flux = dotDamage * ship.getShield().getFluxPerPointOfDamage();
                ship.getFluxTracker().increaseFlux(flux, true);
                if (Misc.shouldShowDamageFloaty(projectile.getSource(), ship)) {
                    engine.addFloatingDamageText(hitPoint, dotDamage, 0f, Misc.FLOATY_SHIELD_DAMAGE_COLOR, ship, source);
                }
                tracker.put(ship, dotDamage); // positive = shield-blocked, intended damage stored
            } else {
                Vector2f offset = Vector2f.sub(hitPoint, ship.getLocation(), new Vector2f());
                offset = Misc.rotateAroundOrigin(offset, -ship.getFacing());
                RadiationEffect radiation = new RadiationEffect(projectile, ship, offset, dotDamage);
                CombatEntityAPI re = engine.addLayeredRenderingPlugin(radiation);
                re.getLocation().set(hitPoint);
                tracker.put(ship, -1f); // negative = DoT already applied
            }
        }
        return tracker;
    }

    // -------------------------------------------------------------------------

    private static DamagingExplosionSpec createExplosionSpec() {
        DamagingExplosionSpec spec = new DamagingExplosionSpec(
                0.15f, 2000f * SCALE, 1400f * SCALE,
                0f, 0f,
                CollisionClass.NONE, CollisionClass.NONE,
                SCALE, 5f * SCALE, 0.1f, 5,
                PARTICLE_INNER, PARTICLE_OUTER
        );
        spec.setDamageType(DamageType.ENERGY);
        spec.setSoundSetId("XLII_halberd_explosion");
        return spec;
    }

    private static DamagingExplosionSpec createVisualSpec() {
        DamagingExplosionSpec spec = new DamagingExplosionSpec(
                0.6f, 2200f * SCALE, 1600f * SCALE,
                0f, 0f,
                CollisionClass.NONE, CollisionClass.NONE,
                8f * SCALE, 12f * SCALE, 1.0f, 300,
                PARTICLE_INNER, PARTICLE_OUTER
        );
        spec.setUseDetailedExplosion(true);
        spec.setDetailedExplosionFlashDuration(1.2f);
        spec.setDetailedExplosionRadius(900f * SCALE);
        spec.setDetailedExplosionFlashRadius(1500f * SCALE);
        spec.setDetailedExplosionFlashColorCore(FLASH_CORE);
        spec.setDetailedExplosionFlashColorFringe(FLASH_FRINGE);
        spec.setDamageType(DamageType.ENERGY);
        spec.setSoundSetId(null);
        return spec;
    }

    private static void spawnShockwave(Vector2f point) {
        RippleDistortion ripple = new RippleDistortion(new Vector2f(point), new Vector2f(0f, 0f));
        ripple.setSize((float) 2100.0);
        ripple.setIntensity((float) 175.0);
        ripple.setFrameRate(60f);
        ripple.fadeInSize((float) 0.4);
        ripple.fadeOutIntensity((float) 0.8);
        ripple.setSize((float) 525.0);
        DistortionShader.addDistortion(ripple);
    }

    private static void spawnLensFlares(CombatEngineAPI engine, ShipAPI source, Vector2f center) {
        for (int i = 0; i < 10; i++) {
            Vector2f pos = MathUtils.getRandomPointInCircle(center, 350f * SCALE);
            float angle = (float) Math.random() * 360f;
            MagicLensFlare.createSharpFlare(engine, source, pos, 250f * SCALE, 100f * SCALE, angle,
                    new Color(120, 120, 255), new Color(255, 255, 255));
        }
    }

    // =========================================================================
    // Multi-phase visual rendering plugin
    // =========================================================================

    static class SuperNukePlugin extends BaseCombatLayeredRenderingPlugin {

        private static final float TOTAL_DURATION = 9.0f;

        // Phase timing
        private static final float FLASH_END  = 0.4f;
        private static final float EXPAND_END = 1.7f;
        private static final float LINGER_END = 3.5f;

        // Disc layer diameters at max expansion
        private static final float HALO_DIAM  = 1200f * SCALE;
        private static final float BODY_DIAM  = 800f  * SCALE;
        private static final float CORE_DIAM  = 350f  * SCALE;
        private static final float MAX_RADIUS = 600f  * SCALE;

        // Disc cuts out between these times; smoke burst fires at DISC_FADE_START
        private static final float DISC_FADE_START = 1.0f;
        private static final float DISC_FADE_END   = 1.5f;

        // Layer colors (additive blending)
        private static final Color CORE_COLOR = new Color(255, 255, 200, 240);
        private static final Color BODY_COLOR = new Color(255, 160,  40, 220);
        private static final Color HALO_COLOR = new Color(255, 200,  80, 140);

        private final Vector2f center;
        private final CombatEngineAPI engine;
        private float elapsed = 0f;

        private final IntervalUtil empInterval   = new IntervalUtil(0.25f, 0.65f);

        private final SpriteAPI discSprite;
        private boolean smokeFired  = false;
        private float currentRadius = 0f;
        private float currentAlpha  = 0f;

        private static final EnumSet<CombatEngineLayers> LAYERS =
                EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);

        SuperNukePlugin(Vector2f center, CombatEngineAPI engine) {
            this.center = new Vector2f(center);
            this.engine = engine;
            discSprite = Global.getSettings().getSprite("fx", "XLII_explosion");
        }

        @Override
        public void advance(float amount) {
            if (engine.isPaused()) return;

            elapsed += amount;

            if (elapsed < FLASH_END) {
                currentRadius = MAX_RADIUS * 0.3f * (elapsed / FLASH_END);
                currentAlpha  = 1.0f;
            } else if (elapsed < EXPAND_END) {
                float p     = (elapsed - FLASH_END) / (EXPAND_END - FLASH_END);
                float eased = 1f - (1f - p) * (1f - p);
                currentRadius = MAX_RADIUS * (0.3f + 0.7f * eased);
                currentAlpha  = 1.0f;
            } else if (elapsed < LINGER_END) {
                // Slowly shrink from MAX_RADIUS to 70% over the linger + fade window
                float shrinkT = (elapsed - EXPAND_END) / (DISC_FADE_END - EXPAND_END);
                currentRadius = MAX_RADIUS * (1.0f - 0.3f * Math.min(1f, shrinkT));
                currentAlpha  = 1.0f - 0.3f * ((elapsed - EXPAND_END) / (LINGER_END - EXPAND_END));
            } else {
                currentRadius = MAX_RADIUS * 0.7f;
                currentAlpha  = 0.7f * (1.0f - Math.min(1f, (elapsed - LINGER_END) / (TOTAL_DURATION - LINGER_END)));
            }

            if (elapsed >= DISC_FADE_START && !smokeFired) {
                spawnSmokeAftermath();
                smokeFired = true;
            }

            // EMP aftermath arcs: 0.5s -> LINGER_END
            if (elapsed >= 0.5f && elapsed < LINGER_END) {
                empInterval.advance(amount);
                if (empInterval.intervalElapsed()) {
                    spawnAftermathArcs();
                }
            }
        }

        @Override
        public boolean isExpired() {
            return elapsed >= TOTAL_DURATION;
        }

        @Override
        public void render(CombatEngineLayers layer, ViewportAPI viewport) {
            if (elapsed <= 0f) return;

            float x = center.x;
            float y = center.y;
            float rRatio = currentRadius / MAX_RADIUS;

            // Disc fades out between DISC_FADE_START and DISC_FADE_END
            float discFade = elapsed < DISC_FADE_START ? 1.0f
                    : Math.max(0f, 1.0f - (elapsed - DISC_FADE_START) / (DISC_FADE_END - DISC_FADE_START));

            if (discFade > 0f) {
                // Back-to-front: halo -> body -> core
                renderSprite(discSprite, x, y, HALO_DIAM * rRatio, HALO_DIAM * rRatio,
                        HALO_COLOR, currentAlpha * discFade * 0.5f);
                renderSprite(discSprite, x, y, BODY_DIAM * rRatio, BODY_DIAM * rRatio,
                        BODY_COLOR, currentAlpha * discFade * 0.85f);
                renderSprite(discSprite, x, y, CORE_DIAM * rRatio, CORE_DIAM * rRatio,
                        CORE_COLOR, currentAlpha * discFade);
            }

        }

        /** Render a sprite centered at (cx, cy) with additive or normal blend. */
        private static void renderSprite(SpriteAPI sprite, float cx, float cy,
                                         float w, float h,
                                         Color color, float alphaMult) {
            sprite.setSize(w, h);
            sprite.setCenter(w * 0.5f, h * 0.5f);
            sprite.setAngle((float) 0.0);
            sprite.setColor(color);
            sprite.setAlphaMult(alphaMult);
            sprite.setAdditiveBlend();
            sprite.renderAtCenter(cx, cy);
        }

        private void spawnSmokeAftermath() {
            float angle = (float) Math.random() * 360f;
            MagicRender.battlespace(
                    Global.getSettings().getSprite("fx", "XLII_explosion"),
                    new Vector2f(center), new Vector2f(),
                    new Vector2f(800f * SCALE, 800f * SCALE), new Vector2f(60f * SCALE, 60f * SCALE),
                    angle, 5f, FRAG_FRINGE, true,
                    0f, 0f, 0f, 0f, 0f,
                    0.2f, 1.2f, 1.8f,
                    CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);
            MagicRender.battlespace(
                    Global.getSettings().getSprite("fx", "XLII_explosion"),
                    new Vector2f(center), new Vector2f(),
                    new Vector2f(600f * SCALE, 600f * SCALE), new Vector2f(50f * SCALE, 50f * SCALE),
                    angle + 60f, -4f, FRAG_MID, true,
                    0f, 0f, 0f, 0f, 0f,
                    0.3f, 0.8f, 1.5f,
                    CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);
            MagicRender.battlespace(
                    Global.getSettings().getSprite("fx", "XLII_explosion"),
                    new Vector2f(center), new Vector2f(),
                    new Vector2f(450f * SCALE, 450f * SCALE), new Vector2f(40f * SCALE, 40f * SCALE),
                    angle - 40f, 6f, FRAG_CORE, true,
                    0f, 0f, 0f, 0f, 0f,
                    0.4f, 0.5f, 1.2f,
                    CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);
        }

        private void spawnAftermathArcs() {
            int count = 2 + (int) (Math.random() * 3); // 2–4 arcs
            for (int i = 0; i < count; i++) {
                Vector2f from = Misc.getPointWithinRadius(center, 600f * SCALE);
                Vector2f to   = Misc.getPointWithinRadius(center, 800f * SCALE);

                EmpArcParams params = new EmpArcParams();
                params.segmentLengthMult     = 8f;
                params.zigZagReductionFactor = 0.06f;
                params.fadeOutDist           = 80f * SCALE;
                params.minFadeOutMult        = 5f;
                params.flickerRateMult       = 0.4f;

                float thickness = 8f * SCALE + (float) Math.random() * 8f * SCALE;
                EmpArcEntityAPI arc = engine.spawnEmpArcVisual(
                        from, null, to, null,
                        thickness,
                        new Color(100, 180, 255),
                        new Color(255, 255, 255),
                        params
                );
                arc.setFadedOutAtStart(true);
                arc.setSingleFlickerMode(true);
            }
        }

        @Override
        public EnumSet<CombatEngineLayers> getActiveLayers() {
            return LAYERS;
        }

        @Override
        public float getRenderRadius() {
            return 1400f * SCALE;
        }

        @Override
        public void init(CombatEntityAPI entity) {
            super.init(entity);
        }
    }

    // =========================================================================
    // Delayed second shockwave
    // =========================================================================

    static class ShockwaveDelayPlugin implements EveryFrameCombatPlugin {

        private final Vector2f point;
        private float elapsed = 0f;
        private boolean fired = false;

        ShockwaveDelayPlugin(Vector2f point) {
            this.point = new Vector2f(point);
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            if (fired) return;
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || engine.isPaused()) return;
            elapsed += amount;
            if (elapsed >= 0.3f) {
                spawnShockwave(point);
                fired = true;
            }
        }

        @SuppressWarnings({"deprecation", "RedundantSuppression"})
        @Override
        public void init(CombatEngineAPI engine) {}

        @Override
        public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {}

        @Override
        public void renderInWorldCoords(ViewportAPI viewport) {}

        @Override
        public void renderInUICoords(ViewportAPI viewport) {}
    }

    // =========================================================================
    // Delayed radiation scan - catches overloaded and newly in-range ships
    // =========================================================================

    static class RadiationLingerPlugin implements EveryFrameCombatPlugin {

        private static final float LINGER_DELAY = 1.5f;

        private final Vector2f point;
        private final DamagingProjectileAPI proj;
        private final float damage;
        private final Map<ShipAPI, Float> tracker;
        private float elapsed = 0f;
        private boolean fired = false;

        RadiationLingerPlugin(Vector2f point, DamagingProjectileAPI proj,
                              float damage, Map<ShipAPI, Float> tracker) {
            this.point   = new Vector2f(point);
            this.proj    = proj;
            this.damage  = damage;
            this.tracker = tracker;
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            if (fired) return;
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || engine.isPaused()) return;
            elapsed += amount;
            if (elapsed < LINGER_DELAY) return;
            fired = true;

            for (ShipAPI ship : CombatUtils.getShipsWithinRange(point, BLAST_RADIUS)) {
                if (!ship.isAlive()) continue;

                Float val = tracker.get(ship);
                if (val != null && val < 0f) continue; // DoT already applied at blast time

                boolean shielded = ship.getShield() != null
                        && ship.getShield().isOn()
                        && ship.getShield().isWithinArc(point);
                if (shielded) continue;

                float dotDamage;
                if (val != null) {
                    // Was shield-blocked at blast time; use the damage that was calculated then
                    dotDamage = val;
                } else {
                    // New ship that entered range after the blast; distance-scale from current position
                    float dist = Misc.getDistance(point, ship.getLocation());
                    float distFraction = (float) Math.sqrt(Math.max(0f, 1f - dist / BLAST_RADIUS));
                    dotDamage = damage * distFraction;
                }
                if (dotDamage <= 0f) continue;

                Vector2f hitPoint = getFacingHitPoint(ship, point);
                Vector2f offset = Vector2f.sub(hitPoint, ship.getLocation(), new Vector2f());
                offset = Misc.rotateAroundOrigin(offset, -ship.getFacing());
                RadiationEffect radiation = new RadiationEffect(proj, ship, offset, dotDamage);
                CombatEntityAPI re = engine.addLayeredRenderingPlugin(radiation);
                re.getLocation().set(hitPoint);
            }
        }

        @SuppressWarnings({"deprecation", "RedundantSuppression"})
        @Override public void init(CombatEngineAPI engine) {}
        @Override public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {}
        @Override public void renderInWorldCoords(ViewportAPI viewport) {}
        @Override public void renderInUICoords(ViewportAPI viewport) {}
    }

    // =========================================================================
    // Radiation DoT - ported from DisintegratorEffect with missile-based damage
    // =========================================================================

    static class RadiationParticleData {
        SpriteAPI sprite;
        Vector2f offset = new Vector2f();
        Vector2f vel;
        float scale;
        float scaleIncreaseRate;
        float turnDir;
        float angle;
        float maxDur;
        float elapsed   = 0f;
        float baseSize;
        Color color     = new Color(100, 150, 255, 35);
        FaderUtil fader;

        RadiationParticleData(float baseSize, float maxDur, float endSizeMult) {
            sprite = Global.getSettings().getSprite("misc", "nebula_particles");
            float i = Misc.random.nextInt(4);
            float j = Misc.random.nextInt(4);
            sprite.setTexWidth(0.25f);
            sprite.setTexHeight(0.25f);
            sprite.setTexX(i * 0.25f);
            sprite.setTexY(j * 0.25f);
            sprite.setAdditiveBlend();

            angle = (float) Math.random() * 360f;
            this.maxDur = maxDur;
            scaleIncreaseRate = endSizeMult / maxDur;
            if (endSizeMult < 1f) scaleIncreaseRate = -1f * endSizeMult;
            scale = 1f;
            this.baseSize = baseSize;
            turnDir = Math.signum((float) Math.random() - 0.5f) * 20f * (float) Math.random();

            float driftDir = (float) Math.random() * 360f;
            vel = Misc.getUnitVectorAtDegreeAngle(driftDir);
            vel.scale(0.25f * baseSize / maxDur * (1f + (float) Math.random()));

            fader = new FaderUtil(0f, 0.5f, 0.5f);
            fader.forceOut();
            fader.fadeIn();
        }

        void advance(float amount) {
            scale += scaleIncreaseRate * amount;
            offset.x += vel.x * amount;
            offset.y += vel.y * amount;
            angle += turnDir * amount;
            elapsed += amount;
            if (maxDur - elapsed <= fader.getDurationOut() + 0.1f) fader.fadeOut();
            fader.advance(amount);
        }
    }

    static class RadiationEffect extends BaseCombatLayeredRenderingPlugin {

        private static final int   NUM_TICKS = 7;
        private static final float TICK_INTERVAL_MIN = 0.8f;
        private static final float TICK_INTERVAL_MAX = 1.0f;

        private final List<RadiationParticleData> particles = new ArrayList<>();
        private final DamagingProjectileAPI proj;
        private final ShipAPI target;
        private final Vector2f offset;
        private final float totalDamage;
        private int ticks = 0;
        private final IntervalUtil interval = new IntervalUtil(TICK_INTERVAL_MIN, TICK_INTERVAL_MAX);
        private final FaderUtil fader = new FaderUtil(1f, 0.5f, 0.5f);

        private static final EnumSet<CombatEngineLayers> LAYERS =
                EnumSet.of(CombatEngineLayers.BELOW_INDICATORS_LAYER);

        RadiationEffect(DamagingProjectileAPI proj, ShipAPI target, Vector2f offset, float totalDamage) {
            this.proj        = proj;
            this.target      = target;
            this.offset      = offset;
            this.totalDamage = totalDamage;
            interval.forceIntervalElapsed();
        }

        @Override
        public float getRenderRadius() { return 500f * SCALE; }

        @Override
        public EnumSet<CombatEngineLayers> getActiveLayers() { return LAYERS; }

        @Override
        public void init(CombatEntityAPI entity) { super.init(entity); }

        @Override
        public void advance(float amount) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine.isPaused()) return;

            // Track hit position on the target as it moves/rotates
            Vector2f loc = new Vector2f(offset);
            loc = Misc.rotateAroundOrigin(loc, target.getFacing());
            Vector2f.add(target.getLocation(), loc, loc);
            entity.getLocation().set(loc);

            List<RadiationParticleData> remove = new ArrayList<>();
            for (RadiationParticleData p : particles) {
                p.advance(amount);
                if (p.elapsed >= p.maxDur) remove.add(p);
            }
            particles.removeAll(remove);

            float volume = 1f;
            if (ticks >= NUM_TICKS || !target.isAlive() || !engine.isEntityInPlay(target)) {
                fader.fadeOut();
                fader.advance(amount);
                volume = fader.getBrightness();
            }
            Global.getSoundPlayer().playLoop("disintegrator_loop", target, 1f, volume, loc, target.getVelocity());

            interval.advance(amount);
            if (interval.intervalElapsed() && ticks < NUM_TICKS) {
                dealDamage(engine, loc);
                ticks++;
            }
        }

        private void dealDamage(CombatEngineAPI engine, Vector2f point) {
            // Spawn particle burst
            for (int i = 0; i < 3; i++) {
                RadiationParticleData p = new RadiationParticleData(30f, 3f + (float) Math.random() * 2f, 2f);
                p.offset = Misc.getPointWithinRadius(p.offset, 20f);
                particles.add(p);
            }

            ArmorGridAPI grid = target.getArmorGrid();
            int[] cell = grid.getCellAtLocation(point);
            if (cell == null) return;

            int gridWidth  = grid.getGrid().length;
            int gridHeight = grid.getGrid()[0].length;
            float damTypeMult   = DisintegratorEffectHelper.getDamageTypeMult(proj.getSource(), target);
            float damagePerTick = totalDamage / NUM_TICKS;
            float damageDealt   = 0f;
            float hullDamage    = 0f;

            for (int i = -2; i <= 2; i++) {
                for (int j = -2; j <= 2; j++) {
                    if ((i == 2 || i == -2) && (j == 2 || j == -2)) continue;
                    int cx = cell[0] + i;
                    int cy = cell[1] + j;
                    if (cx < 0 || cx >= gridWidth || cy < 0 || cy >= gridHeight) continue;

                    float damMult = (i == 0 && j == 0) || (Math.abs(i) <= 1 && Math.abs(j) <= 1)
                            ? 1f / 15f : 1f / 30f;

                    float armorInCell = grid.getArmorValue(cx, cy);
                    float damage = damagePerTick * damMult * damTypeMult;
                    if (damage > armorInCell) hullDamage += damage - armorInCell;
                    damage = Math.min(damage, armorInCell);
                    if (damage <= 0f) continue;

                    grid.setArmorValue(cx, cy, Math.max(0f, armorInCell - damage));
                    damageDealt += damage;
                }
            }

            if (damageDealt > 0f) {
                if (Misc.shouldShowDamageFloaty(proj.getSource(), target)) {
                    engine.addFloatingDamageText(point, damageDealt, 0f, Misc.FLOATY_ARMOR_DAMAGE_COLOR, target, proj.getSource());
                }
                target.syncWithArmorGridState();
            }

            if (hullDamage > 1f) {
                float hp = target.getHitpoints();
                target.setHitpoints(Math.max(0f, hp - hullDamage));
                if (target.getHitpoints() <= 0f && !target.isHulk()) {
                    target.setSpawnDebris(false);
                    engine.applyDamage(target, point, 100f, DamageType.ENERGY, 0f, true, false, proj.getSource(), false);
                }
                if (Misc.shouldShowDamageFloaty(proj.getSource(), target)) {
                    Vector2f p2 = new Vector2f(point);
                    p2.y += 20f;
                    engine.addFloatingDamageText(p2, hullDamage, 0f, Misc.FLOATY_HULL_DAMAGE_COLOR, target, proj.getSource());
                }
            }
        }

        @Override
        public boolean isExpired() {
            return particles.isEmpty() &&
                    (ticks >= NUM_TICKS || !target.isAlive() || !Global.getCombatEngine().isEntityInPlay(target));
        }

        @Override
        public void render(CombatEngineLayers layer, ViewportAPI viewport) {
            float x = entity.getLocation().x;
            float y = entity.getLocation().y;
            float b = viewport.getAlphaMult();

            GL14.glBlendEquation(GL14.GL_FUNC_REVERSE_SUBTRACT);
            for (RadiationParticleData p : particles) {
                float size = p.baseSize * p.scale;
                p.sprite.setAngle(p.angle);
                p.sprite.setSize(size, size);
                p.sprite.setAlphaMult(b * p.fader.getBrightness());
                p.sprite.setColor(p.color);
                p.sprite.renderAtCenter(x + p.offset.x, y + p.offset.y);
            }
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        }
    }

    /** Holds the damage-type multiplier helper copied from DisintegratorEffect. */
    private static class DisintegratorEffectHelper {
        static float getDamageTypeMult(ShipAPI source, ShipAPI target) {
            if (source == null || target == null) return 1f;
            float mult = target.getMutableStats().getArmorDamageTakenMult().getModifiedValue();
            switch (target.getHullSize()) {
                case CAPITAL_SHIP: mult *= source.getMutableStats().getDamageToCapital().getModifiedValue();    break;
                case CRUISER:      mult *= source.getMutableStats().getDamageToCruisers().getModifiedValue();   break;
                case DESTROYER:    mult *= source.getMutableStats().getDamageToDestroyers().getModifiedValue(); break;
                case FRIGATE:      mult *= source.getMutableStats().getDamageToFrigates().getModifiedValue();   break;
                case FIGHTER:      mult *= source.getMutableStats().getDamageToFighters().getModifiedValue();   break;
            }
            return mult;
        }
    }
}
