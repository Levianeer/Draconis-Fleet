package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.EmpArcEntityAPI.EmpArcParams;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.loading.MuzzleFlashSpec;
import com.fs.starfarer.api.loading.ProjectileWeaponSpecAPI;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * OnFireEffect/EveryFrame effect for the Fragarach that spawns growing ring sprites when fired.
 * Based on the how the 'Domain Phase Lab's Bombardon' ring effect pattern works.
 * <p>
 * This script runs every frame and monitors the weapon state. When the beam fires,
 * it spawns a visual effect with expanding rings and arcs following the projectile.
 * <p>
 * What a mess.
 */
public class XLII_MuzzleFlashEffect implements OnFireEffectPlugin, EveryFrameWeaponEffectPlugin {

    // Configuration parameters - Muzzle Flash
    private static final float BACKWARDS_OFFSET_DISTANCE = 25f; // Distance behind the weapon
    private static final float MUZZLE_FLASH_LENGTH = 50f;
    private static final int PARTICLE_COUNT = 25;
    private static final float SPREAD = 35f; // Degrees of spread for the backwards flash
    private static final float SIZE_MULTIPLIER = 1.0f;

    // Configuration parameters - Lightning Arc Trail
    private static final float ARC_INTERVAL_MIN = 0.2f; // Minimum time between arc spawns (seconds)
    private static final float ARC_INTERVAL_MAX = 0.3f; // Maximum time between arc spawns (seconds)
    private static final float ARC_THICKNESS = 20f;
    private static final float ARC_CORE_WIDTH = 10f;
    private static final Color ARC_COLOR = new Color(120, 110, 185, 255); // Match weapon glow color
    private static final Color ARC_CORE_COLOR = new Color(255, 255, 255, 255);
    private static final float ARC_SPEED = 100000f; // Visual speed of arc animation
    private static final float MIN_ARC_DISTANCE = 100f; // Minimum distance traveled before spawning arc

    // Configuration parameters - Ring Trail Effect
    private static final float RING_INTERVAL_MIN = 0.01f; // Minimum time between ring spawns (seconds)
    private static final float RING_INTERVAL_MAX = 0.015f; // Maximum time between ring spawns (seconds)
    private static final float MIN_RING_DISTANCE = 20f; // Minimum distance traveled before spawning ring group
    private static final float RING_BASE_SIZE = 10f; // Base size of ring sprites
    private static final float RING_SIZE_MULTIPLIER = 1.0f; // Overall size multiplier for ring groups
    private static final float RING_DURATION_MULTIPLIER = 3f; // Duration multiplier for ring visibility
    private static final Color RING_CORE_COLOR = new Color(255, 255, 255, 255);
    private static final Color RING_FRINGE_COLOR = new Color(185, 110, 110, 255); // Matches weapon glow

    // Tracking data for fired projectiles
    protected static class TrackedProjectile {
        public DamagingProjectileAPI projectile;

        // Arc trail tracking
        public Vector2f lastArcPosition;
        public IntervalUtil arcInterval;

        // Ring trail tracking
        public Vector2f lastRingPosition;
        public IntervalUtil ringInterval;

        public TrackedProjectile(DamagingProjectileAPI proj) {
            this.projectile = proj;

            this.lastArcPosition = new Vector2f(proj.getLocation());
            this.arcInterval = new IntervalUtil(ARC_INTERVAL_MIN, ARC_INTERVAL_MAX);
            this.arcInterval.randomize();

            this.lastRingPosition = new Vector2f(proj.getLocation());
            this.ringInterval = new IntervalUtil(RING_INTERVAL_MIN, RING_INTERVAL_MAX);
            this.ringInterval.randomize();
        }
    }

    /**
     * Manages the lifecycle of a single ring particle sprite.
     */
    public static class ParticleData {
        public SpriteAPI sprite;
        public float time = 0f;
        public float angle;
        public float maxDur;
        public float size;
        public float scale = 1f;
        public float targetScale;

        public FaderUtil fader;

        public ParticleData(SpriteAPI sprite, float size, float targetScale, float angle, float maxDur) {
            this.sprite = sprite;
            this.size = size;
            this.targetScale = targetScale;
            this.angle = angle;
            this.maxDur = maxDur;

            fader = new FaderUtil(0f, 0.25f, 0.15f);
            fader.fadeIn();

            sprite.setTexWidth(1f);
            sprite.setTexHeight(1f);
            sprite.setTexX(0f);
            sprite.setTexY(0f);

            sprite.setAdditiveBlend();
        }

