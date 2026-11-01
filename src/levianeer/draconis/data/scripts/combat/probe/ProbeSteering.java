package levianeer.draconis.data.scripts.combat.probe;

import java.util.EnumSet;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * Phase 1 probe 1-2 (split control, steering): takes over one ship's movement while vanilla's ship AI
 * stays installed and keeps weapons, shields and systems. Removable once the probe is answered.
 * <p>
 * Vanilla's AI cannot be wrapped: {@code BasicShipAI.advance} throws unless it is the ship's own AI
 * (directly or as the only thing inside the engine's wrapper). So this runs beside it, from a combat
 * plugin. The engine drops every queued command whose type is blocked for the frame, our own
 * included, so movement is taken over by blocking only the movement commands we do not want and
 * issuing the ones we do; vanilla's duplicates of ours collapse into one. See
 * {@code draconis-commander.md}, Phase 1.
 */
class ProbeSteering {

    private static final ShipCommand[] MOVEMENT = {
            ShipCommand.TURN_LEFT, ShipCommand.TURN_RIGHT, ShipCommand.STRAFE_LEFT, ShipCommand.STRAFE_RIGHT,
            ShipCommand.ACCELERATE, ShipCommand.ACCELERATE_BACKWARDS, ShipCommand.DECELERATE,
    };
    /** Close enough to the point to stop pushing towards it. */
    private static final float ARRIVE_RADIUS = 150f;
    private static final float FACING_TOLERANCE = 3f;
    /** Velocity error, in units/s, below which no thrust is applied on that axis. */
    private static final float THRUST_DEADBAND = 15f;

    private final ShipAPI ship;
    /** Where to be and which way to face; null point = vanilla moves the ship. */
    Vector2f point;
    float facing;
    long steerNanos;
    long steerFrames;

    ProbeSteering(ShipAPI ship) {
        this.ship = ship;
    }

    /** Every frame. */
    void advance() {
        if (point == null || ship.isRetreating()) return;
        long start = System.nanoTime();
        steer();
        steerNanos += System.nanoTime() - start;
        steerFrames++;
    }

    private void steer() {
        EnumSet<ShipCommand> want = EnumSet.noneOf(ShipCommand.class);

        // Rotation: turn towards the wanted facing, braking early enough not to overshoot.
        float diff = MathUtils.getShortestRotation(ship.getFacing(), facing);
        float w = ship.getAngularVelocity();
        float stopAngle = w * w / (2f * Math.max(1f, ship.getTurnDeceleration()));
        if (Math.abs(diff) > FACING_TOLERANCE || Math.abs(w) > 5f) {
            boolean braking = Math.signum(w) == Math.signum(diff) && stopAngle >= Math.abs(diff);
            boolean left = braking ? w < 0f : diff > 0f;
            want.add(left ? ShipCommand.TURN_LEFT : ShipCommand.TURN_RIGHT);
        }

        // Translation: arrive at the point, slowing so as to stop on it.
        Vector2f loc = ship.getLocation();
        Vector2f vel = ship.getVelocity();
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
            if (want.contains(command)) ship.giveCommand(command, null, 0);
            else ship.blockCommandForOneFrame(command);
        }
    }
}
