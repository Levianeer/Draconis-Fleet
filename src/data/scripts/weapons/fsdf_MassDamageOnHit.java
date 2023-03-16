package data.scripts.weapons;

import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
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

      float mass = target.getMass ();                                         // Get mass.
      float damageModifier = (mass / 1000) + (float)(8.5 / Math.sqrt(mass));  // Calculate the damage modifier. Thanks Owen for the math function <3
      float damage = projectile.getDamageAmount();                            // Get the initial damage of the projectile.
      float newDamage = damage * damageModifier;                              // Calculate the new damage based on the damage modifier.

      //Get locations and deal damage, god this was a pain in the ass to work out.
      engine.applyDamage(target, point, newDamage, DamageType.KINETIC, 0, false, false, target, false);

      //I have to work out a better way of doing this.
      Vector2f vel = new Vector2f();
      if (target != null) vel.set(target.getVelocity());
      Global.getSoundPlayer().playSound("rifttorpedo_explosion", 1f, 1f, point, vel);

      engine.spawnExplosion(point, vel, new Color(255, 125, 50), newDamage / 5, 1f);  //SFX Stuff.
    }
  }
}