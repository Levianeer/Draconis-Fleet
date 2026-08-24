package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

public class XLII_ChaingunEffect implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin {

    private static final float SPINUP_TIME   = 2.0f;
    private static final float SPINDOWN_TIME = 1.0f;
    private static final float MAX_COOLDOWN  = 60f / 300f;  // 0.2s  (300 RPM cold)
    private static final float MIN_COOLDOWN  = 60f / 900f;  // 0.0667s (900 RPM max)

    // Pitch range: 0.8 cold -> 1.2 full spin
    private static final float PITCH_MIN = 0.8f;
    private static final float PITCH_MAX = 1.2f;

    // Accuracy ramp: wide spread cold -> dead-on at full spin
    private static final float SPREAD_COLD      = 30f;
    private static final float SPREAD_FULL_SPIN = 1f;

    private float spinLevel = 0f;
    private float timeSinceLastAllowedShot = MAX_COOLDOWN;

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        float targetInterval = MAX_COOLDOWN + (MIN_COOLDOWN - MAX_COOLDOWN) * spinLevel;
        if (timeSinceLastAllowedShot >= targetInterval) {
            timeSinceLastAllowedShot = 0f;
            float pitch = PITCH_MIN + (PITCH_MAX - PITCH_MIN) * spinLevel;
            Global.getSoundPlayer().playSound("thumper_spinup", pitch, 1f, projectile.getLocation(), projectile.getVelocity());
            Global.getSoundPlayer().playSound("thumper_fire",   pitch, 1f, projectile.getLocation(), projectile.getVelocity());

            float spread = SPREAD_COLD + (SPREAD_FULL_SPIN - SPREAD_COLD) * spinLevel;
            if (spread > 0f) {
                float offset = (float) (Math.random() - 0.5) * spread;
                projectile.setFacing(projectile.getFacing() + offset);
                Vector2f vel = projectile.getVelocity();
                Vector2f rotated = Misc.rotateAroundOrigin(vel, offset);
                vel.set(rotated.x, rotated.y);
            }
        } else {
            engine.removeEntity(projectile);
        }
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine.isPaused()) return;

        timeSinceLastAllowedShot += amount;

        boolean isActive = timeSinceLastAllowedShot < MAX_COOLDOWN + amount;
        if (isActive) {
            spinLevel = Math.min(1f, spinLevel + amount / SPINUP_TIME);
        } else {
            spinLevel = Math.max(0f, spinLevel - amount / SPINDOWN_TIME);
        }
    }
}
