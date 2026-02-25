package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * OnFireEffect/EveryFrameEffect for the Culverin. Spawns three expanding smoke rings at the
 * muzzle on each shot. The fire event records the spawn parameters; the actual rendering plugins
 * are added in advance() where the combat engine is in a stable state.
 */
public class XLII_CulverinOnFireEffect implements OnFireEffectPlugin, EveryFrameWeaponEffectPlugin {

    // Distance from the muzzle to the first ring (shifts all rings forward together)
    private static final float RING_ORIGIN_OFFSET = 30f;

    // Distance between each of the three ring groups along the firing direction
    private static final float RING_SPACING = 12f;

    // Base size of each ring sprite at spawn
    private static final float RING_BASE_SIZE = 20f;

    // How much each ring expands before disappearing (multiplier applied over its lifetime)
    private static final float RING_TARGET_SCALE = 2.8f;

    // How long the rings are visible (seconds)
    private static final float RING_DURATION = 0.55f;

    // Size of the furthest ring relative to the closest (e.g. 0.5 = half the size)
    private static final float RING_SIZE_AT_FURTHEST = 0.5f;

    // Core (inner) color — bright white, nearly opaque
    private static final Color RING_CORE_COLOR = new Color(245, 245, 245, 155);

    // Fringe (outer glow) color — slightly pink-tinted translucent smoke
    private static final Color RING_FRINGE_COLOR = new Color(200, 170, 175, 55);

    // -------------------------------------------------------------------------

    /**
     * Stores the muzzle position and angle of a fired shot to be processed in advance().
     */
     protected record PendingRingSpawn(Vector2f position, float angle) {
        protected PendingRingSpawn(Vector2f position, float angle) {
            this.position = new Vector2f(position);
            this.angle = angle;
        }
     }

    /**
     * Manages the lifecycle of a single ring sprite layer.
     * Starts at full brightness and fades out continuously while growing.
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

            // Start fully visible, fade to nothing over the ring's lifetime
            fader = new FaderUtil(1f, 0.01f, maxDur);
            fader.fadeOut();

            sprite.setTexWidth(1f);
            sprite.setTexHeight(1f);
            sprite.setTexX(0f);
            sprite.setTexY(0f);
            sprite.setAdditiveBlend();
        }

        public void advance(float amount) {
            time += amount;
            float progress = Math.min(time / maxDur, 1f);
            scale = 1f + (targetScale - 1f) * progress;
            fader.advance(amount);
        }

        public boolean isExpired() {
            return fader.getBrightness() <= 0.01f;
        }
    }

    /**
     * Renders a group of concentric ring sprites at a fixed world position.
     */
    protected static class RingEffectPlugin extends BaseCombatLayeredRenderingPlugin {
        protected List<ParticleData> particles = new ArrayList<>();
        protected Vector2f worldPosition;
        protected float facingAngle;
        protected Color coreColor;
        protected Color fringeColor;

        public RingEffectPlugin(Vector2f worldPosition, float facingAngle,
                                float sizeMultiplier, Color coreColor, Color fringeColor) {
            this.worldPosition = new Vector2f(worldPosition);
            this.facingAngle = facingAngle;
            this.coreColor = coreColor;
            this.fringeColor = fringeColor;

            SpriteAPI ring1 = Global.getSettings().getSprite("fx", "XLII_sovnya_ring1");
            SpriteAPI ring2 = Global.getSettings().getSprite("fx", "XLII_sovnya_ring2");
            SpriteAPI ring3 = Global.getSettings().getSprite("fx", "XLII_sovnya_ring3");

            float baseSize = RING_BASE_SIZE * sizeMultiplier;

            // Three layers form one smoke ring — stagger their growth slightly for depth
            particles.add(new ParticleData(ring1, baseSize, RING_TARGET_SCALE * 0.85f, 0f, RING_DURATION * 0.90f));
            particles.add(new ParticleData(ring2, baseSize, RING_TARGET_SCALE,         0f, RING_DURATION));
            particles.add(new ParticleData(ring3, baseSize, RING_TARGET_SCALE * 1.15f, 0f, RING_DURATION * 1.10f));
        }

        @Override
        public void advance(float amount) {
            if (Global.getCombatEngine().isPaused()) return;

            List<ParticleData> toRemove = new ArrayList<>();
            for (ParticleData p : particles) {
                p.advance(amount);
                if (p.isExpired()) toRemove.add(p);
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

                // Core layer
                p.sprite.setColor(coreColor);
                p.sprite.setSize(currentSize, currentSize);
                p.sprite.setCenter(currentSize * 0.5f, currentSize * 0.5f);
                p.sprite.setAlphaMult(alpha);
                p.sprite.renderAtCenter(x, y);

                // Fringe/glow layer, slightly larger
                float glowSize = currentSize * 1.08f;
                p.sprite.setColor(fringeColor);
                p.sprite.setSize(glowSize, glowSize);
                p.sprite.setCenter(glowSize * 0.5f, glowSize * 0.5f);
                p.sprite.setAlphaMult(alpha * 0.75f);
                p.sprite.renderAtCenter(x, y);
            }
        }

        @Override
        public float getRenderRadius() {
            return 300f;
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

    // -------------------------------------------------------------------------

    protected final List<PendingRingSpawn> pendingSpawns = new ArrayList<>();

    public XLII_CulverinOnFireEffect() {
    }

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        if (projectile.getSource() == null) return;
        // Record position and angle now — rings will be spawned in advance() on the next frame
        pendingSpawns.add(new PendingRingSpawn(weapon.getLocation(), weapon.getCurrAngle()));
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || engine.isInFastTimeAdvance() || pendingSpawns.isEmpty()) return;

        for (PendingRingSpawn spawn : pendingSpawns) {
            Vector2f forward = Misc.getUnitVectorAtDegreeAngle(spawn.angle);
            for (int i = 0; i < 3; i++) {
                float dist = RING_ORIGIN_OFFSET + RING_SPACING * i;
                Vector2f pos = new Vector2f(
                        spawn.position.x + forward.x * dist,
                        spawn.position.y + forward.y * dist
                );
                // Interpolate size from 1.0 at i=0 down to RING_SIZE_AT_FURTHEST at i=2
                float sizeMult = 1f - (i / 2f) * (1f - RING_SIZE_AT_FURTHEST);
                spawnRingGroup(engine, pos, spawn.angle, sizeMult);
            }
        }
        pendingSpawns.clear();
    }

    protected void spawnRingGroup(CombatEngineAPI engine, Vector2f position, float facingAngle, float sizeMultiplier) {
        if (engine == null || position == null) return;

        RingEffectPlugin plugin = new RingEffectPlugin(position, facingAngle, sizeMultiplier, RING_CORE_COLOR, RING_FRINGE_COLOR);
        CombatEntityAPI entity = engine.addLayeredRenderingPlugin(plugin);
        entity.getLocation().set(position);
    }
}
