package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicLensFlare;

import java.awt.*;

public class XLII_EMPBlastOnHitEffect implements OnHitEffectPlugin {

    private static final float BASE_PIERCE_CHANCE = 0.10f; // 10% at 0 hard flux
    private static final float MAX_PIERCE_CHANCE = 0.50f;  // 50% at max hard flux
    private static final float PIERCE_EMP_MULT = 1.5f;     // Multiply EMP damage by this when piercing
    private static final DamagingExplosionSpec VISUAL_EXPLOSION_SPEC = createCachedVisualExplosionSpec();

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target,
                      Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {

        if (!(target instanceof ShipAPI ship)) return;

        float empDamage = projectile.getEmpAmount();
        ShipAPI source = projectile.getSource();

        // Handle shield hit with arc-through chance
        if (shieldHit) {
            handleShieldPierce(ship, source, point, empDamage, engine);
        }

        // Spawn EMP explosion (deals EMP damage in area)
        engine.spawnDamagingExplosion(createEMPExplosionSpec(empDamage), source, point);

        // Spawn visual explosion
        engine.spawnDamagingExplosion(VISUAL_EXPLOSION_SPEC, source, point);

        // Spawn EMP visual effects
        spawnEMPVisuals(engine, point);
    }

    /**
     * Handles shield pierce mechanics based on target's hard flux level
     */
    private void handleShieldPierce(ShipAPI target, ShipAPI source, Vector2f point, float empDamage, CombatEngineAPI engine) {
        if (target.getFluxTracker() == null) return;

        // Calculate pierce chance based on hard flux level
        float hardFluxLevel = target.getFluxTracker().getHardFlux() / target.getFluxTracker().getMaxFlux();
        float pierceChance = BASE_PIERCE_CHANCE + (MAX_PIERCE_CHANCE - BASE_PIERCE_CHANCE) * hardFluxLevel;

        // Roll for pierce
        if (Math.random() < pierceChance) {
            // Pierce successful - arc to weapons and engines
            spawnShieldPierceArcs(target, source, point, empDamage, engine);

            // Apply EMP damage to systems (scaled by pierce multiplier)
            target.getFluxTracker().increaseFlux(empDamage * PIERCE_EMP_MULT, false);

            // Small chance to disable a random weapon
            if (Math.random() < 0.2f) {
                disableRandomWeapon(target);
            }
        }
    }

    /**
     * Spawns visual EMP arcs that pierce through shields
     */
    private void spawnShieldPierceArcs(ShipAPI target, ShipAPI source, Vector2f point, float empDamage, CombatEngineAPI engine) {
        int arcCount = 2 + (int)(Math.random() * 2); // 2-3 arcs

        for (int i = 0; i < arcCount; i++) {
            // Target random point on the ship
            Vector2f targetPoint = MathUtils.getRandomPointInCircle(target.getLocation(), target.getCollisionRadius() * 0.5f);

            engine.spawnEmpArcPierceShields(
                    source,
                    point,
                    target,
                    target,
                    DamageType.ENERGY,
                    0f, // no damage from arc itself
                    empDamage * PIERCE_EMP_MULT / arcCount, // split EMP damage across arcs
                    100000f, // max range
                    "tachyon_lance_emp_impact",
                    12f + (float)Math.random() * 10f, // thickness variation
                    new Color(100, 150, 255, 255),
                    new Color(200, 220, 255, 255)
            );

            // Secondary smaller arcs (fewer)
            if (Math.random() < 0.4f) {
                engine.spawnEmpArc(
                        source,
                        targetPoint,
                        target,
                        target,
                        DamageType.ENERGY,
                        0f,
                        0f,
                        100000f,
                        null,
                        6f + (float)Math.random() * 6f,
                        new Color(150, 180, 255, 180),
                        new Color(220, 230, 255, 120)
                );
            }
        }
    }

    /**
     * Disables a random weapon on the target
     */
    private void disableRandomWeapon(ShipAPI target) {
        if (target.getAllWeapons().isEmpty()) return;

        WeaponAPI weapon = target.getAllWeapons().get((int)(Math.random() * target.getAllWeapons().size()));
        if (weapon != null && !weapon.isPermanentlyDisabled() && !weapon.isDecorative()) {
            weapon.disable(true);
        }
    }

