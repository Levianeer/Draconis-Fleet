package levianeer.draconis.data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import levianeer.draconis.data.scripts.shipsystems.XLII_PhaseTorpedoGlowEffect;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

/**
 * Phase-aware torpedo AI that synchronizes phase state with parent ship.
 * Torpedoes remain intangible while parent is phased, and automatically
 * dephase upon proximity to target or when parent unphases.
 */
public class XLII_PhaseTorpedoAI implements MissileAIPlugin, GuidedMissileAI {

    // ==================== TUNING PARAMETERS ====================

    // Distance to target that triggers automatic dephase
    private static final float PROXIMITY_DEPHASE_DISTANCE = 500f;
    // Sound plays this many units before dephasing for audio warning
    private static final float PROXIMITY_DEPHASE_SOUND_OFFSET = 100f;

    // Guidance parameters
    private static final float DAMPING = 0.1f;
    private static final float PRECISION_RANGE_SQ = (float) Math.pow(500, 2);

    // Update intervals
    private static final float MIN_CHECK_INTERVAL = 0.05f;
    private static final float MAX_CHECK_INTERVAL = 0.25f;

    // ==================== STATE VARIABLES ====================

    private final MissileAPI missile;
    private final float maxSpeed;

    private CombatEntityAPI target;
    private Vector2f lead = new Vector2f();

    private boolean hasDePhased = false;
    private boolean hasPlayedDephaseSound = false;
    private boolean launch = true;
    private float timer = 0f;
    private float checkInterval = 0.1f;

    private boolean isInFormation = true;  // Formation mode: AI disabled while stacked
    private final boolean startedPhased;   // Track if missile started in phase state

    private CombatEngineAPI engine;
    private XLII_PhaseTorpedoGlowEffect glowEffect;  // Custom phase glow for missiles

    // ==================== INITIALIZATION ====================

    public XLII_PhaseTorpedoAI(MissileAPI missile, boolean startedPhased) {
        this.missile = missile;
        this.maxSpeed = missile.getMaxSpeed();
        this.engine = Global.getCombatEngine();
        this.startedPhased = startedPhased;

        // If missile started unphased, it acts like a normal torpedo
        if (!startedPhased) {
            this.hasDePhased = true;
        } else {
            // Create phase glow effect for phased torpedoes
            glowEffect = new XLII_PhaseTorpedoGlowEffect(missile);
            CombatEntityAPI glowEntity = engine.addLayeredRenderingPlugin(glowEffect);
            glowEntity.getLocation().set(missile.getLocation());
        }
    }

    // ==================== MAIN AI LOOP ====================

    @Override
    public void advance(float amount) {
        if (engine != Global.getCombatEngine()) {
            this.engine = Global.getCombatEngine();
        }

        // Skip AI if game is paused or missile is fading
        if (engine.isPaused() || missile.isFading() || missile.isFizzling()) {
            return;
        }

        // Handle phase state synchronization (always runs, even in formation)
        updatePhaseState();

        // Maintain unphased visuals if already dephased
        if (hasDePhased) {
            ensureUnphasedVisuals();
        }

        // FORMATION MODE: Apply formation visuals and don't issue movement commands
        if (isInFormation) {
            if (!hasDePhased) {
                ensureFormationVisuals();
            }
            return;
        }

        // Target acquisition and switching
        if (!isValidTarget(target)) {
            // If no target, just keep flying straight
            missile.giveCommand(ShipCommand.ACCELERATE);
            return;
        }

        timer += amount;

        // Update lead point calculation
        if (launch || timer >= checkInterval) {
            launch = false;
            timer -= checkInterval;

            // Adjust check interval based on distance to target
            float distSq = MathUtils.getDistanceSquared(missile.getLocation(), target.getLocation());
            checkInterval = Math.min(MAX_CHECK_INTERVAL, Math.max(MIN_CHECK_INTERVAL, distSq / PRECISION_RANGE_SQ));

            // Calculate intercept point
            lead = AIUtils.getBestInterceptPoint(
                missile.getLocation(),
                maxSpeed,
                target.getLocation(),
                target.getVelocity()
            );

            if (lead == null) {
                lead = target.getLocation();
            }
        }

        // Calculate desired facing angle
        float correctAngle = VectorUtils.getAngle(missile.getLocation(), lead);
        float aimAngle = MathUtils.getShortestRotation(missile.getFacing(), correctAngle);

        // Give commands
        missile.giveCommand(ShipCommand.ACCELERATE);

        if (aimAngle < 0) {
            missile.giveCommand(ShipCommand.TURN_RIGHT);
        } else {
            missile.giveCommand(ShipCommand.TURN_LEFT);
        }

        // Damp angular velocity when close to target angle
        if (Math.abs(aimAngle) < Math.abs(missile.getAngularVelocity()) * DAMPING) {
            missile.setAngularVelocity(aimAngle / DAMPING);
        }
    }

