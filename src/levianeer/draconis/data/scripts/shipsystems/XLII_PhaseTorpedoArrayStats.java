package levianeer.draconis.data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.impl.combat.NegativeExplosionVisual;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.scripts.ai.XLII_PhaseTorpedoAI;
import org.lazywizard.lazylib.FastTrig;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase Torpedo Array ship system.
 * Allows stacking torpedoes through multiple activations, then launches them
 * all simultaneously when the stack timer expires or releases.
 */
public class XLII_PhaseTorpedoArrayStats extends BaseShipSystemScript {

    // ==================== TUNING PARAMETERS ====================

    // Range and targeting
    private static final float SYSTEM_RANGE = 1200f;

    // Stacking mechanics
    private static final float STACK_TIMER_DURATION = 2.5f;  // seconds
    // MAX_STACK_COUNT is now read from ship_systems.csv

    // Spawn positioning
    private static final float BASE_SPAWN_OFFSET = 150f;     // distance from ship center
    private static final float SPACING_INCREMENT = 50f;     // additional distance per stack

    // Formation inertia (drift/lag when ship maneuvers)
    // Lower values = more drift/lag, higher values = tighter formation
    // At 60fps: 0.03 = ~33 frames (0.5s) to catch up, 0.02 = ~50 frames (0.8s)
    private static final float FORMATION_POSITION_LERP = 0.03f;  // position catch-up speed (very slow = visible lag)
    private static final float VELOCITY_DAMPING = 0.5f;          // velocity smoothing for visual velocity

    // Torpedo stats (applied at spawn)
    private static final float TORPEDO_SPEED = 250f;
    private static final float TORPEDO_MAX_RANGE = 1500f;
    private static final float TORPEDO_LIFESPAN = 9f;

    // Visual effects - using standard phase ship colors
    private static final Color LAUNCH_ARC_COLOR = new Color(255, 175, 255, 100);

    // ==================== STATE TRACKING ====================

    /**
     * Represents a visual-only torpedo in formation (not a real missile).
     * Rendered using custom sprites/particles, with smooth inertia.
     */
    private static class VisualTorpedo {
        Vector2f virtualPosition = new Vector2f();  // Current visual position (with inertia)
        Vector2f virtualVelocity = new Vector2f();  // Current visual velocity (for smooth movement)
        float virtualFacing = 0f;                   // Current visual facing (with rotation inertia)
        int formationIndex = 0;                     // Index in formation (for positioning)
        boolean initialized = false;                // Track first-frame initialization

        // Visual effect timing
        float effectTimer = 0f;                     // Accumulated time for oscillation/pulse effects
        float pulseTimer = 0f;                      // Timer for periodic pulse effects
    }

    private static class StackState {
        List<VisualTorpedo> visualTorpedoes = new ArrayList<>();  // Visual-only torpedoes in formation
        float stackTimer = 0f;
        boolean isStacking = false;
        int currentStackCount = 0;
        boolean startOnStarboard = true;  // Track which side to start on for alternating spawn
    }

    // Ship ID -> StackState mapping
    private static final java.util.HashMap<String, StackState> shipStates = new java.util.HashMap<>();

    private WeaponAPI torpedoWeapon;
    private float prevEffectLevel = 0f;

    // Status keys for UI
    private final Object STATUSKEY_STACK = new Object();
    private final Object STATUSKEY_RANGE = new Object();

    // ==================== MAIN SYSTEM LOGIC ====================

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (stats.getEntity() instanceof ShipAPI) ? (ShipAPI) stats.getEntity() : null;
        if (ship == null) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        // Get or create state for this ship
        StackState stackState = getStackState(ship);

        // Create fake weapon if needed
        if (torpedoWeapon == null) {
            torpedoWeapon = engine.createFakeWeapon(ship, "XLII_phasetorp_launcher");
        }

        boolean isPlayer = ship == Global.getCombatEngine().getPlayerShip();

        // Detect activation when effectLevel reaches 1.0 (fully charged)
        boolean activated = (effectLevel >= 1f && prevEffectLevel < 1f);

        // Max stack is now the actual max ammo (including our rounding bonus)
        int maxStack = ship.getSystem().getMaxAmmo();
        if (activated && stackState.currentStackCount < maxStack) {
            handleActivation(ship, stackState, engine);
        }

        // Update stack timer and formation
        if (stackState.isStacking && !engine.isPaused()) {
            updateStacking(ship, stackState, engine);
        }

