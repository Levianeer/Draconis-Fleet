package levianeer.draconis.data.scripts.weapons;

import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicFakeBeam;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;

/**
 * OnFire/EveryFrame effect for the Trebuchet Cannon (XLII_ttc).
 * <p>
 * Preserves the existing Culverin smoke-ring muzzle effect, and adds a
 * laser sight on each barrel that hasn't yet fired in the current burst.
 * Sights appear during charge-up and disappear barrel-by-barrel as the
 * ALTERNATING burst sequence fires. Each beam terminates at the first
 * ship/missile/asteroid in its line of sight.
 */
public class XLII_TrebuchetWeaponEffect implements OnFireEffectPlugin, EveryFrameWeaponEffectPlugin {

    // Laser sight visuals
    private static final Color LASER_CORE    = new Color(255,  60,  60, 15);
    private static final Color LASER_FRINGE  = new Color(255,  30,  30,  5);
    private static final float LASER_WIDTH_IN    = 2f;    // world units at muzzle
    private static final float LASER_WIDTH_OUT   = 0.5f;    // world units at tip (tapered)
    private static final float LASER_SMOOTH_OUT  = 50f;   // fade length at tip
    private static final float LASER_RANGE_BONUS = 50f;  // extra range beyond weapon range
    private static final int   NUM_BARRELS        = 3;

    // Delegate for the existing Culverin smoke-ring muzzle blast
    private final XLII_CulverinOnFireEffect culverinEffect = new XLII_CulverinOnFireEffect();

    // How many barrels have fired in the current burst (0–3)
    private int firedCount = 0;
    // Charge level last frame - used to detect the start of a fresh chargeup
    private float prevChargeLevel = 0f;

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        culverinEffect.onFire(projectile, weapon, engine);
        firedCount = Math.min(firedCount + 1, NUM_BARRELS);
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        // Always let the culverin effect process its pending ring spawns
        culverinEffect.advance(amount, engine, weapon);

        if (engine.isPaused()) return;
        if (weapon == null || weapon.getShip() == null) return;

        float chargeLevel = weapon.getChargeLevel();

        // Detect the start of a fresh chargeup: chargeLevel was 0, now rising
        if (chargeLevel > 0f && prevChargeLevel <= 0f) {
            firedCount = 0;
        }
        prevChargeLevel = chargeLevel;

        // Show sights while the weapon is active and there are unfired barrels
        if (chargeLevel <= 0f || firedCount >= NUM_BARRELS) return;

        float angle = weapon.getCurrAngle();
        float range = weapon.getRange() + LASER_RANGE_BONUS;

        for (int i = firedCount; i < NUM_BARRELS; i++) {
            Vector2f from = weapon.getFirePoint(i);
            // spawnAdvancedFakeBeam renders via MagicTrailPlugin (world-space, zoom-invariant)
            // and performs LoS collision - beam terminates at the nearest obstacle.
            // normalDamage=0, impactSize=0: purely visual, no damage, no hit particles.
            MagicFakeBeam.spawnAdvancedFakeBeam(
                    engine, from, range, angle,
                    LASER_WIDTH_IN, LASER_WIDTH_OUT, 0f,
                    "base_trail_smooth", "base_trail_aura",
                    64f, 0f,
                    0f, LASER_SMOOTH_OUT,
                    amount, 0.01f,
                    5f,
                    LASER_CORE, LASER_FRINGE,
                    0f, DamageType.ENERGY, 0f,
                    weapon.getShip()
            );
        }
    }
}