    /**
     * Creates the main EMP explosion spec
     */
    private static DamagingExplosionSpec createEMPExplosionSpec(float empDamage) {
        DamagingExplosionSpec spec = new DamagingExplosionSpec(
                0.2f,              // duration
                350f,              // max radius
                250f,              // core radius
                empDamage * 0.2f,  // full damage
                empDamage * 0.05f, // min damage
                CollisionClass.PROJECTILE_FF,
                CollisionClass.PROJECTILE_FIGHTER,
                4f,                // particle size min
                6f,                // particle size range
                0.4f,              // particle duration
                20,                // particle count
                new Color(100, 150, 255, 130),
                new Color(200, 220, 255, 80)
        );
        spec.setDamageType(DamageType.ENERGY);
        spec.setSoundSetId("system_emp_emitter_activate");
        return spec;
    }

    /**
     * Creates the cached visual explosion spec
     */
    private static DamagingExplosionSpec createCachedVisualExplosionSpec() {
        DamagingExplosionSpec spec = new DamagingExplosionSpec(
                0.5f,
                380f,
                280f,
                0f, // no damage
                0f,
                CollisionClass.NONE,
                CollisionClass.NONE,
                5f,
                10f,
                1.0f,
                60,
                new Color(80, 130, 255, 180),
                new Color(180, 210, 255, 80)
        );

        spec.setUseDetailedExplosion(true);
        spec.setDetailedExplosionFlashDuration(1.0f);
        spec.setDetailedExplosionRadius(280f);
        spec.setDetailedExplosionFlashRadius(380f);
        spec.setDetailedExplosionFlashColorCore(new Color(200, 220, 255, 255));
        spec.setDetailedExplosionFlashColorFringe(new Color(100, 150, 255, 160));
        spec.setDamageType(DamageType.ENERGY);
        spec.setSoundSetId(null);

        return spec;
    }

    /**
     * Spawns EMP-themed visual effects
     */
    private static void spawnEMPVisuals(CombatEngineAPI engine, Vector2f center) {
        // Electrical ring effect - using particles instead of entity targets
        final int ringCount = 2;
        for (int ring = 0; ring < ringCount; ring++) {
            float ringRadius = 120f + (ring * 80f);
            int arcCount = 6 + (ring * 3);

            for (int i = 0; i < arcCount; i++) {
                float angle = (360f / arcCount) * i;
                Vector2f point = MathUtils.getPointOnCircumference(center, ringRadius, angle);

                engine.addSmoothParticle(
                        point,
                        new Vector2f(0f, 0f),
                        40f + (float)Math.random() * 25f,
                        1f,
                        0.25f + (float)Math.random() * 0.15f,
                        new Color(100, 150, 255, 130 - ring * 30)
                );
            }
        }

        // Sharp lens flares for EMP burst (reduced count)
        final int flareCount = 10;
        final float flareRange = 100f;

        for (int i = 0; i < flareCount; i++) {
            Vector2f flarePoint = MathUtils.getRandomPointInCircle(center, flareRange);
            float angle = (float) Math.random() * 360f;

            MagicLensFlare.createSharpFlare(
                    engine,
                    null,
                    flarePoint,
                    320f,
                    120f,
                    angle,
                    new Color(100, 150, 255, 255),
                    new Color(220, 230, 255, 255)
            );
        }

        // Random branching arcs - add hit particles only
        final int branchArcCount = 8;
        for (int i = 0; i < branchArcCount; i++) {
            Vector2f endPoint = MathUtils.getRandomPointInCircle(center, 300f);

            // Add visual particles for branching effect
            engine.addHitParticle(
                    endPoint,
                    new Vector2f(0f, 0f),
                    60f + (float)Math.random() * 30f,
                    1f,
                    0.3f + (float)Math.random() * 0.2f,
                    new Color(150, 180, 255, 200)
            );
        }
    }
}