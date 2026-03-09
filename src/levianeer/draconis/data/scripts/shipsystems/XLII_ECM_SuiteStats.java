package levianeer.draconis.data.scripts.shipsystems;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.Misc;
import org.magiclib.util.MagicRender;

import static com.fs.starfarer.api.combat.CollisionClass.NONE;

public class XLII_ECM_SuiteStats extends BaseShipSystemScript {

    private static final float DISABLE_RADIUS = 1000f;

    private static final float ROTATION_SPEED = 5f; // degrees per second
    private static final float SPRITE_ALIGNMENT_SCALE = 512f / 448f;
    private static final Color RING_COLOR = new Color(90, 165, 255, 55);

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

        // Ring FX: effectLevel scales 0->1 on IN and 1->0 on OUT for grow/shrink animation
        if (effectLevel > 0f) {
            float angle = Global.getCombatEngine().getTotalElapsedTime(false) * ROTATION_SPEED;
            float scaledSize = effectiveDisableRadius * 2f * SPRITE_ALIGNMENT_SCALE * effectLevel;
            SpriteAPI ringSprite = Global.getSettings().getSprite("fx", "XLII_jammer_ring");
            MagicRender.singleframe(
                    ringSprite,
                    ship.getLocation(),
                    new Vector2f(scaledSize, scaledSize),
                    angle,
                    RING_COLOR,
                    true
            );
        }

        // Missile disable logic — use spatial query instead of iterating all missiles
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