package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class XLII_SwordbreakerOnHitEffect
        implements OnHitEffectPlugin, OnFireEffectPlugin, EveryFrameWeaponEffectPlugin {

    // Small conventional blast styled after XLII_EMPBlastOnHitEffect's layered blob recipe,
    // scaled down and re-themed orange/white for this PD frag missile; sizes doubled from that recipe.
    private static final Color COLOR_FRINGE = new Color(255, 140, 50, 190);
    private static final Color COLOR_CORE   = new Color(255, 220, 160, 210);

    // Self-destruct when the shot expires unspent; styled after XLII_LargeTorpSelfDestructEffect
    // (Hankyu torpedo), scaled to Swordbreaker's direct-hit damage.
    private static class Entry {
        final DamagingProjectileAPI proj;
        boolean burstTriggered = false;

        Entry(DamagingProjectileAPI p) { proj = p; }

        boolean isExpired(CombatEngineAPI engine) {
            if (proj.didDamage() || burstTriggered) return true;
            // Stays alive while fading so advance()'s burst check still gets a chance to fire this frame.
            if (!engine.isEntityInPlay(proj) && !proj.isFading()) return true;
            return false;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target,
                      Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        spawnVisuals(point, engine);
    }

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        entries.add(new Entry(projectile));
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine.isPaused() || entries.isEmpty()) return;

        List<Entry> toRemove = new ArrayList<>();
        for (Entry e : entries) {
            // Runs before the expiry check so we don't miss the frame isFading()/!isEntityInPlay() first go true together.
            if (!e.burstTriggered) {
                boolean fizzling = (e.proj instanceof MissileAPI) && ((MissileAPI) e.proj).isFizzling();
                boolean flightExpired = (e.proj instanceof MissileAPI m)
                        && m.getFlightTime() >= m.getMaxFlightTime();
                if ((e.proj.isFading() || flightExpired) && !e.proj.didDamage() && !fizzling) {
                    e.burstTriggered = true;
                    Vector2f loc = e.proj.getLocation();

                    spawnVisuals(loc, engine);

                    DamagingExplosionSpec spec = new DamagingExplosionSpec(
                            0.1f, 90f, 36f, 375f, 0f,
                            CollisionClass.HITS_SHIPS_AND_ASTEROIDS,
                            CollisionClass.HITS_SHIPS_AND_ASTEROIDS,
                            9f, 9f, 0.4f, 6,
                            COLOR_FRINGE,
                            COLOR_CORE
                    );
                    spec.setDamageType(DamageType.FRAGMENTATION);
                    spec.setShowGraphic(false);
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

    public static void spawnVisuals(Vector2f point, CombatEngineAPI engine) {
        SpriteAPI spr = Global.getSettings().getSprite("fx", "XLII_explosion");
        float angle = 360f * (float) Math.random();

        // Soft growing blob.
        MagicRender.battlespace(spr, point, new Vector2f(), new Vector2f(24, 24), new Vector2f(180, 180),
                angle, 0, COLOR_FRINGE, false, 0, 0.05f, 0.1f);
        // Denser, contracting core.
        MagicRender.battlespace(spr, point, new Vector2f(), new Vector2f(36, 36), new Vector2f(-24, -24),
                angle, 0, COLOR_CORE, false, 0.03f, 0.08f, 0.25f);

        engine.addHitParticle(point, new Vector2f(), 60f, 0.5f, 0.4f, COLOR_FRINGE);
        engine.addSmoothParticle(point, new Vector2f(), 75f, 1.5f, 0.15f, Color.white);
    }
}