        public void advance(float amount) {
            time += amount;
            scale = 1f + ((targetScale - 1f) * (time / maxDur));
            if (time >= maxDur) {
                fader.fadeOut();
            }
            fader.advance(amount);
        }

        public boolean isExpired() {
            return time >= maxDur + 0.15f;
        }
    }

    /**
     * Rendering plugin for a ring group spawned at a fixed world position along a projectile's path.
     * Renders at a static world position since the projectile moves away from the spawn point.
     */
    protected static class ProjectileRingEffectPlugin extends BaseCombatLayeredRenderingPlugin {
        protected List<ParticleData> particles = new ArrayList<>();
        protected Vector2f worldPosition;
        protected float facingAngle;
        protected Color coreColor;
        protected Color fringeColor;

        public ProjectileRingEffectPlugin(Vector2f worldPosition, float facingAngle,
                                          float sizeMultiplier, float durationMultiplier,
                                          Color coreColor, Color fringeColor) {
            this.worldPosition = new Vector2f(worldPosition);
            this.facingAngle = facingAngle;
            this.coreColor = coreColor;
            this.fringeColor = fringeColor;

            SpriteAPI ring1 = Global.getSettings().getSprite("fx", "XLII_sovnya_ring1");
            SpriteAPI ring2 = Global.getSettings().getSprite("fx", "XLII_sovnya_ring2");
            SpriteAPI ring3 = Global.getSettings().getSprite("fx", "XLII_sovnya_ring3");

            float baseSize = RING_BASE_SIZE * sizeMultiplier;

            float duration1 = 0.025f * durationMultiplier;
            float duration2 = 0.0375f * durationMultiplier;
            float duration3 = 0.05f * durationMultiplier;

            particles.add(new ParticleData(ring1, baseSize, 3f, 0f, duration1));
            particles.add(new ParticleData(ring2, baseSize, 4.333f, 0f, duration2));
            particles.add(new ParticleData(ring3, baseSize, 5.667f, 0f, duration3));
        }

        @Override
        public void advance(float amount) {
            if (Global.getCombatEngine().isPaused()) return;

            List<ParticleData> toRemove = new ArrayList<>();
            for (ParticleData p : particles) {
                p.advance(amount);
                if (p.isExpired()) {
                    toRemove.add(p);
                }
            }
            particles.removeAll(toRemove);
        }

        @Override
        public boolean isExpired() {
            return particles.isEmpty();
        }

        @Override
        public void render(CombatEngineLayers layer, ViewportAPI viewport) {
            float x = worldPosition.x;
            float y = worldPosition.y;

            for (ParticleData p : particles) {
                p.sprite.setAngle(p.angle + facingAngle - 90f);

                float currentSize = p.size * p.scale;
                float alpha = p.fader.getBrightness();

                // Layer 1: Core color (base layer)
                p.sprite.setColor(coreColor);
                p.sprite.setSize(currentSize, currentSize);
                p.sprite.setCenter(currentSize * 0.5f, currentSize * 0.5f);
                p.sprite.setAlphaMult(alpha);
                p.sprite.renderAtCenter(x, y);

                // Layer 2: Fringe color (glow layer, slightly larger)
                float glowSize = currentSize * 1.05f;
                p.sprite.setColor(fringeColor);
                p.sprite.setSize(glowSize, glowSize);
                p.sprite.setCenter(glowSize * 0.5f, glowSize * 0.5f);
                p.sprite.setAlphaMult(alpha * 0.8f);
                p.sprite.renderAtCenter(x, y);
            }
        }

        @Override
        public float getRenderRadius() {
            return 400f;
        }

        protected EnumSet<CombatEngineLayers> layers = EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);

        @Override
        public EnumSet<CombatEngineLayers> getActiveLayers() {
            return layers;
        }

