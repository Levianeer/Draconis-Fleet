package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.HashMap;
import java.util.HashSet;

public class XLII_EvasionProtocolStats extends BaseShipSystemScript {

    private static final Logger log = Global.getLogger(XLII_EvasionProtocolStats.class);

    // ==================== TUNING PARAMETERS ====================

    private static final float VELOCITY = 200f;
    private static final float DELTA = 2000f;

    /** Half-width of the forward arc searched for ramming targets, in degrees. */
    private static final float RAM_ARC_DEGREES = 25f;
    /** Maximum range at which a ramming target will be selected. */
    private static final float RAM_RANGE = 700f;
    /** Duration used when refreshing AI flags each frame (prevents instant expiry). */
    private static final float AI_FLAG_REFRESH_DURATION = 0.5f;

    // Per-combat state (all cleared when a new combat engine is detected)
    /** Ships that have already had their flare burst scheduled this activation. */
    private static final HashSet<String> burstScheduled = new HashSet<>();
    private static final HashMap<String, Float> originalMass = new HashMap<>();
    private static CombatEngineAPI lastEngine_EvasionProtocol;

    private static void checkClearState() {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine != lastEngine_EvasionProtocol) {
            lastEngine_EvasionProtocol = engine;
            burstScheduled.clear();
            originalMass.clear();
        }
    }

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        checkClearState();

        ShipAPI ship = (stats.getEntity() instanceof ShipAPI) ? (ShipAPI) stats.getEntity() : null;
        if (ship == null) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        String shipId = ship.getId();

        if (state == ShipSystemStatsScript.State.OUT) {
            // Restore original mass and clean up
            Float orig = originalMass.get(shipId);
            if (orig != null) {
                ship.setMass(orig);
                originalMass.remove(shipId);
            }

            stats.getMaxSpeed().unmodify(id);
            stats.getAcceleration().unmodify(id);
            stats.getDeceleration().unmodify(id);

            // Allow the next activation to schedule a fresh burst
            burstScheduled.remove(shipId);
        } else {
            // Store the original mass once per activation
            if (!originalMass.containsKey(shipId)) {
                originalMass.put(shipId, ship.getMass());
            }

            // Double the mass, scaling with effectLevel for a smooth ramp-in
            // effectLevel 0→1 gives mass = original * 1x→2x
            ship.setMass(originalMass.get(shipId) * (1f + effectLevel));

            // Apply movement bonuses
            stats.getMaxSpeed().modifyFlat(id, VELOCITY * effectLevel);
            stats.getAcceleration().modifyFlat(id, DELTA * effectLevel);
            stats.getDeceleration().modifyFlat(id, DELTA * effectLevel);

            // Schedule the burst on the first ACTIVE frame.
            // Each shot is a separate engine plugin so it fires at the right time
            // regardless of what state the system transitions to afterwards.
            if (state == ShipSystemStatsScript.State.ACTIVE
                    && !engine.isPaused()
                    && !burstScheduled.contains(shipId)) {

                burstScheduled.add(shipId);
                int burstSize = XLII_DelayedFlareShot.getBurstSize();

                for (int i = 0; i < burstSize; i++) {
                    engine.addPlugin(new XLII_DelayedFlareShot(ship, i * XLII_DelayedFlareShot.BURST_DELAY, i, burstSize));
                }

                log.debug("Draconis: Evasion Protocol - Scheduled " + burstSize + " flare shots for "
                        + ship.getHullSpec().getHullName());
            }

            // Encourage AI ships to ram the nearest enemy in their forward arc.
            // Flags are set with a short duration so they expire cleanly if the
            // system deactivates between apply() calls.
            if (ship.getShipAI() != null) {
                ShipwideAIFlags aiFlags = ship.getAIFlags();
                if (aiFlags != null) {
                    ShipAPI ramTarget = findNearestEnemyInForwardArc(ship, engine);
                    // Only commit to a ram if the target is meaningfully lighter than the
                    // doubled ship — target must be at least 25% lighter than current mass
                    if (ramTarget != null && ramTarget.getMass() < ship.getMass() * 0.75f) {
                        aiFlags.setFlag(ShipwideAIFlags.AIFlags.DO_NOT_BACK_OFF, AI_FLAG_REFRESH_DURATION);
                        aiFlags.setFlag(ShipwideAIFlags.AIFlags.MANEUVER_TARGET, AI_FLAG_REFRESH_DURATION, ramTarget);
                        aiFlags.setFlag(ShipwideAIFlags.AIFlags.HARASS_MOVE_IN, AI_FLAG_REFRESH_DURATION);
                    }
                }
            }
        }
    }

    public void unapply(MutableShipStatsAPI stats, String id) {
        // Safety net: restores mass if apply(OUT) was never called (e.g. combat ends mid-activation)
        ShipAPI ship = (stats.getEntity() instanceof ShipAPI) ? (ShipAPI) stats.getEntity() : null;
        if (ship != null) {
            String shipId = ship.getId();
            Float orig = originalMass.get(shipId);
            if (orig != null) {
                ship.setMass(orig);
                originalMass.remove(shipId);
            }
            burstScheduled.remove(shipId);
        }
        stats.getMaxSpeed().unmodify(id);
        stats.getAcceleration().unmodify(id);
        stats.getDeceleration().unmodify(id);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        return switch (index) {
            case 0 -> new StatusData("increased RCS power", false);
            case 1 -> new StatusData("deploying countermeasures", false);
            default -> null;
        };
    }

    // ==================== RAMMING AI HELPERS ====================

    /**
     * Finds the nearest enemy ship within a forward-facing arc.
     * Fighters, drones, hulks, and shuttle pods are excluded.
     *
     * @param ship   The ship using the system
     * @param engine Combat engine for iterating ships
     * @return The closest qualifying enemy in the arc, or null if none found
     */
    private ShipAPI findNearestEnemyInForwardArc(ShipAPI ship, CombatEngineAPI engine) {
        Vector2f shipLoc = ship.getLocation();
        float shipFacing = ship.getFacing();
        ShipAPI nearest = null;
        float nearestDist = Float.MAX_VALUE;

        for (ShipAPI candidate : engine.getShips()) {
            if (candidate.getOwner() == ship.getOwner()) continue;
            if (candidate.isHulk() || candidate.isShuttlePod() || candidate.isDrone() || candidate.isFighter()) continue;

            float dist = MathUtils.getDistance(shipLoc, candidate.getLocation());
            if (dist > XLII_EvasionProtocolStats.RAM_RANGE) continue;

            float angleToTarget = VectorUtils.getAngle(shipLoc, candidate.getLocation());
            float angleDiff = Math.abs(MathUtils.getShortestRotation(shipFacing, angleToTarget));

            if (angleDiff <= XLII_EvasionProtocolStats.RAM_ARC_DEGREES && dist < nearestDist) {
                nearest = candidate;
                nearestDist = dist;
            }
        }
        return nearest;
    }
}