        // Player status display
        if (isPlayer) {
            updatePlayerStatus(ship, stackState);
        }

        // Note: Launch is handled by timer expiration in updateStacking()
        // No need for state transition launch trigger (it causes immediate launches)

        prevEffectLevel = effectLevel;
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        // Cleanup handled in state machine
    }

    @Override
    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        if (system.isOutOfAmmo()) return null;
        if (system.getState() != ShipSystemAPI.SystemState.IDLE) return null;

        ShipAPI target = findTarget(ship);
        if (isInRange(ship, target)) {
            return "READY";
        }
        if (target != null && !isInRange(ship, target)) {
            return "OUT OF RANGE";
        }
        return "NO TARGET";
    }

    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        // Disable system when at max stack count
        StackState stackState = getStackState(ship);
        int maxStack = system.getMaxAmmo();
        return stackState.currentStackCount < maxStack;
    }

    // ==================== ACTIVATION HANDLING ====================

    /**
     * Handles a single activation: creates a visual torpedo, adds to stack, resets timer.
     */
    private void handleActivation(ShipAPI ship, StackState stackState, CombatEngineAPI engine) {
        // Create visual torpedo (no real missile spawned yet)
        VisualTorpedo visualTorp = createVisualTorpedo(ship, stackState);

        // Add to stack
        stackState.visualTorpedoes.add(visualTorp);
        stackState.currentStackCount++;
        stackState.isStacking = true;
        stackState.stackTimer = STACK_TIMER_DURATION;

        Vector2f spawnLoc = visualTorp.virtualPosition;
        Vector2f zeroVel = new Vector2f();

        // Multi-layered negative explosion (tuned down)
        NegativeExplosionVisual.NEParams inversionParams = new NegativeExplosionVisual.NEParams();
        inversionParams.radius = 20f;   // Reduced from 35f for smaller clouds
        inversionParams.thickness = 15f;
        inversionParams.fadeIn = 0.1f;
        inversionParams.fadeOut = 0.5f;
        inversionParams.color = new Color(255, 175, 255, 255);  // Phase purple
        inversionParams.withNegativeParticles = true;
        inversionParams.hitGlowSizeMult = 0.4f;  // Reduced from 0.8f for smaller negative particles

        CombatEntityAPI inversionEffect = engine.addLayeredRenderingPlugin(new NegativeExplosionVisual(inversionParams));
        inversionEffect.getLocation().set(spawnLoc);
        inversionEffect.getVelocity().set(ship.getVelocity());

        // Secondary smaller inversion (tuned down)
        NegativeExplosionVisual.NEParams innerInversion = new NegativeExplosionVisual.NEParams();
        innerInversion.radius = 8f;  // Reduced from 15f
        innerInversion.thickness = 8f;
        innerInversion.fadeIn = 0.05f;
        innerInversion.fadeOut = 0.3f;
        innerInversion.color = new Color(255, 255, 255, 200);  // Bright white core
        innerInversion.withNegativeParticles = true;
        innerInversion.hitGlowSizeMult = 0.3f;  // Reduced from 1.0f for much smaller negative particles

        CombatEntityAPI innerEffect = engine.addLayeredRenderingPlugin(new NegativeExplosionVisual(innerInversion));
        innerEffect.getLocation().set(spawnLoc);
        innerEffect.getVelocity().set(ship.getVelocity());

        // Phase ripple, concentric expanding rings
        for (int i = 0; i < 3; i++) {
            float size = 30f + (i * 20f);
            float duration = 0.6f + (i * 0.2f);

            engine.addSmoothParticle(
                spawnLoc,
                zeroVel,
                size,
                0.7f - (i * 0.2f),
                duration,
                new Color(255, 175, 255, 150 - (i * 30))
            );
        }

        // Shimmer particles: subtle outward radiating distortion
        for (int i = 0; i < 4; i++) {
            float angle = (360f / 4f) * i + (float)(Math.random() * 20f);  // Add slight randomization
            Vector2f offset = Misc.getUnitVectorAtDegreeAngle(angle);
            offset.scale(15f + (float)(Math.random() * 8f));  // Tighter spawn area

            Vector2f particlePos = new Vector2f(spawnLoc.x + offset.x, spawnLoc.y + offset.y);

            // Reduced outward velocity for subtle effect
            Vector2f shimmerVel = new Vector2f(offset);
            shimmerVel.scale(25f);  // Reduced from 50f

            Color shimmerColor = (i % 2 == 0) ?
                new Color(255, 175, 255, 180) :
                new Color(255, 220, 255, 160);

            engine.addSmoothParticle(
                particlePos,
                shimmerVel,
                5f + (float)(Math.random() * 2f),  // Smaller particles (was 8-12f)
                0.7f,  // Slightly reduced brightness
                0.4f,
                shimmerColor
            );
        }

        // Ghostly after-image trail
        engine.addSmoothParticle(
            spawnLoc,
            zeroVel,
            50f,
            0.5f,
            0.8f,
            new Color(255, 175, 255, 80)
        );
    }

    /**
     * Creates a visual-only torpedo for formation display (not a real missile).
     */
    private VisualTorpedo createVisualTorpedo(ShipAPI ship, StackState stackState) {
        // Calculate initial spawn position based on stack count
        int stackIndex = stackState.currentStackCount;

        // Determine side: alternate from startOnStarboard flag
        boolean isStarboard;
        if (stackState.startOnStarboard) {
            isStarboard = (stackIndex % 2 == 0);  // Even indices → starboard
        } else {
            isStarboard = (stackIndex % 2 != 0); // Odd indices → starboard (pattern reversed)
        }

        float distance = BASE_SPAWN_OFFSET + ((float) stackIndex / 2) * SPACING_INCREMENT;

        // Calculate offset perpendicular to ship facing
        float angle = ship.getFacing() + (isStarboard ? 90f : -90f);
        Vector2f offset = Misc.getUnitVectorAtDegreeAngle(angle);
        offset.scale(distance);

        Vector2f spawnLoc = new Vector2f(ship.getLocation());
        Vector2f.add(spawnLoc, offset, spawnLoc);

        // Create visual torpedo object
        VisualTorpedo visualTorp = new VisualTorpedo();
        visualTorp.virtualPosition.set(spawnLoc);
        visualTorp.virtualVelocity.set(ship.getVelocity());
        visualTorp.virtualFacing = ship.getFacing();
        visualTorp.formationIndex = stackIndex;
        visualTorp.initialized = true;

        return visualTorp;
    }

    // ==================== STACKING UPDATE ====================

    /**
     * Updates stacking state each frame: updates visual torpedo positions and renders them.
     */
    private void updateStacking(ShipAPI ship, StackState stackState, CombatEngineAPI engine) {
        // Safety check: don't update if not actively stacking
        if (!stackState.isStacking) {
            return;
        }

        float amount = engine.getElapsedInLastFrame();

        // Update timer
        stackState.stackTimer -= amount;

        // Update and render each visual torpedo in formation
        int index = 0;
        for (VisualTorpedo visualTorp : stackState.visualTorpedoes) {
            // Update visual position with smooth inertia
            updateVisualTorpedoFormation(ship, visualTorp, index, stackState);

            // Update effect timers
            visualTorp.effectTimer += amount;
            visualTorp.pulseTimer += amount;

            // Render the visual torpedo with enhanced effects
            renderVisualTorpedo(visualTorp, stackState.stackTimer, engine);

            index++;
        }

        // Check if timer expired
        if (stackState.stackTimer <= 0f) {
            attemptLaunch(ship, stackState, engine);
        }
    }

    /**
     * Updates a visual torpedo's formation position with smooth inertia.
     */
    private void updateVisualTorpedoFormation(ShipAPI ship, VisualTorpedo visualTorp, int stackIndex, StackState stackState) {
        // Calculate ideal formation position using SAME logic as createVisualTorpedo
        boolean isStarboard;
        if (stackState.startOnStarboard) {
            isStarboard = (stackIndex % 2 == 0);  // Even indices → starboard
        } else {
            isStarboard = (stackIndex % 2 != 0); // Odd indices → starboard (pattern reversed)
        }
        float distance = BASE_SPAWN_OFFSET + ((float) stackIndex / 2) * SPACING_INCREMENT;

        float angle = ship.getFacing() + (isStarboard ? 90f : -90f);
        Vector2f offset = Misc.getUnitVectorAtDegreeAngle(angle);
        offset.scale(distance);

        Vector2f idealPosition = new Vector2f(ship.getLocation());
        Vector2f.add(idealPosition, offset, idealPosition);
        float idealFacing = ship.getFacing();

        // Update virtual position with smooth interpolation (creates inertia effect)
        visualTorp.virtualPosition.x += (idealPosition.x - visualTorp.virtualPosition.x) * FORMATION_POSITION_LERP;
        visualTorp.virtualPosition.y += (idealPosition.y - visualTorp.virtualPosition.y) * FORMATION_POSITION_LERP;

        // Update virtual velocity (follows ship velocity with damping)
        Vector2f shipVel = ship.getVelocity();
        visualTorp.virtualVelocity.x += (shipVel.x - visualTorp.virtualVelocity.x) * VELOCITY_DAMPING;
        visualTorp.virtualVelocity.y += (shipVel.y - visualTorp.virtualVelocity.y) * VELOCITY_DAMPING;

        // Match ship facing directly (no rotation inertia to avoid launch angle issues)
        // Position inertia provides the main visual effect
        visualTorp.virtualFacing = idealFacing;
    }

    /**
     * Renders enhanced visual effects for torpedo in formation.
     * Includes pulsing glow, phase shimmer, and trailing particles.
     */
    private void renderVisualTorpedo(VisualTorpedo visualTorp, float timeUntilLaunch, CombatEngineAPI engine) {
        Vector2f pos = visualTorp.virtualPosition;
        Vector2f zeroVel = new Vector2f(0f, 0f);

        // === PULSING GLOW EFFECT ===
        // Oscillate size and brightness with sine wave (period ~1.5s)
        float pulsePhase = visualTorp.effectTimer * 2f;  // Speed up oscillation
        float pulseFactor = 0.7f + 0.3f * (float) FastTrig.sin(pulsePhase);  // Range: 0.7 to 1.0

        // Increase urgency as launch approaches
        float urgencyMultiplier = 1.0f;
        if (timeUntilLaunch < 0.5f) {
            urgencyMultiplier = 1.5f;  // Larger and brighter when about to launch
        }

        float coreSize = 12f * pulseFactor * urgencyMultiplier;
        float coreBrightness = 0.8f * pulseFactor * urgencyMultiplier;

        // Inner bright core (white-ish)
        engine.addSmoothParticle(
            pos,
            zeroVel,
            coreSize,
            coreBrightness,
            0.1f,  // One frame duration
            new Color(255, 220, 255, 200)
        );

        // Outer diffuse halo (purple)
        engine.addSmoothParticle(
            pos,
            zeroVel,
            coreSize * 2.5f,
            coreBrightness * 0.5f,
            0.1f,
            new Color(255, 175, 255, 120)
        );

        // Spawn distortion particles around torpedo at random offsets
        int shimmerCount = (timeUntilLaunch < 0.5f) ? 3 : 2;

        for (int i = 0; i < shimmerCount; i++) {
            // Random offset around torpedo
            float offsetAngle = (float)(Math.random() * 360f);
            float offsetDist = 5f + (float)(Math.random() * 10f);

            Vector2f offset = Misc.getUnitVectorAtDegreeAngle(offsetAngle);
            offset.scale(offsetDist);

            Vector2f shimmerPos = new Vector2f(pos.x + offset.x, pos.y + offset.y);

            // Alternate colors for shimmer effect
            Color shimmerColor;
            float rand = (float)Math.random();
            if (rand < 0.33f) {
                shimmerColor = new Color(255, 175, 255, 150);  // Purple
            } else if (rand < 0.66f) {
                shimmerColor = new Color(255, 220, 255, 160);  // Pink
            } else {
                shimmerColor = new Color(255, 255, 255, 140);  // White
            }

            engine.addSmoothParticle(
                shimmerPos,
                zeroVel,
                4f + (float)(Math.random() * 3f),
                0.6f,
                0.15f,
                shimmerColor
            );
        }

        // === TRAILING PARTICLES ===
        // Spawn particles behind torpedo based on velocity
        float speed = (float) Math.sqrt(
            visualTorp.virtualVelocity.x * visualTorp.virtualVelocity.x +
            visualTorp.virtualVelocity.y * visualTorp.virtualVelocity.y
        );

        if (speed > 10f) {  // Only show trail when moving
            // Calculate trail direction (opposite of velocity)
            Vector2f trailDir = new Vector2f(-visualTorp.virtualVelocity.x, -visualTorp.virtualVelocity.y);
            float length = (float) Math.sqrt(trailDir.x * trailDir.x + trailDir.y * trailDir.y);
            if (length > 0) {
                trailDir.scale(1f / length);  // Normalize
            }

            // Spawn 2-3 trail particles
            int trailCount = (speed > 50f) ? 3 : 2;
            for (int i = 1; i <= trailCount; i++) {
                Vector2f trailOffset = new Vector2f(trailDir);
                trailOffset.scale(i * 8f);  // Space them out behind torpedo

                Vector2f trailPos = new Vector2f(pos.x + trailOffset.x, pos.y + trailOffset.y);

                // Fade opacity along trail length
                int alpha = 100 - (i * 25);
                float size = 8f - (i * 2f);
                float brightness = 0.5f - (i * 0.1f);

                engine.addSmoothParticle(
                    trailPos,
                    zeroVel,
                    size,
                    brightness,
                    0.2f,
                    new Color(255, 175, 255, Math.max(alpha, 20))
                );
            }
        }

        // === PERIODIC PHASE PULSE ===
        // Expanding ring effect every 0.5 seconds
        if (visualTorp.pulseTimer >= 0.25f) {
            visualTorp.pulseTimer = 0f;

            // Spawn expanding pulse ring
            engine.addSmoothParticle(
                pos,
                zeroVel,
                25f,
                0.6f,
                0.4f,
                new Color(255, 175, 255, 120)
            );
        }

        // Small particles that drift around the torpedo
        if (visualTorp.effectTimer % 0.3f < 0.1f) {  // Spawn periodically
            float orbitAngle = visualTorp.effectTimer * 50f;  // Slow rotation
            Vector2f orbitOffset = Misc.getUnitVectorAtDegreeAngle(orbitAngle);
            orbitOffset.scale(15f + (float)(Math.random() * 5f));

            Vector2f motePos = new Vector2f(pos.x + orbitOffset.x, pos.y + orbitOffset.y);

            engine.addSmoothParticle(
                motePos,
                zeroVel,
                3f,
                0.4f,
                0.3f,
                new Color(255, 200, 255, 100)
            );
        }
    }

    // ==================== LAUNCH HANDLING ====================

    /**
     * Launches all stacked torpedoes.
     * If target in range: guided launch. If no target: dumbfire in ship's facing direction.
     */
    private void attemptLaunch(ShipAPI ship, StackState stackState, CombatEngineAPI engine) {
        if (!stackState.isStacking || stackState.visualTorpedoes.isEmpty()) {
            resetStackState(stackState);
            return;
        }

        // Find target (may be null)
        ShipAPI target = findTarget(ship);
        boolean targetInRange = isInRange(ship, target);

        // Always launch, either guided or dumbfire
        launchAllTorpedoes(ship, stackState, targetInRange ? target : null, engine);

        // Clear visual torpedo list
        resetStackState(stackState);
    }

    /**
     * Launches all stacked torpedoes toward the target (or dumbfire if no target).
     * Spawns real missiles from visual torpedo data.
     * @param target - The target ship, or null for dumbfire mode
     */
    private void launchAllTorpedoes(ShipAPI ship, StackState stackState, ShipAPI target, CombatEngineAPI engine) {
        // CRITICAL: Stop formation updates immediately by marking as no longer stacking
        stackState.isStacking = false;

        boolean isDumbfire = (target == null);
        int launchedCount = 0;

        // Detect if ship is currently phased - this determines torpedo initial state
        boolean shipIsPhased = ship.isPhased();

        // Spawn real missiles from visual torpedo positions
        for (VisualTorpedo visualTorp : stackState.visualTorpedoes) {
            // Calculate launch direction
            float launchAngle;
            if (isDumbfire) {
                // Dumbfire: launch in ship's facing direction
                launchAngle = ship.getFacing();
            } else {
                // Guided: launch toward target
                launchAngle = Misc.getAngleInDegrees(visualTorp.virtualPosition, target.getLocation());
            }

            // Spawn the REAL missile at the visual torpedo's position
            MissileAPI missile = (MissileAPI) engine.spawnProjectile(
                ship,
                torpedoWeapon,
                "XLII_phasetorp_launcher",
                visualTorp.virtualPosition,
                visualTorp.virtualFacing,
                new Vector2f()  // velocity set below
            );

            if (missile != null) {
                // CRITICAL: Reset flight time immediately to prevent despawning during formation phase
                // This ensures the despawn timer only starts NOW, not during stacking
                missile.setFlightTime(0f);
                missile.setMaxFlightTime(TORPEDO_LIFESPAN);
                missile.setMaxRange(TORPEDO_MAX_RANGE);

                // Set up the custom AI with ship's phase state
                XLII_PhaseTorpedoAI ai = new XLII_PhaseTorpedoAI(missile, shipIsPhased);
                missile.setMissileAI(ai);

                // CRITICAL: Disable formation mode to allow AI to control the missile
                ai.setInFormation(false);

                // Set target (null for dumbfire - AI will handle it)
                ai.setTarget(target);

                // Start in appropriate collision state based on ship phase
                if (shipIsPhased) {
                    missile.setCollisionClass(CollisionClass.NONE);

                    // Sound: Play phase sound for phased torpedos
                    Global.getSoundPlayer().playSound(
                        "system_phase_cloak_activate",
                        1.0f,  // pitch
                        1.0f,  // volume
                        missile.getLocation(),
                        missile.getVelocity()
                    );

                    // Visual: Purple jitter indicates phased state
                    missile.setJitter(this, new Color(180, 120, 255, 100), 0.5f, 3, 0f, 5f);
                } else {
                    missile.setCollisionClass(CollisionClass.MISSILE_FF);
                    // Visual: Orange jitter indicates armed state
                    missile.setJitter(this, new Color(255, 150, 50, 100), 0.5f, 3, 0f, 5f);
                }

                // Give initial velocity
                Vector2f velocity = Misc.getUnitVectorAtDegreeAngle(launchAngle);
                velocity.scale(TORPEDO_SPEED);
                missile.getVelocity().set(velocity);

                // Visual: launch arc
                engine.spawnEmpArcVisual(
                    ship.getLocation(),
                    ship,
                    missile.getLocation(),
                    missile,
                    10f,
                    LAUNCH_ARC_COLOR,
                    Color.WHITE
                );

                launchedCount++;
            }
        }
    }

    // ==================== TARGET FINDING ====================

    /**
     * Finds the current target for the ship.
     */
    private ShipAPI findTarget(ShipAPI ship) {
        ShipAPI target = ship.getShipTarget();

        if (target != null && target.isAlive()) {
            return target;
        }

        // Fallback: find closest enemy in range
        return Misc.findClosestShipEnemyOf(
            ship,
            ship.getMouseTarget() != null ? ship.getMouseTarget() : ship.getLocation(),
            ShipAPI.HullSize.FIGHTER,
            SYSTEM_RANGE,
            true
        );
    }

    /**
     * Checks if target is within system range.
     */
    private boolean isInRange(ShipAPI ship, ShipAPI target) {
        if (target == null) return false;

        float dist = Misc.getDistance(ship.getLocation(), target.getLocation());
        float radSum = ship.getCollisionRadius() + target.getCollisionRadius();

        return dist <= SYSTEM_RANGE + radSum;
    }

    // ==================== PLAYER UI ====================

    /**
     * Updates player status indicators.
     */
    private void updatePlayerStatus(ShipAPI ship, StackState stackState) {
        CombatEngineAPI engine = Global.getCombatEngine();
        int maxStack = ship.getSystem().getMaxAmmo();

        // Stack count indicator
        if (stackState.isStacking) {
            String timerStr = String.format("%.1f", stackState.stackTimer);
            engine.maintainStatusForPlayerShip(
                STATUSKEY_STACK,
                ship.getSystem().getSpecAPI().getIconSpriteName(),
                ship.getSystem().getDisplayName(),
                stackState.currentStackCount + "/" + maxStack + " stacked (" + timerStr + "s)",
                false
            );
        }

        // Range indicator - only show when system is ready and we have a target
        ShipAPI target = findTarget(ship);
        boolean systemReady = stackState.currentStackCount < maxStack;
        if (target != null && systemReady) {
            boolean inRange = isInRange(ship, target);
            engine.maintainStatusForPlayerShip(
                STATUSKEY_RANGE,
                ship.getSystem().getSpecAPI().getIconSpriteName(),
                ship.getSystem().getDisplayName(),
                inRange ? "IN RANGE" : "OUT OF RANGE",
                !inRange
            );
        }
    }

    // ==================== STATE MANAGEMENT ====================

    /**
     * Gets or creates stack state for a ship.
     */
    private StackState getStackState(ShipAPI ship) {
        String shipId = ship.getId();
        if (!shipStates.containsKey(shipId)) {
            shipStates.put(shipId, new StackState());
        }
        return shipStates.get(shipId);
    }

    /**
     * Resets stack state to idle.
     */
    private void resetStackState(StackState stackState) {
        stackState.visualTorpedoes.clear();
        stackState.currentStackCount = 0;
        stackState.isStacking = false;
        stackState.stackTimer = 0f;

        // Toggle starting side for next activation group
        stackState.startOnStarboard = !stackState.startOnStarboard;
    }
}