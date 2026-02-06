package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SoundAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

public class XLII_DraconisHull extends BaseHullMod {

    // Stats
    public static final float DEGRADE_INCREASE_PERCENT = 25f;

    private static final Map<HullSize, Float> CHARGE_TIMES = new EnumMap<>(HullSize.class);
    static {
        CHARGE_TIMES.put(HullSize.FRIGATE, 5f);
        CHARGE_TIMES.put(HullSize.DESTROYER, 6f);
        CHARGE_TIMES.put(HullSize.CRUISER, 7f);
        CHARGE_TIMES.put(HullSize.CAPITAL_SHIP, 9f);
    }

    private static final float CR_LOSS_ON_WARP = 1f;
    private static final String MOD_ID = "draconisHull";

    public static float getChargeTime(HullSize hullSize) {
        return CHARGE_TIMES.getOrDefault(hullSize, 8f);
    }

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // Combat
        stats.getCRLossPerSecondPercent().modifyPercent(id, DEGRADE_INCREASE_PERCENT);
    }

    public static class WarpDriveScript implements AdvanceableListener {

        private final ShipAPI ship;
        private final float chargeTime;
        private final String modId;

        // State variables
        private boolean warpCharging = false;
        private boolean warpExecuting = false;
        private float chargeProgress = 0f;
        private float soundCooldown = 0f;
        private SoundAPI chargingSound = null;

        // Overload arc generation
        private final IntervalUtil overloadArcInterval = new IntervalUtil(0.075f, 0.125f);
        private int arcCounter = 0;

        // Effects
        private final FaderUtil warpFader = new FaderUtil(0.5f, 0.5f);

        // Constants
        private static final Color WARP_COLOR = new Color(100, 150, 255, 255);
        private static final Color EMP_CORE_COLOR = new Color(200, 150, 255, 200);
        private static final Color EMP_FRINGE_COLOR = new Color(180, 100, 255, 150);
        private static final Color BLUE_CORE_COLOR = new Color(150, 200, 255, 200);
        private static final Color BLUE_FRINGE_COLOR = new Color(100, 150, 255, 150);
        private static final Color WHITE = Color.WHITE;
        private static final Vector2f ZERO_VEL = new Vector2f(0, 0);

        // Sound IDs
        private static final String EMP_IMPACT_ID = "draconis_venting_or_overloaded";
        private static final String CHARGE_WARP_ID = "mote_attractor_loop_dark";
        private static final String FINAL_WARP_ID_0 = "system_tenebrous_expulsion_activate";
        private static final String FINAL_WARP_ID_1 = "energy_lash_fire";
        private static final String FINAL_WARP_ID_2 = "system_nova_burst_fire";

        public WarpDriveScript(ShipAPI ship) {
            this.ship = ship;
            this.chargeTime = getChargeTime(ship.getHullSize());
            this.modId = MOD_ID + "_" + ship.getId();
        }

        @Override
        public void advance(float amount) {
            // Safety checks: cleanup if ship is invalid, dead, or hulk
            if (ship == null || !Global.getCombatEngine().isEntityInPlay(ship) ||
                    ship.isHulk() || !ship.isAlive() || ship.isPiece()) {
                cleanupModifiers();
                return;
            }

            if (soundCooldown > 0f) {
                soundCooldown -= amount;
            }

            // Check overload state and handle effects
            handleOverloadEffects(amount);

            boolean shouldRetreat = checkShouldRetreat();

            // Handle state transitions
            if (shouldRetreat && !warpCharging && !warpExecuting) {
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

        private void handleOverloadEffects(float amount) {
            // Include both overload and venting
            boolean isOverloadedOrVenting = ship.getFluxTracker().isOverloaded() || ship.getFluxTracker().isVenting();

            // Spawn arcs at intervals while overloaded or venting
            if (isOverloadedOrVenting) {
                overloadArcInterval.advance(amount * 0.125f);
                if (overloadArcInterval.intervalElapsed()) {
                    spawnOverloadArc();
                }
            }
        }

        private boolean checkShouldRetreat() {
            // Only trigger on "Direct Retreat", not normal "Retreat"
            // ShipAPI.isDirectRetreat() returns true only when the ship has been given the "Direct Retreat" order
            return ship.isDirectRetreat();
        }

        private void startWarpCharge() {
            warpCharging = true;
            chargeProgress = 0f;
            ship.setRetreating(false, false);

            // Visual and audio feedback
            ship.getFluxTracker().showOverloadFloatyIfNeeded("Calculating drive...", WARP_COLOR, 4f, true);
            chargingSound = Global.getSoundPlayer().playSound(CHARGE_WARP_ID, 1f, 1f, ship.getLocation(), ship.getVelocity());
        }

        private void handleCharging(float amount) {
            // Safety check: prevent dead ship from completing charge
            if (ship.isHulk() || !ship.isAlive()) {
                cleanupModifiers();
                return;
            }

            chargeProgress += amount;
            float chargePercent = Math.min(chargeProgress / chargeTime, 1f);

            // Apply movement penalties
            applyChargePenalties(chargePercent);

            // Block movement commands
            blockMovementCommands();

            // Visual effects
            if (Math.random() < 0.15f) {
                spawnChargingParticles();
            }

            if (Math.random() < (0.05f + 0.1f * chargePercent)) {
                spawnChargingLightningFlicker(chargePercent);
            }

            // Jitter effect
            ship.setJitter(this, WARP_COLOR, chargePercent, (int)(10 * chargePercent),
                    ship.getCollisionRadius() * chargePercent * 0.5f);

            // Check completion
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

        private void completeCharge() {
            warpCharging = false;
            warpExecuting = true;
            warpFader.fadeOut();

            // Stop charging sound
            if (chargingSound != null) {
                chargingSound.stop();
                chargingSound = null;
            }

            // Apply CR loss
            if (ship.getFleetMember() != null) {
                float crLoss = CR_LOSS_ON_WARP * ship.getFleetMember().getDeployCost();
                ship.getFleetMember().getRepairTracker().applyCREvent(-crLoss, "Emergency Transverse Jump");
            }

            // Effects
            playWarpSounds();
            spawnWarpFlash();
            spawnWarpLightningStorm();
            ship.getFluxTracker().showOverloadFloatyIfNeeded("Emergency jump!", WHITE, 2f, true);
        }

        private void playWarpSounds() {
            Vector2f loc = ship.getLocation();
            Vector2f vel = ship.getVelocity();
            Global.getSoundPlayer().playSound(FINAL_WARP_ID_0, 1.0f, 1f, loc, vel);
            Global.getSoundPlayer().playSound(FINAL_WARP_ID_1, 1.0f, 1f, loc, vel);
            Global.getSoundPlayer().playSound(FINAL_WARP_ID_2, 1.0f, 1f, loc, vel);
        }

        private void spawnWarpFlash() {
            // Original flash particle for visual impact
            Global.getCombatEngine().addHitParticle(
                    ship.getLocation(),
                    ZERO_VEL,
                    ship.getCollisionRadius() * 4f,
                    1f,
                    0.1f,
                    WHITE
            );

            // GraphicsLib radial distortion ring (expanding shockwave warp effect)
            float shipRadius = ship.getCollisionRadius();
            final RippleDistortion ripple = getRippleDistortion(shipRadius);

            DistortionShader.addDistortion(ripple);
        }

        private RippleDistortion getRippleDistortion(float shipRadius) {
            float finalSize = shipRadius * 6f;
            float intensity = 175f; // Subtle distortion strength (100-300 range)
            float duration = 1.5f; // 1.5 second total effect
            float expansionTime = 1.0f; // Ring expands over 1 second
            float fadeTime = 1.5f; // Intensity fades over full duration

            RippleDistortion ripple = new RippleDistortion(ship.getLocation(), ZERO_VEL);
            ripple.setSize(finalSize);
            ripple.setIntensity(intensity);
            ripple.setFrameRate(60f / duration); // 40 fps animation
            ripple.fadeInSize(expansionTime); // Expanding wavefront
            ripple.fadeOutIntensity(fadeTime); // Fading distortion
            ripple.setSize(shipRadius); // Reset to starting size after setting final size
            return ripple;
        }

        private void handleWarpExecution(float amount) {
            // Safety check: don't teleport a dead ship
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
            // Final safety check: only teleport if ship is still alive
            if (ship.isHulk() || !ship.isAlive()) {
                cleanupModifiers();
                return;
            }

            CombatEngineAPI engine = Global.getCombatEngine();

            if (engine != null) {
                CombatFleetManagerAPI fleetManager = engine.getFleetManager(ship.getOwner());
                if (fleetManager != null) {
                    fleetManager.removeDeployed(ship, true);
                }
            }

            cleanupModifiers();
        }

        private void cleanupModifiers() {
            if (chargingSound != null) {
                chargingSound.stop();
                chargingSound = null;
            }

            MutableShipStatsAPI stats = ship.getMutableStats();
            stats.getMaxSpeed().unmodifyMult(modId + "_charge");
            stats.getAcceleration().unmodifyMult(modId + "_charge");
            stats.getTurnAcceleration().unmodifyMult(modId + "_charge");
        }

        private void spawnChargingLightningFlicker(float chargePercent) {
            if (soundCooldown > 0f) return;

            Vector2f loc = ship.getLocation();
            float range = ship.getCollisionRadius() * (1.0f + 1.5f * chargePercent);

            // Generate random points
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

        private void spawnWarpLightningStorm() {
            CombatEngineAPI engine = Global.getCombatEngine();
            Vector2f loc = ship.getLocation();
            float stormRange = ship.getCollisionRadius() * 6f;
            int numArcs = 10;

            for (int i = 0; i < numArcs; i++) {
                createLightningArc(engine, loc, stormRange);
            }

            // Central explosion
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

            // Occasional branching
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

        // Overload arc effect
        private void spawnOverloadArc() {
            CombatEngineAPI engine = Global.getCombatEngine();
            Vector2f shipLoc = ship.getLocation();

            float maxOffset = 150f;

            // Calculate size-based flux multiplier
            float sizeMultiplier = 0.5f; // Default fallback
            HullSize hullSize = ship.getHullSize();
            if (hullSize != null) {
                sizeMultiplier = switch (hullSize) {
                    case FRIGATE -> 0.33f;
                    case DESTROYER -> 0.44f;
                    case CRUISER -> 0.66f;
                    case CAPITAL_SHIP -> 1.0f;
                    default -> sizeMultiplier;
                };
            }

            // Create arc parameters
            EmpArcEntityAPI.EmpArcParams params = new EmpArcEntityAPI.EmpArcParams();
            params.segmentLengthMult = 4f;
            params.glowSizeMult = (5f + ship.getFluxLevel() * 2f) * sizeMultiplier;
            params.flickerRateMult = 0.5f + (float) Math.random() * 0.5f;

            // Alternate between pink and blue colors
            boolean useBlue = (arcCounter % 2 == 0);
            Color fringeColor = useBlue ? BLUE_FRINGE_COLOR : EMP_FRINGE_COLOR;
            Color coreColor = useBlue ? BLUE_CORE_COLOR : EMP_CORE_COLOR;
            arcCounter++;

            // Fixed thickness
            float thickness = 40f;
            float coreThickness = 20f;

            // Generate random points around the ship
            float r = maxOffset * (0.5f + 0.5f * (float) Math.random());
            Vector2f from = Misc.getPointAtRadius(shipLoc, r);

            float angle = Misc.getAngleInDegrees(from, shipLoc);
            angle = angle + 90f * ((float) Math.random() - 0.5f);
            Vector2f dir = Misc.getUnitVectorAtDegreeAngle(angle);

            float dist = maxOffset * (0.5f + 0.5f * (float) Math.random()) * 1.5f;
            dir.scale(dist);
            Vector2f to = Vector2f.add(from, dir, new Vector2f());

            // Spawn the arc
            EmpArcEntityAPI arc = engine.spawnEmpArcVisual(
                    from, ship, to, ship,
                    thickness,
                    fringeColor,
                    coreColor,
                    params
            );

            arc.setCoreWidthOverride(coreThickness);

            // Play sound with every arc
            Global.getSoundPlayer().playSound(EMP_IMPACT_ID, 1f, 1f, to, ship.getVelocity());
        }
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ship.addListener(new WarpDriveScript(ship));
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        return switch (index) {
            case 0 -> CHARGE_TIMES.get(HullSize.FRIGATE).intValue() + "s";
            case 1 -> CHARGE_TIMES.get(HullSize.DESTROYER).intValue() + "s";
            case 2 -> CHARGE_TIMES.get(HullSize.CRUISER).intValue() + "s";
            case 3 -> CHARGE_TIMES.get(HullSize.CAPITAL_SHIP).intValue() + "s";
            case 4 -> (int) CR_LOSS_ON_WARP + Strings.X;
            default -> null;
        };
    }

    @Override
    public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return false;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        Color t = Misc.getTextColor();

        tooltip.addPara("Draconis-built hulls have a number of shared properties.", opad);

        tooltip.addSectionHeading("Retreat", Alignment.MID, opad);
        tooltip.addPara("When Draconis ships receive %s orders, they will initiate an emergency Transverse Jump. This takes %s/%s/%s/%s to fully charge, depending on hull size.",
                opad, h,
                "Direct Retreat",
                CHARGE_TIMES.get(HullSize.FRIGATE).intValue() + "s",
                CHARGE_TIMES.get(HullSize.DESTROYER).intValue() + "s",
                CHARGE_TIMES.get(HullSize.CRUISER).intValue() + "s",
                CHARGE_TIMES.get(HullSize.CAPITAL_SHIP).intValue() + "s");

        tooltip.addPara("Ship counts as retreated upon completion and emerges post-combat, suffering CR penalty equal to %s of deployment cost. Once started, this cannot be aborted.",
                opad, h,
                (int) CR_LOSS_ON_WARP + Strings.X);

        tooltip.addSectionHeading("Maintenance", new Color(255,100,0) , new Color(105,40,0,175) , Alignment.MID, opad);
        tooltip.addPara("Draconis-built hulls require excessive maintenance and do not stand up well under the rigours of prolonged engagements.", opad);

        tooltip.addPara("Increases the rate of in-combat CR decay after peak performance time runs out by %s.",
                opad, h,
                (int) DEGRADE_INCREASE_PERCENT + "%");
    }

    @Override
    public int getDisplaySortOrder() {
        return 0;
    }

    @Override
    public int getDisplayCategoryIndex() {
        return 0;
    }
}