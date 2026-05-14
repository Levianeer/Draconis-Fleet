package levianeer.draconis.data.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SoundAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.Misc;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

/**
 * Abstract base for the Transverse Jump mechanic. Handles charge sequence, visual effects,
 * and fade-out. Subclasses implement {@link #checkShouldActivate()} to decide when to trigger
 * and {@link #onWarpComplete()} to perform the actual teleport.
 */
public abstract class XLII_TransverseJumpScript implements AdvanceableListener {

    protected final ShipAPI ship;
    protected final float chargeTime;
    protected final String modId;

    // State
    private boolean warpCharging = false;
    private boolean warpExecuting = false;
    private float chargeProgress = 0f;
    protected float soundCooldown = 0f;
    private SoundAPI chargingSound = null;
    private final FaderUtil warpFader = new FaderUtil(0.5f, 0.5f);

    // Colors
    public static final Color WARP_COLOR = new Color(100, 150, 255, 255);
    public static final Color EMP_CORE_COLOR = new Color(200, 150, 255, 200);
    public static final Color EMP_FRINGE_COLOR = new Color(180, 100, 255, 150);
    protected static final Color BLUE_CORE_COLOR = new Color(150, 200, 255, 200);
    protected static final Color BLUE_FRINGE_COLOR = new Color(100, 150, 255, 150);
    protected static final Vector2f ZERO_VEL = new Vector2f(0, 0);

    // Sound IDs
    public static final String EMP_IMPACT_ID = "draconis_venting_or_overloaded";
    private static final String CHARGE_WARP_ID = "mote_attractor_loop_dark";
    private static final String FINAL_WARP_ID_0 = "system_tenebrous_expulsion_activate";
    private static final String FINAL_WARP_ID_1 = "energy_lash_fire";
    private static final String FINAL_WARP_ID_2 = "system_nova_burst_fire";

    public XLII_TransverseJumpScript(ShipAPI ship, float chargeTime, String modId) {
        this.ship = ship;
        this.chargeTime = chargeTime;
        this.modId = modId;
    }

    @Override
    public final void advance(float amount) {
        if (ship == null || !Global.getCombatEngine().isEntityInPlay(ship) ||
                ship.isHulk() || !ship.isAlive() || ship.isPiece()) {
            cleanupModifiers();
            return;
        }

        if (soundCooldown > 0f) {
            soundCooldown -= amount;
        }

        onAdvance(amount);

        if (checkShouldActivate() && !warpCharging && !warpExecuting) {
            startWarpCharge();
        }

        if (warpCharging && !warpExecuting) {
            handleCharging(amount);
        } else if (warpExecuting) {
            handleWarpExecution(amount);
        } else {
            cleanupModifiers();
        }
    }

    /** Called each frame before the warp state machine. Override for extra per-frame logic (e.g. overload effects). */
    protected void onAdvance(float amount) {}

    /** Return true when this ship should begin charging the warp jump. */
    protected abstract boolean checkShouldActivate();

    /** Called when the warp fade-out completes. Perform the actual teleport here. */
    protected abstract void onWarpComplete();

    private void startWarpCharge() {
        warpCharging = true;
        chargeProgress = 0f;
        ship.setRetreating(false, false);
        ship.getFluxTracker().showOverloadFloatyIfNeeded("Calculating drive...", WARP_COLOR, 4f, true);
        chargingSound = Global.getSoundPlayer().playSound(CHARGE_WARP_ID, 1f, 1f, ship.getLocation(), ship.getVelocity());
    }

    private void handleCharging(float amount) {
        if (ship.isHulk() || !ship.isAlive()) {
            cleanupModifiers();
            return;
        }

        chargeProgress += amount;
        float chargePercent = Math.min(chargeProgress / chargeTime, 1f);

        applyChargePenalties(chargePercent);
        blockMovementCommands();

        if (Math.random() < 0.15f) {
            spawnChargingParticles();
        }

        if (Math.random() < (0.05f + 0.1f * chargePercent)) {
            spawnChargingLightningFlicker(chargePercent);
        }

        ship.setJitter(this, WARP_COLOR, chargePercent, (int)(10 * chargePercent),
                ship.getCollisionRadius() * chargePercent * 0.5f);

        if (chargeProgress >= chargeTime) {
            completeCharge();
        }
    }

    private void applyChargePenalties(float chargePercent) {
        ship.getMutableStats().getMaxSpeed().modifyMult(modId + "_charge", 1f - (chargePercent * 0.8f));
        ship.getMutableStats().getAcceleration().modifyMult(modId + "_charge", 1f - (chargePercent * 0.7f));
        ship.getMutableStats().getTurnAcceleration().modifyMult(modId + "_charge", 1f - (chargePercent * 0.6f));
    }

