package levianeer.draconis.data.scripts.shipsystems;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI.ShipEngineAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

public class XLII_ShuntDriveStats extends BaseShipSystemScript {

    public static final float SPEED_BONUS = 225f;
    public static final float TURN_BONUS = 30f;

    private final Color engineColor = new Color(225, 105, 50, 70);
    private final Color particleColor = new Color(235, 105, 5, 5);
    private final IntervalUtil particleInterval = new IntervalUtil(0.35f, 0.45f);

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (!(stats.getEntity() instanceof ShipAPI ship)) return;

        // Stat mod
        if (state == ShipSystemStatsScript.State.OUT) {
            stats.getMaxSpeed().unmodify(id);
            stats.getMaxTurnRate().unmodify(id);

            // Velocity damping
            if (ship.getVelocity().x >= 200 || ship.getVelocity().y >= 200 ||
                    ship.getVelocity().x <= -200 || ship.getVelocity().y <= -200) {
                ship.getVelocity().set(ship.getVelocity().x * 0.99f, ship.getVelocity().y * 0.999f);
            }
        } else {
            stats.getMaxSpeed().modifyFlat(id, SPEED_BONUS);
            stats.getAcceleration().modifyPercent(id, 500f * effectLevel);
            stats.getTurnAcceleration().modifyFlat(id, TURN_BONUS * effectLevel);
            stats.getTurnAcceleration().modifyPercent(id, 100f * effectLevel);
            stats.getMaxTurnRate().modifyFlat(id, 15f);
            stats.getMaxTurnRate().modifyPercent(id, 200f);
        }

        // Engine visuals
        ship.getEngineController().fadeToOtherColor(this, engineColor, new Color(0, 0, 0, 0), effectLevel, 0.67f);
        ship.getEngineController().extendFlame(this, 2f * effectLevel, 1f, 1f);

        // Exhaust particles
        spawnExhaustParticles(ship, effectLevel);
    }

    private void spawnExhaustParticles(ShipAPI ship, float effectLevel) {
        if (effectLevel < 0.5f) return;

        float timeMult = Global.getCombatEngine().getTimeMult().getMult();
        if (timeMult == 0) timeMult = 0.01f;
        particleInterval.advance(0.05f * timeMult);

        if (!particleInterval.intervalElapsed()) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        Vector2f shipVel = ship.getVelocity();

        for (ShipEngineAPI shipEngine : ship.getEngineController().getShipEngines()) {
            if (shipEngine.isDisabled()) continue;

            Vector2f loc = shipEngine.getLocation();
            float angle = shipEngine.getEngineSlot().getAngle() + ship.getFacing();

            float min = 50;
            float range = 120;
            float spread = 15;
            float length = 155;
            for (int i = 0; i < 20; i++) {
                float size = range * (float) Math.random() + min;
                float theta = (float) (Math.random() * Math.toRadians(spread) + Math.toRadians(angle - spread / 2f));
                float r = (float) (Math.random() * length);
                Vector2f dir = new Vector2f((float) Math.cos(theta), (float) Math.sin(theta));
                float x = dir.x * r;
                float y = dir.y * r;
                Vector2f particleLoc = new Vector2f(loc.x + x, loc.y + y);
                Vector2f vel = new Vector2f(x * 2f + shipVel.x + dir.x,
                        y * 2f + shipVel.y + dir.y);
                Vector2f rand = Misc.getPointWithinRadius(new Vector2f(), length * 0.5f);
                Vector2f.add(vel, rand, vel);
                engine.addNebulaParticle(particleLoc, vel, size, 1f, 0.1f, 0f, 3.25f, particleColor);
            }
        }
    }

    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().unmodify(id);
        stats.getMaxTurnRate().unmodify(id);
        stats.getTurnAcceleration().unmodify(id);
        stats.getAcceleration().unmodify(id);
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("improved maneuverability", false);
        } else if (index == 1) {
            return new StatusData("+" + (int) SPEED_BONUS + " top speed", false);
        }
        return null;
    }
}