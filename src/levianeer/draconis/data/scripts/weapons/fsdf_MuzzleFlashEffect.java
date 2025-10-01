package levianeer.draconis.data.scripts.weapons;

import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.loading.MuzzleFlashSpec;
import com.fs.starfarer.api.loading.ProjectileWeaponSpecAPI;
import com.fs.starfarer.api.util.Misc;

public class fsdf_MuzzleFlashEffect implements OnFireEffectPlugin {

    // Configuration parameters
    private static final float BACKWARDS_OFFSET_DISTANCE = 25f; // Distance behind the weapon
    private static final float MUZZLE_FLASH_LENGTH = 50f;
    private static final int PARTICLE_COUNT = 25;
    private static final float SPREAD = 35f; // Degrees of spread for the backwards flash
    private static final float SIZE_MULTIPLIER = 1.0f;

    public fsdf_MuzzleFlashEffect() {
    }

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        ShipAPI ship = projectile.getSource();
        if (ship == null) return;

        // Get the weapon's specifications
        ProjectileWeaponSpecAPI weaponSpec = (ProjectileWeaponSpecAPI) weapon.getSpec();
        MuzzleFlashSpec originalSpec = weaponSpec.getMuzzleFlashSpec();
        if (originalSpec == null) return;

        // Create a modified spec for the backwards flash
        MuzzleFlashSpec backwardsSpec = originalSpec.clone();
        backwardsSpec.setLength(MUZZLE_FLASH_LENGTH);
        backwardsSpec.setParticleCount(PARTICLE_COUNT);
        backwardsSpec.setSpread(SPREAD);

        // Get weapon position and angle
        Vector2f weaponLocation = weapon.getLocation();
        float weaponAngle = weapon.getCurrAngle();

        // Calculate backwards position
        Vector2f backwardsOffset = Misc.getUnitVectorAtDegreeAngle(weaponAngle + 180f);
        backwardsOffset.scale(BACKWARDS_OFFSET_DISTANCE);
        Vector2f backwardsPosition = Vector2f.add(weaponLocation, backwardsOffset, new Vector2f());

        // Spawn the backwards muzzle flash
        spawnMuzzleFlash(
                backwardsSpec,
                backwardsPosition,
                weaponAngle + 180f, // Point backwards
                ship.getVelocity(),
                1.5f, // Velocity multiplier
                15f   // Additional velocity
        );
    }

    /**
     * Spawns a muzzle flash effect at the specified location
     */
    public static void spawnMuzzleFlash(MuzzleFlashSpec spec, Vector2f point, float angle,
                                        Vector2f shipVel, float velMult, float velAdd) {
        if (spec == null) return;

        CombatEngineAPI engine = Global.getCombatEngine();

        Color color = spec.getParticleColor();
        float min = spec.getParticleSizeMin();
        float range = spec.getParticleSizeRange();
        float spread = spec.getSpread();
        float length = spec.getLength();

        for (int i = 0; i < spec.getParticleCount(); i++) {
            float size = range * (float) Math.random() + min;
            size *= SIZE_MULTIPLIER;

            float theta = (float) (Math.random() * Math.toRadians(spread) +
                    Math.toRadians(angle - spread/2f));
            float r = (float) (Math.random() * length);

            Vector2f dir = new Vector2f((float) Math.cos(theta), (float) Math.sin(theta));
            float x = dir.x * r;
            float y = dir.y * r;

            Vector2f loc = new Vector2f(point.x + x, point.y + y);
            Vector2f vel = new Vector2f(
                    x * velMult + shipVel.x + dir.x * velAdd,
                    y * velMult + shipVel.y + dir.y * velAdd
            );

            // Add some randomness to velocity
            Vector2f rand = Misc.getPointWithinRadius(new Vector2f(), length * 0.3f);
            Vector2f.add(vel, rand, vel);

            // Calculate particle duration with some variation
            float dur = spec.getParticleDuration();
            dur *= 0.8f + (float) Math.random() * 0.4f;

            // Spawn the particle
            engine.addNebulaParticle(
                    loc,
                    vel,
                    size,
                    0.5f, // end size multiplier
                    0f,   // ramp up fraction
                    0f,   // full brightness fraction
                    dur,
                    color
            );
        }
    }
}