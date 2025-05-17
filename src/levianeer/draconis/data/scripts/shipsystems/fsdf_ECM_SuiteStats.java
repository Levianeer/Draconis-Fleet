package levianeer.draconis.data.scripts.shipsystems;

import java.awt.Color;

import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.Misc;
import org.magiclib.util.MagicRender;

import static com.fs.starfarer.api.combat.CollisionClass.NONE;

public class fsdf_ECM_SuiteStats extends BaseShipSystemScript {

    private static final float DISABLE_RADIUS = 1000f;

    private boolean textDisplayed  = false;
    private boolean spriteRendered  = false;

    public static Color TEXT_COLOR = new Color(200, 200, 200);
    SpriteAPI jammer_ring = Global.getSettings().getSprite("fx", "fsdf_jammer_ring");

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null) return;

        // Calculate effective disable radius
        float effectiveDisableRadius = DISABLE_RADIUS;
        if (ship.isFighter()) {
            effectiveDisableRadius = DISABLE_RADIUS * 0.5f;
        }

        // When system is active, show the text and render the sprite
        if (state == State.ACTIVE) {
            if (!textDisplayed) {
                ship.getFluxTracker().showOverloadFloatyIfNeeded("Jamming!", TEXT_COLOR, 1f, true);
                textDisplayed = true;
            }

            if (!spriteRendered) {
                renderECMSprite(ship, effectiveDisableRadius);
                spriteRendered = true;
            }
        }

        // Missile disable logic
        for (MissileAPI missile : Global.getCombatEngine().getMissiles()) {
            if (missile.getSource() != null && missile.getSource().getOwner() != ship.getOwner()) {
                float distance = Misc.getDistance(ship.getLocation(), missile.getLocation());
                if (distance <= effectiveDisableRadius) {
                    missile.setDamageAmount(0);
                    missile.setOwner(100);
                    missile.setMissileAI(null);
                    missile.setCollisionClass(NONE);
                    missile.flameOut();
                    spawnHitParticle(missile.getLocation());
                }
            }
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        textDisplayed = false;
        spriteRendered = false;
    }

    private void renderECMSprite(ShipAPI ship, float radius) {
        float spriteSize = radius * 2f; // Diameter

        Vector2f size = new Vector2f(spriteSize + 100, spriteSize + 100);
        Vector2f growthNone = new Vector2f(0f, 0f); // no growth

        Color color = new Color(90, 165, 255, 55);

        MagicRender.objectspace(
                this.jammer_ring,
                ship,
                growthNone,
                new Vector2f(0f, 0f),
                size,
                growthNone,
                0f,
                0f,
                false,
                color,
                false,
                0.25f,
                0.5f,
                0.25f,
                true
        );
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