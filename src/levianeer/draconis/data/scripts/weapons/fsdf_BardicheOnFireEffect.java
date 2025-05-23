package levianeer.draconis.data.scripts.weapons;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;

public class fsdf_BardicheOnFireEffect implements OnFireEffectPlugin, DamageDealtModifier {

    public static float DAMAGE = 50;

    protected String weaponId = null;

    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        ShipAPI ship = weapon.getShip();
        if (!ship.hasListenerOfClass(fsdf_BardicheOnFireEffect.class)) {
            ship.addListener(this);
            weaponId = weapon.getId();
        }
    }

    public String modifyDamageDealt(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
        if (shieldHit && param instanceof MissileAPI) {
            MissileAPI m = (MissileAPI) param;
            if (m.getWeaponSpec() != null && m.getWeaponSpec().getWeaponId().equals(weaponId)) {
                float base = damage.getBaseDamage();
                damage.setDamage(base + DAMAGE);
                return "bardiche";
            }
        }
        return null;
    }
}