    private void blockMovementCommands() {
        ship.blockCommandForOneFrame(ShipCommand.ACCELERATE_BACKWARDS);
        ship.blockCommandForOneFrame(ShipCommand.ACCELERATE);
        ship.blockCommandForOneFrame(ShipCommand.STRAFE_LEFT);
        ship.blockCommandForOneFrame(ShipCommand.STRAFE_RIGHT);
    }

    private void spawnChargingParticles() {
        Vector2f sparkPoint = Misc.getPointAtRadius(ship.getLocation(),
                (float) (ship.getCollisionRadius() * (1f + Math.random() * 0.5f)));
        Global.getCombatEngine().addSmoothParticle(
                sparkPoint,
                Misc.getUnitVectorAtDegreeAngle((float)(Math.random() * 360f)),
                50f + (float)(Math.random() * 100f),
                1f,
                0.2f + (float)(Math.random() * 0.3f),
                WARP_COLOR
        );
    }

    private void spawnChargingLightningFlicker(float chargePercent) {
        if (soundCooldown > 0f) return;

        Vector2f loc = ship.getLocation();
        float range = ship.getCollisionRadius() * (1.0f + 1.5f * chargePercent);

        Vector2f start = Misc.getPointAtRadius(loc, range);
        Vector2f end = Misc.getPointAtRadius(loc, range);

        Global.getCombatEngine().spawnEmpArcVisual(
                start, ship, end, ship,
                3f + (float)Math.random() * 3f,
                Misc.setAlpha(EMP_FRINGE_COLOR, 180),
                Misc.setAlpha(EMP_CORE_COLOR, 180)
        );

        Global.getSoundPlayer().playSound(EMP_IMPACT_ID, 1.0f, 0.7f, loc, ZERO_VEL);
        soundCooldown = 0.35f;
    }

    private void completeCharge() {
        warpCharging = false;
        warpExecuting = true;
        warpFader.fadeOut();

        if (chargingSound != null) {
            chargingSound.stop();
            chargingSound = null;
        }

        if (ship.getFleetMember() != null) {
            float crLoss = ship.getFleetMember().getDeployCost();
            ship.getFleetMember().getRepairTracker().applyCREvent(-crLoss, "Emergency Transverse Jump");
        }

        playWarpSounds();
        spawnWarpFlash();
        spawnWarpLightningStorm();
        ship.getFluxTracker().showOverloadFloatyIfNeeded("Emergency jump!", Color.WHITE, 2f, true);
    }

    private void playWarpSounds() {
        Vector2f loc = ship.getLocation();
        Vector2f vel = ship.getVelocity();
        Global.getSoundPlayer().playSound(FINAL_WARP_ID_0, 1.0f, 1f, loc, vel);
        Global.getSoundPlayer().playSound(FINAL_WARP_ID_1, 1.0f, 1f, loc, vel);
        Global.getSoundPlayer().playSound(FINAL_WARP_ID_2, 1.0f, 1f, loc, vel);
    }

    private void spawnWarpFlash() {
        Global.getCombatEngine().addHitParticle(
                ship.getLocation(),
                ZERO_VEL,
                ship.getCollisionRadius() * 4f,
                1f,
                0.1f,
                Color.WHITE
        );

        float shipRadius = ship.getCollisionRadius();
        RippleDistortion ripple = new RippleDistortion(ship.getLocation(), ZERO_VEL);
        ripple.setSize(shipRadius * 6f);
        ripple.setIntensity(175f);
        ripple.setFrameRate(60f / 1.5f);
        ripple.fadeInSize(1.0f);
        ripple.fadeOutIntensity(1.5f);
        ripple.setSize(shipRadius);
        DistortionShader.addDistortion(ripple);
    }

    private void handleWarpExecution(float amount) {
        if (ship.isHulk() || !ship.isAlive()) {
            cleanupModifiers();
            return;
        }

        warpFader.advance(amount);
        ship.setRetreating(true, false);

        float fadeValue = warpFader.getBrightness();
        ship.setExtraAlphaMult(fadeValue);
        ship.setJitter(this, WARP_COLOR, 1f - fadeValue, 30,
                ship.getCollisionRadius() * 2f * (1f - fadeValue));

        if (fadeValue > 0.1f && Math.random() < 0.3f) {
            spawnTunnelEffect(fadeValue);
        }

        if (warpFader.isFadedOut()) {
            completeWarp();
        }
    }

