package data.scripts.shipsystems;

import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.Misc;

public class fsdf_ECM_SuiteStats extends BaseShipSystemScript {

    private static final float DISABLE_RADIUS = 1000f;
    private static final float DISPLAY_DURATION = 8f;

    private float textTimer = 0f;
    private boolean textDisplayed = false;

    public static Color TEXT_COLOR = new Color(200, 200, 200);

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();

        if (state == State.ACTIVE) {
            if (!textDisplayed) {
                ship.getFluxTracker().showOverloadFloatyIfNeeded("Active Jamming!", TEXT_COLOR, 1f, true);
                textDisplayed = true;
            }

            textTimer += Global.getCombatEngine().getElapsedInLastFrame();
            if (textTimer >= DISPLAY_DURATION) {
                textDisplayed = false;
                textTimer = 0f;
            }
        }

        for (MissileAPI missile : Global.getCombatEngine().getMissiles()) {
            if (missile.getSource().getOwner() != ship.getOwner()) {
                float distance = Misc.getDistance(ship.getLocation(), missile.getLocation());
                if (distance <= DISABLE_RADIUS) {
                    missile.flameOut();
                    missile.setOwner(ship.getOwner());


                    spawnHitParticle(missile.getLocation());
                }
            }
        }
    }

    private void spawnHitParticle(Vector2f location) {

        float angle = 0f;

        Global.getCombatEngine().addHitParticle(
                location,
                new Vector2f((float) Math.cos(Math.toRadians(angle)), (float) Math.sin(Math.toRadians(angle))),
                10f,
                1f,
                0.1f,
                new Color(50,50,255,155)
        );
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("Jamming Enemy Missiles", false);
        }
        return null;
    }
}