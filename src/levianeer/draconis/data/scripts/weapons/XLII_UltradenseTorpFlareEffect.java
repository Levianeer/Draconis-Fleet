package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class XLII_UltradenseTorpFlareEffect
        implements OnFireEffectPlugin, EveryFrameWeaponEffectPlugin {

    // Must match "fadeTime" in .proj
    private static final float FADE_DURATION = 0.5f;

    private static final Color FLARE_STREAK = new Color(235, 45, 105, 65);
    private static final Color FLARE_CORE   = new Color(235, 205, 205, 155);

    private static class Entry {
        final DamagingProjectileAPI proj;
        float fadeElapsed = -1f; // negative = not yet fading
        boolean burstTriggered = false;

        Entry(DamagingProjectileAPI p) { proj = p; }

        void tick(float amount) {
            if (proj.isFading() && fadeElapsed < 0f) fadeElapsed = 0f;
            if (fadeElapsed >= 0f) fadeElapsed += amount;
        }

        float alphaMult() {
            if (fadeElapsed < 0f) return 1f;
            return Math.max(0f, 1f - fadeElapsed / FADE_DURATION);
        }

        boolean isExpired(CombatEngineAPI engine) {
            return burstTriggered
                    || proj.didDamage()
                    || fadeElapsed >= FADE_DURATION
                    || (!engine.isEntityInPlay(proj) && fadeElapsed < 0f);
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
            e.tick(amount);

            // Self-destruct at max range — runs after tick() sets fadeElapsed,
            // before isExpired() so we don't miss the window.
            if (!e.burstTriggered) {
                boolean fizzling = (e.proj instanceof MissileAPI) && ((MissileAPI) e.proj).isFizzling();
                boolean flightExpired = (e.proj instanceof MissileAPI m)
                        && m.getFlightTime() >= m.getMaxFlightTime();
                if ((e.proj.isFading() || flightExpired) && !e.proj.didDamage() && !fizzling) {
                    e.burstTriggered = true;
                    Vector2f loc = e.proj.getLocation();
                    new XLII_UltradenseNukeOnHitEffect().onHit(e.proj, null, loc, false, null, engine);
                    engine.removeEntity(e.proj);
                }
            }

            if (e.isExpired(engine)) {
                toRemove.add(e);
                continue;
            }

            float alpha = e.alphaMult();

            // Offset slightly behind the nose tip
            Vector2f pos = new Vector2f(-10f, 0f);
            VectorUtils.rotate(pos, e.proj.getFacing());
            Vector2f.add(pos, e.proj.getLocation(), pos);

            SpriteAPI streak = Global.getSettings().getSprite("fx", "XLII_torpedo_flare");
            SpriteAPI core   = Global.getSettings().getSprite("fx", "XLII_torpedo_flare");

            // Wide horizontal streak - main anamorphic flare
            MagicRender.singleframe(streak,
                    MathUtils.getRandomPointInCircle(pos, 1.5f),
                    new Vector2f(500f, 8f),
                    0f, withAlpha(FLARE_STREAK, alpha), true);

            // Shorter, brighter core
            MagicRender.singleframe(core,
                    MathUtils.getRandomPointInCircle(pos, 1.5f),
                    new Vector2f(220f, 5f),
                    0f, withAlpha(FLARE_CORE, alpha), true);
        }
        entries.removeAll(toRemove);
    }

    private static Color withAlpha(Color base, float alphaMult) {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(),
                Math.round(base.getAlpha() * alphaMult));
    }
}