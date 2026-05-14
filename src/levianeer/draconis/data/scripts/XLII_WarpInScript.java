// Huge credit to Tartiflette!
// Largely based on the script from the Seeker mod
package levianeer.draconis.data.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.util.Misc;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Warp-in script for AI-controlled DDA capital ships.
 * Closely mirrors the SKR_warpDriveEffect boss warp-in from SEEKER_UC.
 * <p>
 * Flow: ship is held off-map and frozen -> after warpDelay seconds a target is found ->
 * warpTo is computed near that target -> warpZone plays for TELEGRAPHING seconds -> ship warps in.
 */
public class XLII_WarpInScript implements AdvanceableListener {

    private static final int   TELEGRAPHING    = 9;   // seconds for warp-zone build-up
    private static final int   DISTANCE        = 20000;
    private static final String ID             = "draconisWarpIn";
    private static final float MIN_DELAY       = 6f;   // minimum seconds before looking for a target
    private static final float MAX_DELAY       = 9f;  // maximum seconds before looking for a target
    private static final float WARP_OFFSET     = 3000f; // distance north of target for enemy warp-in
    private static final float MIN_SEPARATION  = 1000f; // minimum distance between warp-in destinations
    private static final float MAX_WAIT_TIME   = 12f;  // seconds before warping in without a target (fallback)

    // Per-combat list of claimed warp destinations to prevent stacking
    private static CombatEngineAPI lastEngine;
    private static final List<Vector2f> claimedPositions = new ArrayList<>();

    private final ShipAPI ship;
    private final float   warpDelay; // randomized per instance

    private boolean activeWarp    = true;
    private boolean healNextFrame = false;
    private ShipAPI  target       = null;
    private Vector2f warpTo       = null;
    private Vector2f initialPosition = null;
    private float    timer        = 0f;

    public XLII_WarpInScript(ShipAPI ship) {
        this.ship = ship;
        this.warpDelay = MIN_DELAY + (float) (Math.random() * (MAX_DELAY - MIN_DELAY));
    }

    @Override
    public void advance(float amount) {
        if (healNextFrame) {
            healShip();
            healNextFrame = false;
            activeWarp = false;
            return;
        }
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused() || !activeWarp) return;
        if (!ship.isAlive() || ship.isHulk()) {
            activeWarp = false;
            return;
        }

        // Capture deploy position once, before any teleport
        if (initialPosition == null) initialPosition = new Vector2f(ship.getLocation());

        // Player-controlled ship: immediate warp-in, no hold phase or telegraphing.
        // We do NOT move the ship to the holding position first - the audio listener follows
        // the player ship, so a detour to (0, -20000) would make all arrival sounds inaudible.
        // Instead, compute the arrival facing directly using the virtual holding position.
        if (ship.equals(engine.getPlayerShip())) {
            if (warpTo == null) {
                warpTo = new Vector2f(initialPosition.x, initialPosition.y);
                claimedPositions.add(warpTo);
            }
            // Facing computed as if arriving from the virtual holding position (south of map).
            float arrivalFacing = VectorUtils.getAngle(holdingPosition(), warpTo);
            // Teleport to final position first so all visuals are aligned with the ship.
            // Sounds are redirected to initialPosition (camera location this frame) so they remain audible.
            moveToLocation(warpTo, arrivalFacing, 300f);
            unfreeze(ship.getMutableStats());
            ship.turnOffTravelDrive();
            ship.turnOnTravelDrive(1);
            landWings(ship);
            warpVisualEffect(engine, new Vector2f(initialPosition));
            applyArrivalShockwave(engine);
            ship.getVelocity().set(0f, 0f);
            healNextFrame = true;
            return;
        }

        // Clear claimed positions when a new battle starts
        if (engine != lastEngine) {
            lastEngine = engine;
            claimedPositions.clear();
        }

