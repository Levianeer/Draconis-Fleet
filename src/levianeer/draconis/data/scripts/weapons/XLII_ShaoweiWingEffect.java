// Wing animation script for XLII_shaowei
// Implements dynamic wing rotation based on engine inputs
package levianeer.draconis.data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

import java.util.List;

/**
 * Dynamic wing animation for the Shaowei destroyer.
 * Wings spread outward when accelerating forward and angle inward when decelerating/reversing,
 * based on engine controller states (player input).
 * <p>
 * Inspired by Tartiflette's Diable Avionics Versant implementation
 */
public class XLII_ShaoweiWingEffect implements EveryFrameWeaponEffectPlugin {

    private static final Logger log = Global.getLogger(XLII_ShaoweiWingEffect.class);

    // Weapon slot IDs from ship file
    private static final String LEFT_WING_SLOT_ID = "XLII_WING_LEFT";
    private static final String RIGHT_WING_SLOT_ID = "XLII_WING_RIGHT";

    // Animation parameters
    private static final float MAX_WING_ANGLE = 15f;  // Maximum rotation from center (respects 30° arc)
    private static final float ROTATION_SPEED = 0.125f;   // Degrees per frame for smooth interpolation

    // Cached references
    private WeaponAPI leftWing;
    private WeaponAPI rightWing;
    private ShipAPI ship;
    private ShipEngineControllerAPI engines;

    // Animation state
    private float currentRotateLeft = 0f;
    private float currentRotateRight = 0f;
    private boolean initialized = false;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

        // Pause check
        if (Global.getCombatEngine().isPaused()) {
            return;
        }

        // Initialize on first frame
        if (!initialized || ship == null || engines == null) {
            ship = weapon.getShip();
            engines = ship.getEngineController();

            // Find wing weapons by slot ID
            List<WeaponAPI> weapons = ship.getAllWeapons();
            for (WeaponAPI w : weapons) {
                String slotId = w.getSlot().getId();
                if (LEFT_WING_SLOT_ID.equals(slotId)) {
                    leftWing = w;
                } else if (RIGHT_WING_SLOT_ID.equals(slotId)) {
                    rightWing = w;
                }
            }

            // Verify both wings were found
            if (leftWing == null || rightWing == null) {
                log.warn("Draconis: XLII_ShaoweiWingEffect could not find wing weapons. Left: " +
                        (leftWing != null) + ", Right: " + (rightWing != null));
                return;
            }

            initialized = true;
            log.info("Draconis: XLII_ShaoweiWingEffect initialized successfully for " + ship.getName());
            return; // Skip first frame to avoid null errors
        }

        // Calculate target wing angles based on engine states
        float leftTarget = 0f;
        float rightTarget = 0f;

        // Forward (accelerating): wings spread outward
        if (engines.isAccelerating()) {
            leftTarget = -MAX_WING_ANGLE;
            rightTarget = MAX_WING_ANGLE;
        }
        // Backward (decelerating/reversing): wings angle inward
        else if (engines.isDecelerating() || engines.isAcceleratingBackwards()) {
            leftTarget = MAX_WING_ANGLE;
            rightTarget = -MAX_WING_ANGLE;
        }
        // Neutral: wings return to center (already set to 0f)

        // Smooth interpolation to target angles
        currentRotateLeft = smoothRotate(currentRotateLeft, leftTarget);
        currentRotateRight = smoothRotate(currentRotateRight, rightTarget);

        // Apply rotation to wings
        float shipFacing = ship.getFacing();
        leftWing.setCurrAngle(shipFacing + currentRotateLeft);
        rightWing.setCurrAngle(shipFacing + currentRotateRight);
    }

    /**
     * Smoothly interpolates rotation from current to target angle.
     *
     * @param current Current rotation angle
     * @param target  Target rotation angle
     * @return New rotation angle
     */
    private float smoothRotate(float current, float target) {
        float difference = MathUtils.getShortestRotation(current, target);

        if (Math.abs(difference) < XLII_ShaoweiWingEffect.ROTATION_SPEED) {
            return target; // Close enough, snap to target
        } else if (difference > 0) {
            return current + XLII_ShaoweiWingEffect.ROTATION_SPEED; // Rotate toward target
        } else {
            return current - XLII_ShaoweiWingEffect.ROTATION_SPEED; // Rotate toward target
        }
    }
}
