package levianeer.draconis.data.scripts.combat.commander;

import java.util.EnumSet;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * The ship layer for one hull. The task force gives it intent, not a slot - a minimum gap to keep from
 * its nearest threat and an anchor to stay near - and vanilla's ship AI flies it freely while it keeps
 * to that intent (choosing targets, dodging, keeping shields and guns on whatever is hitting it). This
 * steps in only when the intent is broken, and out of contact, where it moves the ship in formation.
 * Closing in on a focus target and pushing that target to the ship were tried and removed: ships were
 * dragged towards a target the rest of the group was not fighting, and it took 4-14% of our damage.
 * <p>
 * Vanilla's AI stays installed (it cannot be wrapped: it throws unless it is the ship's own AI) and
 * keeps weapons, shields and systems. The engine drops every queued command whose type is blocked for
 * the frame, ours included, so steering blocks only the commands we do not want and issues the ones we
 * do. See {@code draconis-commander.md}, Phase 1 and the Phase 2 restructure.
 */
final class ShipControl {

    /** What the ship is doing this frame, for the log. */
    enum Action { FREE, SLOT, BACK_OFF, REGROUP }

    private static final ShipCommand[] MOVEMENT = {
            ShipCommand.TURN_LEFT, ShipCommand.TURN_RIGHT, ShipCommand.STRAFE_LEFT, ShipCommand.STRAFE_RIGHT,
            ShipCommand.ACCELERATE, ShipCommand.ACCELERATE_BACKWARDS, ShipCommand.DECELERATE,
    };
    private static final float ARRIVE_RADIUS = 150f;
    private static final float FACING_TOLERANCE = 3f;
    /** Velocity error, in units/s, below which no thrust is applied on that axis. */
    private static final float THRUST_DEADBAND = 15f;
    /** Steer to this much beyond a band edge, so the ship does not hover on it. */
    private static final float BAND_OVERSHOOT = 150f;
    /** Strayed this far beyond the cohesion radius, the ship also loses its facing to us. */
    private static final float REGROUP_TAKEOVER = 1.5f;

    final ShipAPI ship;

    // Intent, set by the task force every decision.
    /** False = no enemy anywhere near: move in formation to {@link #slot}. */
    boolean inContact;
    Vector2f slot;
    float slotFacing;
    /** Nearest enemy, and the smallest edge-to-edge gap to keep from it (0 = none). */
    ShipAPI threat;
    float minGap;
    Vector2f anchor;
    float cohesion;
    /** Vent now, because nothing can reach the ship. Vanilla still vents on its own judgement too. */
    boolean ventAllowed;

    Action action = Action.FREE;
    int vents;

    ShipControl(ShipAPI ship) {
        this.ship = ship;
    }

    /** Every frame, from live positions. */
    void advance() {
        if (ship.isRetreating()) return;
        action = decideAction();
        switch (action) {
            case SLOT:
                steer(slot, slotFacing, true);
                break;
            case BACK_OFF: {
                // Back out of the threat's reach facing it - turning away gets a ship shot in the back.
                float away = Misc.getAngleInDegrees(threat.getLocation(), ship.getLocation());
                Vector2f p = Misc.getUnitVectorAtDegreeAngle(away);
                p.scale(minGap + BAND_OVERSHOOT + ship.getCollisionRadius() + threat.getCollisionRadius());
                Vector2f.add(threat.getLocation(), p, p);
                steer(p, Misc.getAngleInDegrees(ship.getLocation(), threat.getLocation()), true);
                break;
            }
            case REGROUP: {
                float dist = Misc.getDistance(ship.getLocation(), anchor);
                float facing = threat != null ? Misc.getAngleInDegrees(ship.getLocation(), threat.getLocation())
                        : Misc.getAngleInDegrees(ship.getLocation(), anchor);
                steer(anchor, facing, dist > cohesion * REGROUP_TAKEOVER);
                break;
            }
            case FREE:
                break;
        }
        if (ventAllowed && !ship.getFluxTracker().isOverloadedOrVenting()) {
            ship.giveCommand(ShipCommand.VENT_FLUX, null, 0);
            vents++;
            ventAllowed = false;
        }
    }

    /** Danger first, then cohesion; otherwise vanilla flies the ship. */
    private Action decideAction() {
        if (!inContact) return slot == null ? Action.FREE : Action.SLOT;
        if (threat != null && threat.isAlive() && minGap > 0f && gap(threat) < minGap) return Action.BACK_OFF;
        if (anchor != null && Misc.getDistance(ship.getLocation(), anchor) > cohesion) return Action.REGROUP;
        return Action.FREE;
    }

    private float gap(ShipAPI other) {
        return MathUtils.getDistance(ship, other);
    }

    /** Arrive at {@code point}; take facing too only when {@code turn}. */
    private void steer(Vector2f point, float facing, boolean turn) {
        EnumSet<ShipCommand> want = EnumSet.noneOf(ShipCommand.class);
        Vector2f loc = ship.getLocation();
        Vector2f vel = ship.getVelocity();

        if (turn) {
            float diff = MathUtils.getShortestRotation(ship.getFacing(), facing);
            float w = ship.getAngularVelocity();
            float stopAngle = w * w / (2f * Math.max(1f, ship.getTurnDeceleration()));
            if (Math.abs(diff) > FACING_TOLERANCE || Math.abs(w) > 5f) {
                boolean braking = Math.signum(w) == Math.signum(diff) && stopAngle >= Math.abs(diff);
                boolean left = braking ? w < 0f : diff > 0f;
                want.add(left ? ShipCommand.TURN_LEFT : ShipCommand.TURN_RIGHT);
            }
        }

        float dx = point.x - loc.x;
        float dy = point.y - loc.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float speed = Math.min(ship.getMaxSpeed(),
                (float) Math.sqrt(2f * Math.max(1f, ship.getDeceleration()) * Math.max(0f, dist - ARRIVE_RADIUS)));
        float wantX = dist > 1f ? dx / dist * speed : 0f;
        float wantY = dist > 1f ? dy / dist * speed : 0f;
        float errX = wantX - vel.x;
        float errY = wantY - vel.y;
        double rad = Math.toRadians(ship.getFacing());
        float fx = (float) Math.cos(rad);
        float fy = (float) Math.sin(rad);
        float forward = errX * fx + errY * fy;
        float left = -errX * fy + errY * fx;
        if (forward > THRUST_DEADBAND) want.add(ShipCommand.ACCELERATE);
        else if (forward < -THRUST_DEADBAND) want.add(ShipCommand.ACCELERATE_BACKWARDS);
        if (left > THRUST_DEADBAND) want.add(ShipCommand.STRAFE_LEFT);
        else if (left < -THRUST_DEADBAND) want.add(ShipCommand.STRAFE_RIGHT);
        if (!want.contains(ShipCommand.ACCELERATE) && !want.contains(ShipCommand.ACCELERATE_BACKWARDS)
                && !want.contains(ShipCommand.STRAFE_LEFT) && !want.contains(ShipCommand.STRAFE_RIGHT)
                && vel.length() > THRUST_DEADBAND) {
            want.add(ShipCommand.DECELERATE);
        }

        for (ShipCommand command : MOVEMENT) {
            if (!turn && (command == ShipCommand.TURN_LEFT || command == ShipCommand.TURN_RIGHT)) continue;
            if (want.contains(command)) ship.giveCommand(command, null, 0);
            else ship.blockCommandForOneFrame(command);
        }
    }
}