    // ==================== PHASE STATE MANAGEMENT ====================

    /**
     * Handles proximity-based dephasing. Torpedoes remain phased until close to enemy ships/fighters.
     * Only runs proximity checks if torpedo started phased.
     */
    private void updatePhaseState() {
        // Already dephased? Nothing to do
        if (hasDePhased) {
            return;
        }

        // If missile didn't start phased, skip all phase mechanics
        if (!startedPhased) {
            return;
        }

        // Check distance to nearest enemy ship or fighter (not stations, drones, etc.)
        ShipAPI nearestEnemy = findNearestEnemyShipOrFighter();
        if (nearestEnemy != null) {
            float distance = MathUtils.getDistance(missile.getLocation(), nearestEnemy.getLocation());

            // Play warning sound before actual dephasing
            if (!hasPlayedDephaseSound && distance <= PROXIMITY_DEPHASE_DISTANCE + PROXIMITY_DEPHASE_SOUND_OFFSET) {
                Global.getSoundPlayer().playSound(
                    "system_phase_cloak_deactivate",
                    1.0f,  // pitch
                    1.0f,  // volume
                    missile.getLocation(),
                    missile.getVelocity()
                );
                hasPlayedDephaseSound = true;
            }

            // Close to enemy - dephase permanently (visual change only, sound already played)
            if (distance <= PROXIMITY_DEPHASE_DISTANCE) {
                dePhase();
                hasDePhased = true;
                return;
            }
        }

        // Still phased - maintain phased visuals
        ensurePhasedCollision();
        ensurePhasedGlow();
        ensurePhasedVisuals();  // Maintain jitter and alpha
    }

    /**
     * Finds the nearest enemy ship or fighter (excludes stations, drones, etc.).
     * Only dephases for actual combat ships to avoid wasting torpedoes on debris/stations.
     */
    private ShipAPI findNearestEnemyShipOrFighter() {
        ShipAPI nearest = null;
        float minDistSq = Float.MAX_VALUE;

        for (ShipAPI ship : engine.getShips()) {
            // Skip friendlies
            if (ship.getOwner() == missile.getOwner()) continue;

            // Skip dead/hulked ships
            if (!ship.isAlive() || ship.isHulk()) continue;

            // ONLY target combat ships/fighters (not stations, objectives, etc.)
            // DEFAULT hull size = stations, nav buoys, comm relays, etc.
            if (ship.getHullSize() == ShipAPI.HullSize.DEFAULT) continue;

            // Find closest
            float distSq = MathUtils.getDistanceSquared(missile.getLocation(), ship.getLocation());
            if (distSq < minDistSq) {
                minDistSq = distSq;
                nearest = ship;
            }
        }

        return nearest;
    }
    /**
     * Ensures torpedo has formation visual effects (purple engines).
     * Only applied while in formation mode.
     */
    private void ensureFormationVisuals() {
        // Set phased engine color using standard phase ship colors
        missile.getEngineController().fadeToOtherColor(
            this,
            new Color(255, 175, 255),  // effectColor1 - bright glow
            new Color(255, 0, 255),    // effectColor2 - contrail
            1.0f,
            1.0f
        );
    }

