package levianeer.draconis.data.scripts.weapons;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.loading.BeamWeaponSpecAPI;
import com.fs.starfarer.api.util.FaderUtil;

import org.apache.log4j.Logger;

/**
 * Spawns three expanding ring sprites when the Sovnya Burst Lance beam fires, based on Domain
 * Phase Lab's Bombardon ring effect pattern.
 */
public class XLII_SovnyaLanceEffect implements EveryFrameWeaponEffectPlugin {

    private static final Logger log = Global.getLogger(XLII_SovnyaLanceEffect.class);

    // Detects the beam's off-to-on transition so the ring effect fires once per burst, not every frame.
    private boolean wasFiring = false;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine.isPaused()) return;
        if (weapon == null || weapon.getShip() == null) return;

        boolean isFiring = weapon.isFiring();

        if (isFiring && !wasFiring) {
            spawnRingEffect(weapon, engine);
        }

        wasFiring = isFiring;
    }

    /**
     * Spawns three ring groups along the beam path, progressively smaller with distance; for beam
     * weapons, duration and color are scaled/extracted from the beam's burst duration and core/fringe colors.
     */
    private void spawnRingEffect(WeaponAPI weapon, CombatEngineAPI engine) {
        if (!weapon.getShip().isAlive()) return;

        float durationMultiplier = 1f;
        Color coreColor = new Color(255, 255, 255); // Default
        Color fringeColor = new Color(255, 255, 255); // Default

        if (weapon.getSpec() instanceof BeamWeaponSpecAPI beamSpec) {
            float burstDuration = beamSpec.getBurstDuration();

            // Base longest ring duration is 0.1s, scale all durations to match burst
            if (burstDuration > 0) {
                durationMultiplier = burstDuration / 0.1f;
            }

            coreColor = beamSpec.getCoreColor();
            fringeColor = beamSpec.getFringeColor();
        }

        float[] distances = {60f, 100f, 140f};
        float[] sizeMultipliers = {0.8f, 0.7f, 0.6f};

        for (int i = 0; i < distances.length; i++) {
            RingEffectPlugin plugin = new RingEffectPlugin(weapon, distances[i], sizeMultipliers[i],
                    durationMultiplier, coreColor, fringeColor);
            CombatEntityAPI entity = engine.addLayeredRenderingPlugin(plugin);
            // Initial location set to the fire point; render() recalculates the actual position each frame.
            entity.getLocation().set(weapon.getFirePoint(0));
        }

        if (log.isDebugEnabled()) {
            log.debug("Draconis: Sovnya Lance fired, spawned 3 ring groups along beam path (duration mult: "
                    + durationMultiplier + ")");
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

            // Use additive blending for glow effect
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
            return time >= maxDur + 0.15f; // Add buffer for fade-out
        }
    }

    /**
     * Rendering plugin that manages and renders the ring effect with two-tone colors.
     */
    protected static class RingEffectPlugin extends BaseCombatLayeredRenderingPlugin {
        protected WeaponAPI weapon;
        protected List<ParticleData> particles = new ArrayList<>();
        protected float offsetDistance; // Distance from weapon fire point along weapon angle
        protected Color coreColor; // Core color from beam (base layer)
        protected Color fringeColor; // Fringe color from beam (glow layer)

        public RingEffectPlugin(WeaponAPI weapon, float offsetDistance, float sizeMultiplier,
                               float durationMultiplier, Color coreColor, Color fringeColor) {
            this.weapon = weapon;
            this.offsetDistance = offsetDistance;
            this.coreColor = coreColor;
            this.fringeColor = fringeColor;

            SpriteAPI ring1 = Global.getSettings().getSprite("fx", "XLII_sovnya_ring1");
            SpriteAPI ring2 = Global.getSettings().getSprite("fx", "XLII_sovnya_ring2");
            SpriteAPI ring3 = Global.getSettings().getSprite("fx", "XLII_sovnya_ring3");

            // Base size scaled by the multiplier (closer = bigger, further = smaller)
            float baseSize = 10f * sizeMultiplier;

            // Base durations scaled by the duration multiplier to match beam burst duration
            float duration1 = 0.025f * durationMultiplier;
            float duration2 = 0.0375f * durationMultiplier;
            float duration3 = 0.05f * durationMultiplier;

            // Three rings with progressively larger scales and durations create a cascading effect.
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
            // Position is recalculated every frame (not fixed at spawn) so rings track weapon rotation.
            Vector2f firePoint = weapon.getFirePoint(0);
            float weaponAngle = weapon.getCurrAngle();
            float angleRad = (float) Math.toRadians(weaponAngle);

            float x = firePoint.x + offsetDistance * (float) Math.cos(angleRad);
            float y = firePoint.y + offsetDistance * (float) Math.sin(angleRad);

            for (ParticleData p : particles) {
                p.sprite.setAngle(p.angle + weapon.getCurrAngle() - 90f);

                float currentSize = p.size * p.scale;
                float alpha = p.fader.getBrightness();

                // Layer 1: Render with core color (base layer)
                p.sprite.setColor(coreColor);
                p.sprite.setSize(currentSize, currentSize);
                p.sprite.setCenter(currentSize * 0.5f, currentSize * 0.5f);
                p.sprite.setAlphaMult(alpha);
                p.sprite.renderAtCenter(x, y);

                // Layer 2: Render with fringe color (glow layer, slightly larger)
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
            return 400f; // Increased to account for rings spawned further away
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
}