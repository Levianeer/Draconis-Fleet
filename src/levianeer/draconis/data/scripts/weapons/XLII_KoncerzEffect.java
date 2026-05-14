package levianeer.draconis.data.scripts.weapons;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

public class XLII_KoncerzEffect implements OnFireEffectPlugin, OnHitEffectPlugin {

    public static class ParticleData {
        public SpriteAPI sprite;
        public float time      = 0f;
        public float angle;
        public float maxDur;
        public float size;
        public float scale     = 1f;
        public float targetScale;
        public FaderUtil fader;
        public Vector2f posOffset = new Vector2f();

        public ParticleData(SpriteAPI sprite, float size, float targetScale, float angle, float maxDur) {
            this.sprite       = sprite;
            this.size         = size;
            this.targetScale  = targetScale;
            this.angle        = angle;
            this.maxDur       = maxDur;

            fader = new FaderUtil(1f, 0.01f, maxDur);
            fader.fadeOut();

            sprite.setTexWidth(1f);
            sprite.setTexHeight(1f);
            sprite.setTexX(0f);
            sprite.setTexY(0f);
            sprite.setAdditiveBlend();
        }

        public void advance(float amount) {
            time += amount;
            float progress = Math.min(time / maxDur, 1f);
            scale = 1f + (targetScale - 1f) * progress;
            fader.advance(amount);
        }

        public boolean isExpired() {
            return fader.getBrightness() <= 0.01f;
        }
    }

    /** A fixed-position snapshot of ring sprites left behind by the projectile as it travels. */
    private static class RingSnapshot {
        final Vector2f pos;
        final float facing;
        final List<ParticleData> particles = new ArrayList<>();

        private static final float BASE_SIZE    = 26f;
        private static final float TARGET_SCALE = 2.0f;
        private static final float DURATION     = 0.15f;
        private static final float SPACING_1      = 20f;
        private static final float SPACING_2      = 40f;

        RingSnapshot(Vector2f pos, float facing) {
            this.pos    = new Vector2f(pos);
            this.facing = facing;

            Vector2f forward = Misc.getUnitVectorAtDegreeAngle(facing);

            SpriteAPI ring1 = Global.getSettings().getSprite("fx", "XLII_sovnya_ring1");
            SpriteAPI ring2 = Global.getSettings().getSprite("fx", "XLII_sovnya_ring2");
            SpriteAPI ring3 = Global.getSettings().getSprite("fx", "XLII_sovnya_ring3");

            ParticleData p1 = new ParticleData(ring1, BASE_SIZE,         TARGET_SCALE,         0f, DURATION);
            ParticleData p2 = new ParticleData(ring2, BASE_SIZE * 0.75f, TARGET_SCALE * 1.15f, 0f, DURATION * 1.1f);
            ParticleData p3 = new ParticleData(ring3, BASE_SIZE * 0.85f, TARGET_SCALE * 1.25f, 0f, DURATION * 1.2f);

            // Space rings along the already-traveled path (behind snapshot position)
            p2.posOffset.set(-forward.x * SPACING_1, -forward.y * SPACING_1);
            p3.posOffset.set(-forward.x * SPACING_2, -forward.y * SPACING_2);

            particles.add(p1);
            particles.add(p2);
            particles.add(p3);
        }

        void advance(float amount) {
            for (ParticleData p : particles) p.advance(amount);
            particles.removeIf(ParticleData::isExpired);
        }

        boolean isExpired() { return particles.isEmpty(); }
    }

    private static class TrailPlugin extends BaseCombatLayeredRenderingPlugin {
        private final DamagingProjectileAPI proj;
        private final List<RingSnapshot> snapshots = new ArrayList<>();
        private final IntervalUtil spawnTimer = new IntervalUtil(0.06f, 0.06f);

        private static final Color CORE_COLOR   = new Color(245, 200, 230, 150);
        private static final Color FRINGE_COLOR = new Color(185, 75,  155,  40);
        private static final EnumSet<CombatEngineLayers> LAYERS =
                EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);

        TrailPlugin(DamagingProjectileAPI proj) {
            this.proj = proj;
            // Seed the first snapshot immediately at spawn position
            snapshots.add(new RingSnapshot(proj.getLocation(), proj.getFacing()));
        }

        @Override public void init(CombatEntityAPI entity) { super.init(entity); }
        @Override public float getRenderRadius() { return 700f; }
        @Override public EnumSet<CombatEngineLayers> getActiveLayers() { return LAYERS; }
        @Override public boolean isExpired() {
            return snapshots.isEmpty() &&
                   (proj.isExpired() || !Global.getCombatEngine().isEntityInPlay(proj));
        }

        @Override
        public void advance(float amount) {
            if (Global.getCombatEngine().isPaused()) return;

            if (!proj.isExpired() && Global.getCombatEngine().isEntityInPlay(proj) && !proj.didDamage()) {
                spawnTimer.advance(amount);
                if (spawnTimer.intervalElapsed()) {
                    RingSnapshot snap = new RingSnapshot(proj.getLocation(), proj.getFacing());
                    snap.advance(0.06f); // pre-advance to match spawn interval so new rings start at the same animation state as the previous ones
                    snapshots.add(snap);
                }
            }

            snapshots.removeIf(s -> { s.advance(amount); return s.isExpired(); });
        }

        @Override
        public void render(CombatEngineLayers layer, ViewportAPI viewport) {
            float alphaMult = viewport.getAlphaMult();
            for (RingSnapshot snap : snapshots) {
                for (ParticleData p : snap.particles) {
                    float x = snap.pos.x + p.posOffset.x;
                    float y = snap.pos.y + p.posOffset.y;
                    float currentSize = p.size * p.scale;
                    float alpha = p.fader.getBrightness() * alphaMult;

                    p.sprite.setAngle(p.angle + snap.facing - 90f);

                    p.sprite.setColor(CORE_COLOR);
                    p.sprite.setSize(currentSize, currentSize);
                    p.sprite.setCenter(currentSize * 0.5f, currentSize * 0.5f);
                    p.sprite.setAlphaMult(alpha);
                    p.sprite.renderAtCenter(x, y);

                    float glowSize = currentSize * 1.08f;
                    p.sprite.setColor(FRINGE_COLOR);
                    p.sprite.setSize(glowSize, glowSize);
                    p.sprite.setCenter(glowSize * 0.5f, glowSize * 0.5f);
                    p.sprite.setAlphaMult(alpha * 0.75f);
                    p.sprite.renderAtCenter(x, y);
                }
            }
        }
    }

    // -------------------------------------------------------------------------

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        TrailPlugin trail = new TrailPlugin(projectile);
        CombatEntityAPI e = engine.addLayeredRenderingPlugin(trail);
        e.getLocation().set(projectile.getLocation());
    }

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        Color color = projectile.getProjectileSpec().getFringeColor();
        color = Misc.setAlpha(color, 100);

        Vector2f vel = new Vector2f();
        if (target instanceof ShipAPI) {
            vel.set(target.getVelocity());
        }

        float sizeMult = Misc.getHitGlowSize(100f, projectile.getDamage().getBaseDamage(), damageResult) / 100f;

        for (int i = 0; i < 5; i++) {
            float size = 25f * (0.75f + (float) Math.random() * 0.5f);
            Color c = Misc.scaleAlpha(color, projectile.getBrightness());
            engine.addNebulaParticle(point, vel, size, 5f + 3f * sizeMult, 0f, 0f, 1f, c, true);
        }
    }
}