        @Override
        public void init(CombatEntityAPI entity) {
            super.init(entity);
        }
    }

    protected List<TrackedProjectile> trackedProjectiles = new ArrayList<>();

    public XLII_MuzzleFlashEffect() {
    }

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        ShipAPI ship = projectile.getSource();
        if (ship == null) return;

        // Get the weapon's specifications
        ProjectileWeaponSpecAPI weaponSpec = (ProjectileWeaponSpecAPI) weapon.getSpec();
        MuzzleFlashSpec originalSpec = weaponSpec.getMuzzleFlashSpec();
        if (originalSpec == null) return;

        // Create a modified spec for the backwards flash
        MuzzleFlashSpec backwardsSpec = originalSpec.clone();
        backwardsSpec.setLength(MUZZLE_FLASH_LENGTH);
        backwardsSpec.setParticleCount(PARTICLE_COUNT);
        backwardsSpec.setSpread(SPREAD);

        // Get weapon position and angle
        Vector2f weaponLocation = weapon.getLocation();
        float weaponAngle = weapon.getCurrAngle();

        // Calculate backwards position
        Vector2f backwardsOffset = Misc.getUnitVectorAtDegreeAngle(weaponAngle + 180f);
        backwardsOffset.scale(BACKWARDS_OFFSET_DISTANCE);
        Vector2f backwardsPosition = Vector2f.add(weaponLocation, backwardsOffset, new Vector2f());

        // Spawn the backwards muzzle flash
        spawnMuzzleFlash(
                backwardsSpec,
                backwardsPosition,
                weaponAngle + 180f, // Point backwards
                ship.getVelocity(),
                1.5f, // Velocity multiplier
                15f   // Additional velocity
        );

        // Track projectile for lightning arc trail effect
        trackedProjectiles.add(new TrackedProjectile(projectile));
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        // Skip during fast time advance (e.g., during campaign tactical pause)
        if (engine != null && engine.isInFastTimeAdvance()) {
            return;
        }

        List<TrackedProjectile> toRemove = new ArrayList<>();

        for (TrackedProjectile tracked : trackedProjectiles) {
            DamagingProjectileAPI proj = tracked.projectile;

            // Check if projectile should be removed from tracking
            if (proj == null || proj.didDamage() || proj.isFading() || !Objects.requireNonNull(engine).isEntityInPlay(proj)) {
                // Spawn final arc to ensure complete coverage before removing
                spawnFinalArc(engine, weapon, tracked, proj);
                toRemove.add(tracked);
                continue;
            }

            Vector2f currentPos = new Vector2f(proj.getLocation());

            // --- Arc trail ---
            tracked.arcInterval.advance(amount);
            if (tracked.arcInterval.intervalElapsed()) {
                float arcDistance = Misc.getDistance(tracked.lastArcPosition, currentPos);
                if (arcDistance >= MIN_ARC_DISTANCE) {
                    spawnLightningArc(engine, weapon.getShip(), tracked.lastArcPosition, currentPos);
                    tracked.lastArcPosition = new Vector2f(currentPos);
                }
            }

            // --- Ring trail ---
            tracked.ringInterval.advance(amount);
            if (tracked.ringInterval.intervalElapsed()) {
                float ringDistance = Misc.getDistance(tracked.lastRingPosition, currentPos);
                if (ringDistance >= MIN_RING_DISTANCE) {
                    spawnRingGroup(engine, currentPos, proj.getFacing());
                    tracked.lastRingPosition = new Vector2f(currentPos);
                }
            }
        }

        // Remove dead/finished projectiles
        trackedProjectiles.removeAll(toRemove);
    }

    /**
     * Spawns a final arc to close any gap between the last arc position and the projectile's final position.
     * This ensures complete visual coverage up to the weapon's max range.
     */
    protected void spawnFinalArc(CombatEngineAPI engine, WeaponAPI weapon, TrackedProjectile tracked, DamagingProjectileAPI proj) {
        if (engine == null || weapon == null || tracked == null) {
            return;
        }

        // Get the projectile's final position (if still valid)
        Vector2f finalPos = null;
        if (proj != null && proj.getLocation() != null) {
            finalPos = new Vector2f(proj.getLocation());
        } else if (tracked.lastArcPosition != null) {
            // If projectile is null, use last known position
            finalPos = tracked.lastArcPosition;
        }

        if (finalPos == null) {
            return;
        }

        // Calculate the distance from last arc spawn to final position
        float distanceToFinal = Misc.getDistance(tracked.lastArcPosition, finalPos);

        // Only spawn final arc if there's a meaningful gap
        // Use a lower threshold than MIN_ARC_DISTANCE to ensure we catch smaller gaps
        if (distanceToFinal >= MIN_ARC_DISTANCE * 0.5f) {
            ShipAPI ship = weapon.getShip();
            if (ship != null) {
                spawnLightningArc(engine, ship, tracked.lastArcPosition, finalPos);
            }
        }
    }

    /**
     * Spawns a lightning arc from one position to another, creating a trail effect
     */
    protected void spawnLightningArc(CombatEngineAPI engine, ShipAPI ship, Vector2f from, Vector2f to) {
        if (engine == null || ship == null || from == null || to == null) {
            return;
        }

        float dist = Misc.getDistance(from, to);
        if (dist < 1f) {
            return; // Arc too short, skip
        }

        // Configure arc visual parameters
        EmpArcParams params = new EmpArcParams();
        params.segmentLengthMult = 10f;
        params.zigZagReductionFactor = 0.08f;
        params.fadeOutDist = 50f;
        params.minFadeOutMult = 10f;
        params.flickerRateMult = 0.3f;

        // Configure bright spot parameters for visual effect
        float fraction = Math.min(0.33f, 300f / dist);
        params.brightSpotFullFraction = fraction;
        params.brightSpotFadeFraction = fraction;

        // Calculate arc animation duration based on distance
        params.movementDurOverride = Math.max(0.05f, dist / ARC_SPEED);

        // Spawn the arc visual (null anchors = absolute world position, won't rotate with ship)
        EmpArcEntityAPI arc = engine.spawnEmpArcVisual(
                from,
                null,
                to,
                null,
                ARC_THICKNESS,
                ARC_COLOR,
                ARC_CORE_COLOR,
                params
        );

        // Configure arc appearance
        arc.setCoreWidthOverride(ARC_CORE_WIDTH);
        arc.setRenderGlowAtStart(false);
        arc.setFadedOutAtStart(true);
        arc.setSingleFlickerMode(true);

        // Play lightning sound effect at midpoint of arc
        Vector2f soundPoint = Vector2f.add(from, to, new Vector2f());
        soundPoint.scale(0.5f);
        Global.getSoundPlayer().playSound("abyssal_glare_lightning", 1f, 0.8f, soundPoint, new Vector2f());
    }

    /**
     * Spawns a group of expanding ring sprites at the given world position,
     * oriented along the specified facing angle.
     */
    protected void spawnRingGroup(CombatEngineAPI engine, Vector2f position, float facingAngle) {
        if (engine == null || position == null) return;

        ProjectileRingEffectPlugin plugin = new ProjectileRingEffectPlugin(
                position, facingAngle, RING_SIZE_MULTIPLIER, RING_DURATION_MULTIPLIER,
                RING_CORE_COLOR, RING_FRINGE_COLOR);

        CombatEntityAPI entity = engine.addLayeredRenderingPlugin(plugin);
        entity.getLocation().set(position);
    }

    /**
     * Spawns a muzzle flash effect at the specified location
     */
    public static void spawnMuzzleFlash(MuzzleFlashSpec spec, Vector2f point, float angle,
                                        Vector2f shipVel, float velMult, float velAdd) {
        if (spec == null) return;

        CombatEngineAPI engine = Global.getCombatEngine();

        Color color = spec.getParticleColor();
        float min = spec.getParticleSizeMin();
        float range = spec.getParticleSizeRange();
        float spread = spec.getSpread();
        float length = spec.getLength();

        for (int i = 0; i < spec.getParticleCount(); i++) {
            float size = range * (float) Math.random() + min;
            size *= SIZE_MULTIPLIER;

            float theta = (float) (Math.random() * Math.toRadians(spread) +
                    Math.toRadians(angle - spread/2f));
            float r = (float) (Math.random() * length);

            Vector2f dir = new Vector2f((float) Math.cos(theta), (float) Math.sin(theta));
            float x = dir.x * r;
            float y = dir.y * r;

            Vector2f loc = new Vector2f(point.x + x, point.y + y);
            Vector2f vel = new Vector2f(
                    x * velMult + shipVel.x + dir.x * velAdd,
                    y * velMult + shipVel.y + dir.y * velAdd
            );

            // Add some randomness to velocity
            Vector2f rand = Misc.getPointWithinRadius(new Vector2f(), length * 0.3f);
            Vector2f.add(vel, rand, vel);

            // Calculate particle duration with some variation
            float dur = spec.getParticleDuration();
            dur *= 0.8f + (float) Math.random() * 0.4f;

            // Spawn the particle
            engine.addNebulaParticle(
                    loc,
                    vel,
                    size,
                    0.5f, // end size multiplier
                    0f,   // ramp up fraction
                    0f,   // full brightness fraction
                    dur,
                    color
            );
        }
    }
}