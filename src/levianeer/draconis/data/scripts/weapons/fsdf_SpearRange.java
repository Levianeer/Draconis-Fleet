package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.ProximityExplosionEffect;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class fsdf_SpearRange implements ProximityExplosionEffect {

    private static final Vector2f ZERO_VELOCITY = new Vector2f(0f, 0f);
    private static final Color WHITE_COLOR = Color.WHITE;

    private static final float LARGE_EXPLOSION_SIZE = 90f;
    private static final float SMALL_EXPLOSION_SIZE = 60f;
    private static final float EXPLOSION_DURATION = 0.6f;
    private static final float PARTICLE_SIZE = 30f;
    private static final float PARTICLE_DURATION = 2f;
    private static final float PARTICLE_OPACITY = 0.5f;

    @Override
    public void onExplosion(DamagingProjectileAPI explosion, DamagingProjectileAPI originalProjectile) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        Vector2f loc = explosion.getLocation();

        // Spawn explosions
        engine.spawnExplosion(loc, ZERO_VELOCITY, WHITE_COLOR, LARGE_EXPLOSION_SIZE, EXPLOSION_DURATION);
        engine.spawnExplosion(loc, ZERO_VELOCITY, WHITE_COLOR, SMALL_EXPLOSION_SIZE, EXPLOSION_DURATION);

        // Particle FX
        engine.addNegativeNebulaParticle(loc, ZERO_VELOCITY, PARTICLE_SIZE, PARTICLE_DURATION,
                0f, 0f, PARTICLE_OPACITY, WHITE_COLOR);
    }
}