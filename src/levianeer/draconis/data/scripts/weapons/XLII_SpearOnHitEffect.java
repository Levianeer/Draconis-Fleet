package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class XLII_SpearOnHitEffect implements OnHitEffectPlugin {

    private static final Color WHITE = Color.WHITE;
    private static final Color ARC_COLOR_CORE = new Color(35, 105, 155, 255);
    private static final Color ARC_COLOR_FRINGE = new Color(255, 255, 255, 255);
    private static final Vector2f ZERO_VELOCITY = new Vector2f(0f, 0f);

    private static final String SOUND_ID = "mine_explosion";
    private static final String ARC_SOUND_ID = "shock_repeater_emp_impact";
    private static final float DEBRIS_ARC = 150f;
    private static final float PIERCE_BASE_CHANCE = -0.1f;
    private static final float PIERCE_FLUX_MULT = 0.75f;

    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target,
                      Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {

        if (point == null) {
            return;
        }

        // Cache target velocity
        Vector2f targetVel = target.getVelocity();

        // Spawn visual effects
        spawnExplosionEffects(engine, point, targetVel);

        // Spawn debris only if not shield hit
        if (!shieldHit) {
            spawnDebris(engine, target, point, targetVel);
        }

        // Handle EMP effects
        handleEmpEffects(projectile, target, point, shieldHit, engine);

        // Play SFX
        Global.getSoundPlayer().playSound(SOUND_ID, 1f, 1f, point, ZERO_VELOCITY);
    }

    private void spawnExplosionEffects(CombatEngineAPI engine, Vector2f point, Vector2f velocity) {
        engine.spawnExplosion(point, velocity, WHITE, 90f, 0.6f);
        engine.spawnExplosion(point, velocity, WHITE, 60f, 0.6f);
        engine.addNegativeNebulaParticle(point, velocity, 30f, 2f, 0f, 0f, 0.5f, WHITE);
    }

    private void spawnDebris(CombatEngineAPI engine, CombatEntityAPI target, Vector2f point, Vector2f velocity) {
        float direction = Misc.getAngleInDegrees(target.getLocation(), point);

        engine.spawnDebrisSmall(point, velocity, 6, direction, DEBRIS_ARC, 20f, 20f, 720f);
        engine.spawnDebrisMedium(point, velocity, 3, direction, DEBRIS_ARC, 10f, 20f, 360f);
        engine.spawnDebrisLarge(point, velocity, 1, direction, DEBRIS_ARC, 10f, 10f, 180f);
    }

    private void handleEmpEffects(DamagingProjectileAPI projectile, CombatEntityAPI target,
                                  Vector2f point, boolean shieldHit, CombatEngineAPI engine) {

        // Early return if target is not a ship
        if (!(target instanceof ShipAPI empTarget)) {
            return;
        }

        boolean shouldApplyEmp = !shieldHit;

        // Calculate shield piercing
        if (shieldHit) {
            float pierceChance = calculatePierceChance(empTarget);
            shouldApplyEmp = Math.random() < pierceChance;
        }

        if (shouldApplyEmp) {
            spawnEmpArc(projectile, empTarget, point, engine);
        }
    }

    private float calculatePierceChance(ShipAPI ship) {
        float baseChance = ship.getHardFluxLevel() * PIERCE_FLUX_MULT + PIERCE_BASE_CHANCE;
        float pierceMultiplier = ship.getMutableStats().getDynamic().getValue(Stats.SHIELD_PIERCED_MULT);
        return baseChance * pierceMultiplier;
    }

    private void spawnEmpArc(DamagingProjectileAPI projectile, ShipAPI target,
                             Vector2f point, CombatEngineAPI engine) {

        float empDamage = projectile.getEmpAmount();
        float damage = projectile.getDamageAmount();

        engine.spawnEmpArcPierceShields(
                projectile.getSource(),
                point,
                target,
                target,
                DamageType.ENERGY,
                damage,
                empDamage,
                100000f,
                ARC_SOUND_ID,
                15f,
                ARC_COLOR_CORE,
                ARC_COLOR_FRINGE
        );
    }
}