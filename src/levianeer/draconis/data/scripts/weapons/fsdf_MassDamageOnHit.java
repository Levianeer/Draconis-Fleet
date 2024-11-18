package levianeer.draconis.data.scripts.weapons;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

public class fsdf_MassDamageOnHit implements OnHitEffectPlugin {

	public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target,
					  Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
            if (!shieldHit && target instanceof ShipAPI) {

      float mass = target.getMass ();
      float damageModifier = (mass / 2500) + (float)(8.5 / Math.sqrt(mass));  // Calculates the modifier. Thanks Owen for the equation <3
      float damage = projectile.getDamageAmount();
      float newDamage = damage * damageModifier;

            Math.min(damageModifier, 2.5);  // Upper limit
            Math.max(damageModifier, 0.5);  // Lower limit

      engine.applyDamage(target, point, newDamage, DamageType.ENERGY, 0, false, false, target, false); // Get location, velocity, etc and deal damage
    }
  }
}