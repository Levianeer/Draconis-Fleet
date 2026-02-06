package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import org.lazywizard.lazylib.FastTrig;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

/**
 * On-hit effect for mist cloud deployer missiles.
 * Creates visual smoke explosion and signals the combat plugin to spawn a cloud.
 */
public class XLII_MistCloudOnHitEffect implements OnHitEffectPlugin {

    // Color constants (pre-allocated to avoid per-call allocations)
    private static final Color SMOKE_COLOR_BRIGHT = new Color(205, 205, 205, 155);
    private static final Color SMOKE_COLOR_PUFF = new Color(170, 170, 170, 150);

    // Effect constants
    private static final float EXPLOSION_RADIUS = 120f;
    private static final int SMOKE_RING_COUNT = 5;
    private static final int RADIATING_PUFF_COUNT = 12;
    private static final int DENSE_SMOKE_COUNT = 20;

    // Reusable vectors to reduce allocations
    private static final Vector2f ZERO_VEL = new Vector2f(0f, 0f);
    private static final Vector2f tempOffset = new Vector2f();
    private static final Vector2f tempPos = new Vector2f();
    private static final Vector2f tempVel = new Vector2f();

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target,
                      Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult,
                      CombatEngineAPI engine) {

        if (projectile == null || engine == null) return;

        Vector2f impactPoint = point != null ? point : projectile.getLocation();

        // Spawn visual explosion
        spawnSmokeExplosion(engine, impactPoint);

        // Play sound
        Global.getSoundPlayer().playSound(
            "vent_flux",
            1.0f,
            0.7f,
            impactPoint,
            new Vector2f()
        );

        // Signal the combat plugin to create a cloud at this location
        if (projectile.getSource() != null) {
            engine.getCustomData().put("XLII_MIST_SPAWN_" + System.nanoTime(), impactPoint);
        }

        // No manual despawn needed - the missile naturally expires after hitting
        // The "frozen missile" bug was actually caused by the AI anchor in MistCloudsPlugin
    }

    /**
     * Spawns layered smoke effects at the impact point
     * Optimized with reduced allocations and FastTrig usage
     */
    private void spawnSmokeExplosion(CombatEngineAPI engine, Vector2f point) {
        // Central bright flash
        engine.addSmoothParticle(
            point,
            ZERO_VEL,
            80f,
            1.0f,
            0.3f,
            SMOKE_COLOR_BRIGHT
        );

        // Expanding smoke rings (pre-calculated alpha values to avoid Color allocations)
        for (int i = 0; i < SMOKE_RING_COUNT; i++) {
            float size = 60f + (i * 30f);
            float duration = 1.5f + (i * 0.3f);
            float brightness = 0.8f - (i * 0.12f);
            int alpha = Math.max(200 - (i * 30), 50);

            engine.addSmoothParticle(
                point,
                ZERO_VEL,
                size,
                brightness,
                duration,
                new Color(180, 180, 180, alpha)
            );
        }

        // Radiating smoke puffs (using FastTrig and reusable vectors)
        for (int i = 0; i < RADIATING_PUFF_COUNT; i++) {
            float angle = (360f / RADIATING_PUFF_COUNT) * i + (float)(Math.random() * 20f - 10f);
            float distance = 40f + (float)(Math.random() * 30f);

            // Reuse tempOffset vector
            tempOffset.set(
                (float)FastTrig.cos(Math.toRadians(angle)) * distance,
                (float)FastTrig.sin(Math.toRadians(angle)) * distance
            );

            // Reuse tempPos vector
            tempPos.set(point.x + tempOffset.x, point.y + tempOffset.y);

            // Reuse tempVel vector (scaled offset)
            tempVel.set(tempOffset.x * 0.5f, tempOffset.y * 0.5f);

            engine.addSmokeParticle(
                tempPos,
                tempVel,
                25f + (float)(Math.random() * 15f),
                0.7f,
                1.2f + (float)(Math.random() * 0.5f),
                SMOKE_COLOR_PUFF
            );
        }

        // Additional dense smoke particles for volume (using FastTrig and reusable vectors)
        for (int i = 0; i < DENSE_SMOKE_COUNT; i++) {
            float angle = (float)(Math.random() * 360f);
            float distance = (float)(Math.random() * EXPLOSION_RADIUS * 0.7f);

            // Reuse tempOffset vector
            tempOffset.set(
                (float)FastTrig.cos(Math.toRadians(angle)) * distance,
                (float)FastTrig.sin(Math.toRadians(angle)) * distance
            );

            // Reuse tempPos vector
            tempPos.set(point.x + tempOffset.x, point.y + tempOffset.y);

            // Reuse tempVel vector
            tempVel.set(
                (float)(Math.random() * 40f - 20f),
                (float)(Math.random() * 40f - 20f)
            );

            int alpha = 100 + (int)(Math.random() * 80);

            engine.addSmokeParticle(
                tempPos,
                tempVel,
                20f + (float)(Math.random() * 20f),
                0.6f + (float)(Math.random() * 0.2f),
                2f + (float)(Math.random() * 1f),
                new Color(160, 160, 160, alpha)
            );
        }
    }
}