        // Player-fleet AI ship: immediate warp-in behind deploy position, mirroring the piloted path.
        // No camera/sound offset needed since the camera follows the player ship, not this one.
        // Separation/claimed positions are ignored - each ship just warps to its own deploy point.
        if (ship.getOwner() == 0) {
            if (warpTo == null) {
                warpTo = new Vector2f(initialPosition.x, initialPosition.y - 750f);
            }
            float arrivalFacing = VectorUtils.getAngle(holdingPosition(), warpTo);
            moveToLocation(warpTo, arrivalFacing, 300f);
            unfreeze(ship.getMutableStats());
            ship.turnOffTravelDrive();
            ship.turnOnTravelDrive(2);
            landWings(ship);
            warpVisualEffect(engine);
            applyArrivalShockwave(engine);
            ship.getVelocity().set(0f, 0f);
            healNextFrame = true;
            return;
        }

        if (warpTo != null) {
            // Telegraphing phase - timer runs 0 -> 1 over TELEGRAPHING seconds
            if (timer < 1f) {
                timer += amount / (float) TELEGRAPHING;
                warpZone(engine, timer, warpTo);
            } else {
                warpIn(engine);
                healNextFrame = true;
                timer = 0f;
            }
        } else if (target != null && target.isAlive()) {
            warpTo = findWarpLocation(target);
        } else {
            if (engine.getTotalElapsedTime(false) > warpDelay) {
                target = findSuitableTarget(engine);
            }
            // Fallback: no target after MAX_WAIT_TIME, warp to initial deploy position
            if (target == null && engine.getTotalElapsedTime(false) > MAX_WAIT_TIME) {
                warpTo = new Vector2f(initialPosition);
                claimedPositions.add(warpTo);
            }
            // Hold off-map and freeze while waiting for a target
            moveToLocation(holdingPosition(), holdingFacing(), 0f);
            freeze(ship.getMutableStats());
        }
    }

    // -------------------------------------------------------------------------
    // Core mechanics
    // -------------------------------------------------------------------------

    /** Holding position: enemy ships wait above the map, player ships below. */
    private Vector2f holdingPosition() {
        return new Vector2f(0f, ship.getOwner() == 1 ? DISTANCE : -DISTANCE);
    }

    /** Holding facing: point toward the battle while waiting. */
    private float holdingFacing() {
        return ship.getOwner() == 1 ? 270f : 90f;
    }

    private void freeze(MutableShipStatsAPI stats) {
        stats.getMaxSpeed().modifyMult(ID, 0f);
        stats.getAcceleration().modifyMult(ID, 0f);
        stats.getDeceleration().modifyMult(ID, 0f);
        stats.getTurnAcceleration().modifyMult(ID, 0f);
        stats.getMaxTurnRate().modifyMult(ID, 0f);
        stats.getFluxCapacity().modifyMult(ID, 0f);
        stats.getFluxDissipation().modifyMult(ID, 0f);
    }

    private void unfreeze(MutableShipStatsAPI stats) {
        stats.getMaxSpeed().unmodify(ID);
        stats.getAcceleration().unmodify(ID);
        stats.getDeceleration().unmodify(ID);
        stats.getTurnAcceleration().unmodify(ID);
        stats.getMaxTurnRate().unmodify(ID);
        stats.getFluxCapacity().unmodify(ID);
        stats.getFluxDissipation().unmodify(ID);
    }

    private void moveToLocation(Vector2f loc, float facing, float speed) {
        ship.setFacing(facing);
        ship.getLocation().set(loc);
        ship.getVelocity().set(MathUtils.getPoint(new Vector2f(), speed, facing));
    }

    private void warpIn(CombatEngineAPI engine) {
        moveToLocation(warpTo, VectorUtils.getAngle(ship.getLocation(), warpTo), 300f);
        unfreeze(ship.getMutableStats());
        ship.turnOffTravelDrive();
        ship.turnOnTravelDrive(2);

        landWings(ship);
        if (ship.isShipWithModules()) {
            for (ShipAPI module : ship.getChildModulesCopy()) {
                module.turnOffTravelDrive();
                module.turnOnTravelDrive(3);
                landWings(module);
            }
        }

        warpVisualEffect(engine);
        applyArrivalShockwave(engine);
    }

    private void applyArrivalShockwave(CombatEngineAPI engine) {
        float halfMass = ship.getMass() / 2f;
        float radius = ship.getCollisionRadius() * 6f;
        Vector2f loc = ship.getLocation();
        for (ShipAPI nearby : CombatUtils.getShipsWithinRange(loc, radius)) {
            if (nearby.equals(ship) || nearby.isHulk()) continue;
            if (nearby.getOwner() == ship.getOwner()) continue;
            float dist = MathUtils.getDistance(loc, nearby.getLocation());
            float fraction = Math.max(0f, 1f - dist / radius);
            if (fraction <= 0f) continue;
            engine.applyDamage(nearby, nearby.getLocation(), halfMass * fraction, DamageType.KINETIC, 0f, false, false, ship);
        }
    }

    private void landWings(ShipAPI carrier) {
        if (carrier.getAllWings().isEmpty()) return;
        for (FighterWingAPI wing : carrier.getAllWings()) {
            for (ShipAPI fighter : wing.getWingMembers()) {
                fighter.getWing().getSource().makeCurrentIntervalFast();
                fighter.getWing().getSource().land(fighter);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Target / location selection
    // -------------------------------------------------------------------------

    /** Returns the fleet side that is the "enemy" from this ship's perspective. */
    private FleetSide enemySide() {
        return ship.getOwner() == 1 ? FleetSide.PLAYER : FleetSide.ENEMY;
    }

    /**
     * Mirrors SKR_warpDriveEffect.findSuitableTarget - prefers the player flagship,
     * otherwise finds the largest deployed ship on the opposing side.
     */
    private ShipAPI findSuitableTarget(CombatEngineAPI engine) {
        FleetSide side = enemySide();
        if (engine.getFleetManager(side).getDeployedCopy().isEmpty()) return null;

        if (side == FleetSide.PLAYER && engine.getPlayerShip() != null && engine.getPlayerShip().isAlive()) {
            return engine.getPlayerShip();
        }

        Map<HullSize, FleetMemberAPI> bySize = new WeakHashMap<>();
        for (FleetMemberAPI m : engine.getFleetManager(side).getDeployedCopy()) {
            ShipAPI s = engine.getFleetManager(side).getShipFor(m);
            if (s == null || !s.isAlive()) continue;
            HullSize sz = m.getHullSpec().getHullSize();
            if (!bySize.containsKey(sz) || Math.random() > 0.5f) {
                bySize.put(sz, m);
            }
        }

        for (HullSize sz : new HullSize[]{HullSize.CAPITAL_SHIP, HullSize.CRUISER, HullSize.DESTROYER, HullSize.FRIGATE}) {
            if (bySize.containsKey(sz)) return engine.getFleetManager(side).getShipFor(bySize.get(sz));
        }
        return null;
    }

    /**
     * Mirrors SKR_warpDriveEffect.findWarpLocation - returns a point 2000-3000u toward
     * this fleet's spawn side from the target, once the target is visible to this fleet.
     * Retries up to 5 times to avoid placing the arrival point too close to another capital.
     */
    private Vector2f findWarpLocation(ShipAPI target) {
        if (target == null) return null;

        // Place ships in a guaranteed-separate line along the X axis.
        // index 0 -> target X, 1 -> +MIN_SEP, 2 -> −MIN_SEP, 3 -> +2×MIN_SEP, …
        int idx = claimedPositions.size();
        float xOffset;
        if (idx == 0) {
            xOffset = 0f;
        } else {
            int n = (idx + 1) / 2;
            float xSide = (idx % 2 == 1) ? 1f : -1f;
            xOffset = n * MIN_SEPARATION * xSide;
        }
        Vector2f result = new Vector2f(
                target.getLocation().x + xOffset + MathUtils.getRandomNumberInRange(-300, 300),
                target.getLocation().y + WARP_OFFSET
        );
        claimedPositions.add(result);
        return result;
    }


    @SuppressWarnings("unused")
    private boolean isClearOfClaimed(Vector2f candidate) {
        float minSepSq = MIN_SEPARATION * MIN_SEPARATION;
        for (Vector2f claimed : claimedPositions) {
            if (MathUtils.getDistanceSquared(candidate, claimed) < minSepSq) return false;
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Effects
    // -------------------------------------------------------------------------

    /**
     * Per-frame build-up effect at the arrival location. Styled after the Transverse Jump
     * charge sequence: WARP_COLOR glow, converging EMP arcs, contracting particle ring.
     * Also physically pushes nearby ships away when pushAway=true.
     */
    private void warpZone(CombatEngineAPI engine, float intensity, Vector2f location) {
        // Push nearby ships radially outward - mirrors SEEKER push formula (PUSH=1_000_000)
        for (ShipAPI s : CombatUtils.getShipsWithinRange(location, intensity * 750f)) {
            if (s.equals(ship) || s.isHulk()) continue;
            Vector2f vel = s.getVelocity();
            float mag = engine.getElapsedInLastFrame()
                    * Math.min(50f, intensity * 1_000_000f / (1f + MathUtils.getDistanceSquared(location, s.getLocation())));
            Vector2f.add(vel, MathUtils.getPoint(new Vector2f(), mag, VectorUtils.getAngle(location, s.getLocation())), vel);
        }

        // Central WARP_COLOR glow - builds from faint to blinding
        if (Math.random() < 0.15f + intensity * 0.35f) {
            engine.addHitParticle(
                    location,
                    new Vector2f(),
                    MathUtils.getRandomNumberInRange(100f, 200f + 600f * intensity),
                    0.3f + 0.7f * intensity,
                    MathUtils.getRandomNumberInRange(0.05f, 0.1f + 0.1f * intensity),
                    Misc.setAlpha(XLII_TransverseJumpScript.WARP_COLOR, (int) (50 + 205f * intensity))
            );
        }

        // Inward-pulling particles - spawn radius contracts as intensity rises
        if (Math.random() < 0.3f + intensity * 0.5f) {
            float spawnRadius = Math.max(50f, 500f - 400f * intensity);
            Vector2f offset = MathUtils.getRandomPointInCircle(new Vector2f(), spawnRadius);
            float len = (float) Math.sqrt(offset.x * offset.x + offset.y * offset.y);
            float speed = MathUtils.getRandomNumberInRange(100f + 200f * intensity, 300f + 400f * intensity);
            Vector2f vel = len > 0f
                    ? new Vector2f(-offset.x / len * speed, -offset.y / len * speed)
                    : new Vector2f();
            engine.addHitParticle(
                    Vector2f.add(location, offset, new Vector2f()),
                    vel,
                    MathUtils.getRandomNumberInRange(4f, 8f + 8f * intensity),
                    0.6f + 0.4f * intensity,
                    MathUtils.getRandomNumberInRange(0.3f, 0.6f + 0.4f * intensity),
                    Misc.setAlpha(XLII_TransverseJumpScript.EMP_CORE_COLOR, (int) (80 + 175f * intensity))
            );
        }

        // EMP arc storm clustered around the arrival point - two-segment arcs, both endpoints random
        if (Math.random() < 0.08f + 0.2f * intensity) {
            float stormRadius = 300f + 200f * intensity;
            float variability = stormRadius * 0.5f;
            float a1 = (float) (Math.random() * Math.PI * 2);
            float a2 = (float) (Math.random() * Math.PI * 2);
            float d1 = stormRadius * (0.1f + 0.4f * (float) Math.random());
            float d2 = stormRadius * (0.4f + 0.6f * (float) Math.random());
            Vector2f arcStart = new Vector2f(
                    location.x + (float) Math.cos(a1) * d1,
                    location.y + (float) Math.sin(a1) * d1);
            Vector2f arcEnd = new Vector2f(
                    location.x + (float) Math.cos(a2) * d2,
                    location.y + (float) Math.sin(a2) * d2);
            Vector2f mid = Vector2f.add(Vector2f.add(arcStart, arcEnd, null),
                    new Vector2f(variability * ((float) Math.random() - 0.5f),
                                 variability * ((float) Math.random() - 0.5f)), null);
            mid.scale(0.5f);
            float thickness = 3f + 4f * intensity;
            engine.spawnEmpArcVisual(arcStart, null, mid, null, thickness,
                    Misc.setAlpha(XLII_TransverseJumpScript.EMP_FRINGE_COLOR, (int) (100 + 155f * intensity)),
                    Misc.setAlpha(XLII_TransverseJumpScript.EMP_CORE_COLOR, (int) (150 + 105f * intensity)));
            engine.spawnEmpArcVisual(mid, null, arcEnd, null, thickness * 0.8f,
                    Misc.setAlpha(XLII_TransverseJumpScript.EMP_FRINGE_COLOR, (int) (100 + 155f * intensity)),
                    Misc.setAlpha(XLII_TransverseJumpScript.EMP_CORE_COLOR, (int) (150 + 105f * intensity)));
        }

        // Contracting outer ring - sparks that tighten toward the center over time
        if (Math.random() < 0.15f + 0.2f * intensity) {
            float ringRadius = Math.max(30f, 350f * (1.1f - intensity));
            Vector2f ringPoint = MathUtils.getPoint(location,
                    MathUtils.getRandomNumberInRange(ringRadius * 0.8f, ringRadius * 1.2f),
                    (float) (Math.random() * 360f));
            engine.addSmoothParticle(
                    ringPoint,
                    new Vector2f(),
                    MathUtils.getRandomNumberInRange(10f, 25f + 25f * intensity),
                    0.5f + 0.5f * intensity,
                    MathUtils.getRandomNumberInRange(0.1f, 0.2f + 0.15f * intensity),
                    Misc.setAlpha(XLII_TransverseJumpScript.WARP_COLOR, (int) (80 + 120f * intensity))
            );
        }

        // Charging sound loop - matches the Transverse Jump charge-up audio
        Global.getSoundPlayer().playLoop(
                "mote_attractor_loop_dark",
                engine.getPlayerShip(), 0.5f + 0.5f * intensity, 0.5f + 0.5f * intensity,
                location, new Vector2f()
        );
    }

    private void warpVisualEffect(CombatEngineAPI engine) {
        warpVisualEffect(engine, null);
    }

    /** soundLoc overrides where arrival sounds play; pass null to use the ship's current location. */
    private void warpVisualEffect(CombatEngineAPI engine, Vector2f soundLoc) {
        Vector2f loc = new Vector2f(ship.getLocation());
        Vector2f vel = new Vector2f(ship.getVelocity());

        // Flash + GraphicsLib ripple
        engine.addHitParticle(loc, new Vector2f(), ship.getCollisionRadius() * 4f, 1f, 0.1f, Color.WHITE);
        float r = ship.getCollisionRadius();
        RippleDistortion ripple = new RippleDistortion(loc, vel);
        ripple.setSize(r * 6f);
        ripple.setIntensity(175f);
        ripple.setFrameRate(60f / 1.5f);
        ripple.fadeInSize(1.0f);
        ripple.fadeOutIntensity(1.5f);
        ripple.setSize(r);
        DistortionShader.addDistortion(ripple);

        // Arrival sounds - play at soundLoc (camera position) if provided, otherwise at ship location
        Vector2f sndLoc = soundLoc != null ? soundLoc : loc;
        Global.getSoundPlayer().playSound("system_tenebrous_expulsion_activate", 1f, 1f, sndLoc, vel);
        Global.getSoundPlayer().playSound("energy_lash_fire", 1f, 1f, sndLoc, vel);
        Global.getSoundPlayer().playSound("system_nova_burst_fire", 1f, 1f, sndLoc, vel);

        // Lightning storm
        float stormRange = r * 6f;
        for (int i = 0; i < 10; i++) createLightningArc(engine, loc, stormRange);
        engine.addHitParticle(loc, new Vector2f(), stormRange * 0.8f, 0.8f, 0.3f, new Color(220, 200, 255, 150));
        for (int i = 0; i < 15; i++) {
            float angle = (float) (Math.random() * Math.PI * 2);
            engine.addSmoothParticle(loc,
                    new Vector2f((float) Math.cos(angle) * (200f + (float) Math.random() * 300f),
                                 (float) Math.sin(angle) * (200f + (float) Math.random() * 300f)),
                    80f + (float) Math.random() * 120f, 1.2f, 0.4f + (float) Math.random() * 0.6f,
                    XLII_TransverseJumpScript.EMP_CORE_COLOR);
        }

        // Afterimage trail - ship appears to have burst in from its facing direction
        for (int i = 1; i < 50; i++) {
            Vector2f offset   = MathUtils.getPoint(new Vector2f(), (float) (i * (i + 5)), ship.getFacing() + 180f);
            Vector2f trailVel = MathUtils.getPoint(new Vector2f(), 5f * i - 4f, ship.getFacing());
            float    duration = 5.1f - (0.1f * i);
            Color    color    = new Color(0.5f, 0.25f, 1f, 0.15f);

            for (ShipAPI module : ship.getChildModulesCopy()) {
                if (module.isAlive()) {
                    module.addAfterimage(color, offset.x, offset.y, trailVel.x, trailVel.y,
                            0.1f, 0f, 0.1f, duration, false, true, false);
                }
            }
            ship.addAfterimage(color, offset.x, offset.y, trailVel.x, trailVel.y,
                    0.1f, 0f, 0.1f, duration, false, true, false);
        }
    }

    private void healShip() {
        ship.setHitpoints(ship.getMaxHitpoints());
        ArmorGridAPI armor = ship.getArmorGrid();
        for (int x = 0; x < armor.getGrid().length; x++) {
            for (int y = 0; y < armor.getGrid()[x].length; y++) {
                armor.setArmorValue(x, y, armor.getMaxArmorInCell());
            }
        }
    }

    private void createLightningArc(CombatEngineAPI engine, Vector2f loc, float stormRange) {
        float a1 = (float) (Math.random() * Math.PI * 2);
        float a2 = (float) (Math.random() * Math.PI * 2);
        float d1 = stormRange * (0.3f + 0.4f * (float) Math.random());
        float d2 = stormRange * (0.6f + 0.4f * (float) Math.random());
        Vector2f start = new Vector2f(loc.x + (float) Math.cos(a1) * d1, loc.y + (float) Math.sin(a1) * d1);
        Vector2f end   = new Vector2f(loc.x + (float) Math.cos(a2) * d2, loc.y + (float) Math.sin(a2) * d2);
        Vector2f mid   = Vector2f.add(start, end, null);
        mid.scale(0.5f);
        mid.x += stormRange * 0.3f * ((float) Math.random() - 0.5f);
        mid.y += stormRange * 0.3f * ((float) Math.random() - 0.5f);
        float thickness = 8f + (float) Math.random() * 12f;
        engine.spawnEmpArcVisual(start, null, mid, null, thickness,
                XLII_TransverseJumpScript.EMP_FRINGE_COLOR, XLII_TransverseJumpScript.EMP_CORE_COLOR);
        engine.spawnEmpArcVisual(mid, null, end, null, thickness * 0.7f,
                XLII_TransverseJumpScript.EMP_FRINGE_COLOR, XLII_TransverseJumpScript.EMP_CORE_COLOR);
    }
}