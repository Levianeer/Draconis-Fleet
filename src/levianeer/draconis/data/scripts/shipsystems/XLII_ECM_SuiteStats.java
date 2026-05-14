package levianeer.draconis.data.scripts.shipsystems;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

import static com.fs.starfarer.api.combat.CollisionClass.NONE;

public class XLII_ECM_SuiteStats extends BaseShipSystemScript {

    private static final float DISABLE_RADIUS = 1000f;

    private static final float ROTATION_SPEED = 5f; // degrees per second
    private static final float SPRITE_ALIGNMENT_SCALE = 512f / 448f; // ring art is smaller than PNG bounds
    private static final Color RING_COLOR = new Color(90, 165, 255, 55);

    private SpriteAPI ringSprite = null;
    private boolean textDisplayed = false;

    public static Color TEXT_COLOR = new Color(200, 200, 200);

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null) return;

        // Calculate effective disable radius
        float effectiveDisableRadius = DISABLE_RADIUS;
        if (ship.isFighter()) {
            effectiveDisableRadius = DISABLE_RADIUS * 0.25f;
        }

        // Show activation text once when the system becomes fully active
        if (state == State.ACTIVE && !textDisplayed) {
            ship.getFluxTracker().showOverloadFloatyIfNeeded("Jamming!", TEXT_COLOR, 1f, true);
            textDisplayed = true;
        }

        // Ring FX: rendered in screen space so it's pixel-perfect at any zoom level.
        // effectLevel fades the ring in on IN and out on OUT.
        if (effectLevel > 0f) {
            CombatEngineAPI engine = Global.getCombatEngine();
            ViewportAPI view = engine.getViewport();
            Vector2f loc = ship.getLocation();
            if (view.isNearViewport(loc, effectiveDisableRadius)) {
                float screenScale = Global.getSettings().getScreenScaleMult();
                float radius = (effectiveDisableRadius + ship.getCollisionRadius()) * 2f * SPRITE_ALIGNMENT_SCALE * screenScale * effectLevel / view.getViewMult();
                float angle = engine.getTotalElapsedTime(false) * ROTATION_SPEED;
                if (ringSprite == null) ringSprite = Global.getSettings().getSprite("fx", "XLII_jammer_ring");
                ringSprite.setSize(radius, radius);
                ringSprite.setColor(RING_COLOR);
                ringSprite.setAdditiveBlend();
                GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPushMatrix();
                GL11.glViewport(0, 0, Display.getWidth(), Display.getHeight());
                GL11.glOrtho(0, Display.getWidth(), 0, Display.getHeight(), -1, 1);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glEnable(GL11.GL_BLEND);
                ringSprite.setAngle(angle);
                ringSprite.renderAtCenter(
                        view.convertWorldXtoScreenX(loc.x) * screenScale,
                        view.convertWorldYtoScreenY(loc.y) * screenScale
                );
                GL11.glPopMatrix();
                GL11.glPopAttrib();
            }
        }

        // Missile disable logic - use spatial query instead of iterating all missiles
        List<MissileAPI> nearbyMissiles = CombatUtils.getMissilesWithinRange(ship.getLocation(), effectiveDisableRadius);
        for (MissileAPI missile : nearbyMissiles) {
            if (missile.getSource() != null && missile.getSource().getOwner() != ship.getOwner()) {
                missile.setDamageAmount(0);
                missile.setOwner(100);
                missile.setMissileAI(null);
                missile.setCollisionClass(NONE);
                missile.flameOut();
                spawnHitParticle(missile.getLocation());
            }
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        textDisplayed = false;
    }

    private void spawnHitParticle(Vector2f location) {
        // Spawn hit particles to indicate missile disabling
        float angle = 0f;

        Global.getCombatEngine().addHitParticle(
                location,
                new Vector2f((float) Math.cos(Math.toRadians(angle)), (float) Math.sin(Math.toRadians(angle))),
                10f,
                1f,
                0.1f,
                new Color(50, 50, 255, 155)
        );
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("Jamming Enemy Missiles!", false);
        }
        return null;
    }
}