    /**
     * Transitions torpedo from phased to corporeal state.
     */
    private void dePhase() {
        if (hasDePhased) return;

        hasDePhased = true;
        missile.setCollisionClass(CollisionClass.MISSILE_FF);

        // Note: Sound is played earlier in updatePhaseState() as warning

        // Visual: Change from purple to ORANGE (armed state)
        missile.setJitter(this, new Color(255, 150, 50, 100), 0.5f, 3, 0f, 5f);

        // Orange engine for armed state
        missile.getEngineController().fadeToOtherColor(
            this,
            new Color(255, 150, 50),   // glow
            new Color(255, 100, 30),   // contrail
            1.0f,
            1.0f
        );

        setArmedGlow();  // Maintain glow

        // Fade out the phase glow effect
        if (glowEffect != null) {
            glowEffect.startFadeOut();
        }

        // Visual feedback: spawn small phase-out effect
        Vector2f loc = new Vector2f(missile.getLocation());
        engine.addSmoothParticle(
            loc,
            new Vector2f(),
            50f,  // size
            1.0f, // brightness
            0.5f, // duration
            new Color(255, 175, 255, 150)  // effectColor1 with transparency
        );
    }

    /**
     * Ensures missile has intangible collision class while phased.
     */
    private void ensurePhasedCollision() {
        if (missile.getCollisionClass() != CollisionClass.NONE) {
            missile.setCollisionClass(CollisionClass.NONE);
        }
    }

    /**
     * Applies purple glow effect while torpedo is phased.
     */
    private void ensurePhasedGlow() {
        missile.setRenderGlowAbove(false);
        // Glow color is handled by the sprite's built-in glow based on engine color
    }

    /**
     * Ensures torpedo maintains phased visual effects (purple jitter + purple engines).
     * Called every frame to prevent visual effects from being reset.
     */
    private void ensurePhasedVisuals() {
        // Maintain purple phase jitter effect
        missile.setJitter(this, new Color(255, 175, 255, 100), 0.5f, 3, 0f, 5f);  // effectColor1

        // Maintain purple engine trail (phased state)
        missile.getEngineController().fadeToOtherColor(
            this,
            new Color(255, 175, 255),  // effectColor1 - bright glow
            new Color(255, 0, 255),    // effectColor2 - contrail
            1.0f,
            1.0f
        );
    }

    /**
     * Ensures torpedo maintains unphased/armed visual effects (orange jitter + orange engines).
     * Called every frame after dephasing to keep orange jitter visible.
     */
    private void ensureUnphasedVisuals() {
        // Maintain orange armed jitter effect
        missile.setJitter(this, new Color(255, 150, 50, 100), 0.5f, 3, 0f, 5f);

        // Maintain orange engine trail (armed state)
        missile.getEngineController().fadeToOtherColor(
            this,
            new Color(255, 150, 50),   // orange glow
            new Color(255, 100, 30),   // orange contrail
            1.0f,
            1.0f
        );
    }

    /**
     * Applies red glow effect when torpedo is armed and dephased.
     */
    private void setArmedGlow() {
        missile.setRenderGlowAbove(false);
        // Glow color transitions to red to indicate armed warhead
    }

    // ==================== TARGET MANAGEMENT ====================

    @Override
    public void setTarget(CombatEntityAPI target) {
        this.target = target;
    }

    @Override
    public CombatEntityAPI getTarget() {
        return target;
    }

    /**
     * Sets formation mode. When true, AI doesn't issue movement commands.
     */
    public void setInFormation(boolean inFormation) {
        // If transitioning from formation to launched, switch to orange engines
        if (this.isInFormation && !inFormation) {
            transitionToLaunchedVisuals();
        }
        this.isInFormation = inFormation;
    }

    /**
     * Transitions visuals when torpedo exits formation mode (but is still phased).
     * Changes engine color to orange while maintaining phase effects.
     */
    private void transitionToLaunchedVisuals() {
        // Change to orange engine color when launched
        missile.getEngineController().fadeToOtherColor(
            this,
            new Color(255, 150, 50),   // orange glow
            new Color(255, 100, 30),   // orange contrail
            1.0f,
            1.0f
        );
    }

    /**
     * Checks if target is still valid.
     */
    private boolean isValidTarget(CombatEntityAPI target) {
        if (target instanceof ShipAPI ship) {
            return ship.isAlive() && !ship.isExpired();
        }
        return target != null && engine.isEntityInPlay(target);
    }

    // ==================== UNUSED INTERFACE METHODS ====================

    public void init() {
        // Initialization handled in constructor
    }
}