    private void spawnTunnelEffect(float fadeValue) {
        for (int i = 0; i < 3; i++) {
            Vector2f ringPoint = Misc.getPointAtRadius(ship.getLocation(),
                    ship.getCollisionRadius() * (2f + i));
            Global.getCombatEngine().addSmoothParticle(
                    ringPoint,
                    ZERO_VEL,
                    0f,
                    1f,
                    0.3f,
                    Misc.setAlpha(WARP_COLOR, (int)(255 * fadeValue * 0.6f))
            );
        }
    }

    private void completeWarp() {
        if (ship.isHulk() || !ship.isAlive()) {
            cleanupModifiers();
            return;
        }

        onWarpComplete();
        cleanupModifiers();
    }

    protected void cleanupModifiers() {
        if (chargingSound != null) {
            chargingSound.stop();
            chargingSound = null;
        }

        MutableShipStatsAPI stats = ship.getMutableStats();
        stats.getMaxSpeed().unmodifyMult(modId + "_charge");
        stats.getAcceleration().unmodifyMult(modId + "_charge");
        stats.getTurnAcceleration().unmodifyMult(modId + "_charge");
    }

    private void spawnWarpLightningStorm() {
        CombatEngineAPI engine = Global.getCombatEngine();
        Vector2f loc = ship.getLocation();
        float stormRange = ship.getCollisionRadius() * 6f;

        for (int i = 0; i < 10; i++) {
            createLightningArc(engine, loc, stormRange);
        }

        engine.addHitParticle(loc, ZERO_VEL, stormRange * 0.8f, 0.8f, 0.3f,
                new Color(220, 200, 255, 150));

        for (int i = 0; i < 15; i++) {
            createSpark(engine, loc);
        }
    }

    private void createLightningArc(CombatEngineAPI engine, Vector2f loc, float stormRange) {
        float angle1 = (float) (Math.random() * Math.PI * 2);
        float angle2 = (float) (Math.random() * Math.PI * 2);
        float distance1 = stormRange * (0.3f + 0.4f * (float) Math.random());
        float distance2 = stormRange * (0.6f + 0.4f * (float) Math.random());

        Vector2f startPoint = new Vector2f(
                loc.x + (float) Math.cos(angle1) * distance1,
                loc.y + (float) Math.sin(angle1) * distance1
        );
        Vector2f endPoint = new Vector2f(
                loc.x + (float) Math.cos(angle2) * distance2,
                loc.y + (float) Math.sin(angle2) * distance2
        );

        Vector2f midPoint = Vector2f.add(startPoint, endPoint, null);
        midPoint.scale(0.5f);
        midPoint.x += stormRange * 0.3f * ((float) Math.random() - 0.5f);
        midPoint.y += stormRange * 0.3f * ((float) Math.random() - 0.5f);

        float thickness = 8f + (float) Math.random() * 12f;

        engine.spawnEmpArcVisual(startPoint, ship, midPoint, ship, thickness, EMP_FRINGE_COLOR, EMP_CORE_COLOR);
        engine.spawnEmpArcVisual(midPoint, ship, endPoint, ship, thickness * 0.7f, EMP_FRINGE_COLOR, EMP_CORE_COLOR);

        if (Math.random() < 0.3f) {
            Vector2f branchPoint = Vector2f.add(midPoint, endPoint, null);
            branchPoint.scale(0.5f);
            branchPoint.x += stormRange * 0.2f * ((float) Math.random() - 0.5f);
            branchPoint.y += stormRange * 0.2f * ((float) Math.random() - 0.5f);

            engine.spawnEmpArcVisual(midPoint, ship, branchPoint, ship, thickness * 0.4f,
                    Misc.setAlpha(EMP_FRINGE_COLOR, 150), Misc.setAlpha(EMP_CORE_COLOR, 150));
        }
    }

    private void createSpark(CombatEngineAPI engine, Vector2f loc) {
        float sparkAngle = (float) (Math.random() * Math.PI * 2);
        Vector2f sparkVel = new Vector2f(
                (float) Math.cos(sparkAngle) * (200f + (float) Math.random() * 300f),
                (float) Math.sin(sparkAngle) * (200f + (float) Math.random() * 300f)
        );

        engine.addSmoothParticle(loc, sparkVel, 80f + (float) Math.random() * 120f,
                1.2f, 0.4f + (float) Math.random() * 0.6f, EMP_CORE_COLOR);
    }
}
