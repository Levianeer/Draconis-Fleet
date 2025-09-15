package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.ProximityExplosionEffect;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class fsdf_SpearRange implements ProximityExplosionEffect {

    @Override
    public void onExplosion(DamagingProjectileAPI explosion, DamagingProjectileAPI originalProjectile) {

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        Vector2f loc = explosion.getLocation();

        engine.spawnExplosion(loc, new Vector2f(), Color.white, 90f, 0.6f);
        engine.spawnExplosion(loc, new Vector2f(), Color.white, 60f, 0.6f);
        engine.addNegativeNebulaParticle(loc, new Vector2f(), 30f, 2f, 0f, 0f, 0.5f, Color.white);
    }
}