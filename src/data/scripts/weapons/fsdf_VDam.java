package data.scripts.weapons;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

public class fsdf_VDam implements OnHitEffectPlugin {

	public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target,
					  Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {

        // Get velocity
        float missileVelocity = projectile.getVelocity().length();

        // Base damage
        float baseDamage = 100f;

        // Calculate damage
        float velocityMultiplier = 0.25f;
        float damage = baseDamage + (missileVelocity * velocityMultiplier);

        // Apply the damage
        engine.applyDamage(target, point, damage, DamageType.ENERGY, 0f, false, false, projectile.getSource());
    }
}