package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class XLII_LargeTorpSelfDestructEffect
        implements OnFireEffectPlugin, EveryFrameWeaponEffectPlugin {

    private static class Entry {
        final DamagingProjectileAPI proj;
        boolean burstTriggered = false;

        Entry(DamagingProjectileAPI p) { proj = p; }

        boolean isExpired(CombatEngineAPI engine) {
            if (proj.didDamage() || burstTriggered) return true;
            // Keep alive while fading so the burst check gets a chance to fire
            // on the same frame isFading/flightExpired becomes true.
            if (!engine.isEntityInPlay(proj) && !proj.isFading()) return true;
            return false;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        entries.add(new Entry(projectile));
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine.isPaused() || entries.isEmpty()) return;

        List<Entry> toRemove = new ArrayList<>();
        for (Entry e : entries) {
            // Burst check runs BEFORE expiry so we don't miss the frame where
            // isFading() and !isEntityInPlay() first become true simultaneously.
            if (!e.burstTriggered) {
                boolean fizzling = (e.proj instanceof MissileAPI) && ((MissileAPI) e.proj).isFizzling();
                boolean flightExpired = (e.proj instanceof MissileAPI m)
                        && m.getFlightTime() >= m.getMaxFlightTime();
                if ((e.proj.isFading() || flightExpired) && !e.proj.didDamage() && !fizzling) {
                    e.burstTriggered = true;
                    Vector2f loc = e.proj.getLocation();

                    XLII_LargeTorpOnHitEffect.spawnVisuals(loc, engine);

                    DamagingExplosionSpec spec = new DamagingExplosionSpec(
                            0.1f, 250f, 100f, 4000f, 0f,
                            CollisionClass.HITS_SHIPS_AND_ASTEROIDS,
                            CollisionClass.HITS_SHIPS_AND_ASTEROIDS,
                            8f, 8f, 0.5f, 12,
                            new Color(255, 128, 75, 200),
                            new Color(255, 100, 50, 255)
                    );
                    spec.setDamageType(DamageType.FRAGMENTATION);
                    spec.setShowGraphic(true);
                    engine.spawnDamagingExplosion(spec, e.proj.getSource(), loc);
                    engine.removeEntity(e.proj);
                }
            }

            if (e.isExpired(engine)) {
                toRemove.add(e);
            }
        }
        entries.removeAll(toRemove);
    }
}