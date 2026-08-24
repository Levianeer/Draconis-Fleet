// Huge credit to Tartiflette!
// Largely based on the script from the Seeker mod
package levianeer.draconis.data.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.util.Misc;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Warp-in script for AI-controlled DDA capital ships.
 * Closely mirrors the SKR_warpDriveEffect boss warp-in from SEEKER_UC.
 * <p>
 * Flow: ship is held off-map and frozen -> once it has waited out warpDelay and its own
 * escorts are on the map, an arrival point is picked behind their line -> warpZone plays
 * for TELEGRAPHING seconds -> ship warps in.
 * <p>
 * Some capitals instead arrive off the enemy formation's left or right edge, beyond
 * weapons range, and close from the side. The chance is weighted by hull speed, so the
 * fleet's fastest capital is the one most likely to be sent wide.
 */
public class XLII_WarpInScript implements AdvanceableListener {

    private static final int   TELEGRAPHING    = 9;   // seconds for warp-zone build-up
    private static final int   DISTANCE        = 20000;
    private static final String ID             = "draconisWarpIn";
    private static final float MIN_DELAY       = 15f;  // minimum seconds held off-map before arriving
    private static final float MAX_DELAY       = 22f;  // maximum seconds held off-map before arriving
    private static final int   MIN_ESCORTS     = 3;    // own non-capitals on the map before arriving
    private static final float WARP_OFFSET     = 1200f; // distance behind own line to arrive at
    private static final float FLANK_CHANCE    = 0.50f; // flank chance for the fleet's fastest capital; scales to 0 at its slowest
    private static final float FLANK_RANGE     = 4000f; // distance beyond the enemy's edge to arrive at
    private static final float MIN_FLANK_RANGE = 2000f; // below this there is no room to flank; rally instead
    private static final float[] FLANK_Y_OFFSETS = {0f, 1500f, -1500f, 3000f, -3000f}; // tried in order for an unseen arrival
    private static final float MIN_SEPARATION  = 1000f; // minimum distance between warp-in destinations
    private static final float MAX_WAIT_TIME   = 45f;  // seconds before arriving regardless (fallback)
    private static final float ARRIVAL_CLEARANCE = 300f; // minimum gap between hulls at the arrival point
    private static final float MAP_EDGE_MARGIN   = 500f; // keep arrivals this far inside the battle map

    // Per-combat list of claimed warp destinations to prevent stacking
    private static CombatEngineAPI lastEngine;
    private static final List<Vector2f> claimedPositions = new ArrayList<>();

    private final ShipAPI ship;
    private final float   warpDelay; // randomized per instance
    private Boolean       flanking;  // rally with own line, or flank; decided at warp time
    private final float   flankSide; // -1 = left of the enemy formation, +1 = right

    private boolean activeWarp    = true;
    private boolean healNextFrame = false;
    private Vector2f warpTo       = null;
    private Vector2f initialPosition = null;
    private float    timer        = 0f;
    private float    heldFor      = 0f; // seconds this ship has waited off-map

    /** Collision classes stashed while the ship waits off-map, restored on arrival. */
    private final Map<ShipAPI, CollisionClass> heldCollision = new IdentityHashMap<>();

    public XLII_WarpInScript(ShipAPI ship) {
        this.ship = ship;
        this.warpDelay = MIN_DELAY + (float) (Math.random() * (MAX_DELAY - MIN_DELAY));
        this.flankSide = Math.random() < 0.5 ? -1f : 1f;
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
            exitHold();
            activeWarp = false;
            return;
        }

        // Clear claimed positions when a new battle starts. Must run before any branch
        // that claims a position, otherwise the first ship to advance claims against
        // the previous battle's list.
        if (engine != lastEngine) {
            lastEngine = engine;
            claimedPositions.clear();
        }

        // Capture deploy position once, before any teleport
        if (initialPosition == null) initialPosition = new Vector2f(ship.getLocation());

        // Player-controlled ship: immediate warp-in, no hold phase or telegraphing.
        // We do NOT move the ship to the holding position first - the audio listener follows
        // the player ship, so a detour to (0, -20000) would make all arrival sounds inaudible.
        // Instead, compute the arrival facing directly using the virtual holding position.
        if (ship.equals(engine.getPlayerShip())) {
            if (warpTo == null) {
                warpTo = findClearArrival(engine, new Vector2f(initialPosition));
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

        // Player-fleet AI ship: immediate warp-in behind deploy position, mirroring the piloted path.
        // No camera/sound offset needed since the camera follows the player ship, not this one.
        // Separation/claimed positions are ignored - each ship just warps to its own deploy point.
        if (ship.getOwner() == 0) {
            if (warpTo == null) {
                warpTo = findClearArrival(engine, new Vector2f(initialPosition.x, initialPosition.y - 750f));
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
        } else {
            heldFor += amount;
            // Wait for the rest of the fleet. Arriving before the line has formed drops a
            // lone capital into the middle of the enemy; MAX_WAIT_TIME covers the case
            // where the escorts are dead or the fleet is capitals only.
            List<ShipAPI> escorts = findEscorts(engine);
            boolean lineFormed = heldFor > warpDelay && escorts.size() >= MIN_ESCORTS;
            if (lineFormed || heldFor > MAX_WAIT_TIME) {
                // Deferred to here: ranking this capital against the others needs the
                // fleet roster, which does not exist yet when the script is constructed.
                if (flanking == null) flanking = rollFlanking(engine);
                warpTo = flanking
                        ? findFlankLocation(engine, escorts)
                        : findWarpLocation(rallyPoint(escorts));
            }
            // Hold off-map and freeze until then
            moveToLocation(holdingPosition(), holdingFacing(), 0f);
            freeze(ship.getMutableStats());
            enterHold();
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
        exitHold();
        // The destination was picked when the telegraph started; ships (and the ship this
        // one is arriving next to) have moved since, so re-check it before materialising.
        if (!isClearArrival(engine, warpTo)) {
            warpTo = findClearArrival(engine, warpTo);
        }
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

    /**
     * This ship's own deployed non-capital warships that are actually on the battle map.
     * Excludes fighters, modules, and any other capital still waiting in the off-map hold.
     */
    private List<ShipAPI> findEscorts(CombatEngineAPI engine) {
        List<ShipAPI> result = new ArrayList<>();
        for (ShipAPI other : engine.getShips()) {
            if (other.equals(ship) || other.getOwner() != ship.getOwner()) continue;
            if (other.getHullSize() == HullSize.CAPITAL_SHIP) continue;
            if (!isOnMap(engine, other)) continue;
            result.add(other);
        }
        return result;
    }

    /** Everything this ship is fighting that is actually on the map, capitals included. */
    private List<ShipAPI> findEnemies(CombatEngineAPI engine) {
        List<ShipAPI> result = new ArrayList<>();
        for (ShipAPI other : engine.getShips()) {
            if (other.getOwner() == ship.getOwner()) continue;
            if (!isOnMap(engine, other)) continue;
            result.add(other);
        }
        return result;
    }

    /** A live, independent warship inside the battle map - not a fighter, module or hulk. */
    private boolean isOnMap(CombatEngineAPI engine, ShipAPI other) {
        if (!other.isAlive() || other.isHulk() || other.isFighter()) return false;
        if (other.getParentStation() != null) return false;
        Vector2f loc = other.getLocation();
        return Math.abs(loc.x) <= engine.getMapWidth()  / 2f
            && Math.abs(loc.y) <= engine.getMapHeight() / 2f;
    }

    /** Centre of this ship's own line, or its deploy point if there is no line left. */
    private Vector2f rallyPoint(List<ShipAPI> escorts) {
        if (escorts.isEmpty()) return new Vector2f(initialPosition);
        Vector2f sum = new Vector2f();
        for (ShipAPI escort : escorts) {
            Vector2f.add(sum, escort.getLocation(), sum);
        }
        sum.scale(1f / escorts.size());
        return sum;
    }

    /**
     * Arrival point: WARP_OFFSET behind the rally point, on this fleet's own side, so the
     * capital materialises at the back of its line and advances with it. Anchoring on an
     * enemy ship instead - as this used to - dropped capitals inside the enemy formation.
     * Multiple capitals are spread along the X axis so they never claim the same spot.
     */
    private Vector2f findWarpLocation(Vector2f rally) {
        float ownSide = ship.getOwner() == 1 ? 1f : -1f;
        float xOffset = spreadOffset();
        Vector2f result = findClearArrival(Global.getCombatEngine(), new Vector2f(
                rally.x + xOffset + MathUtils.getRandomNumberInRange(-300, 300),
                rally.y + WARP_OFFSET * ownSide
        ));
        claimedPositions.add(result);
        return result;
    }


    /**
     * Decides whether this capital flanks, weighted by how fast it is relative to the other
     * capitals its side has deployed: the fastest rolls the full FLANK_CHANCE, the slowest
     * never flanks. A slow hull sent wide spends most of the battle in transit.
     */
    private boolean rollFlanking(CombatEngineAPI engine) {
        float mine = baseSpeed(ship);
        float slowest = mine, fastest = mine;
        for (ShipAPI other : engine.getShips()) {
            if (other.equals(ship) || other.getOwner() != ship.getOwner()) continue;
            if (other.getHullSize() != HullSize.CAPITAL_SHIP) continue;
            if (!other.isAlive() || other.isHulk() || other.getParentStation() != null) continue;
            float speed = baseSpeed(other);
            slowest = Math.min(slowest, speed);
            fastest = Math.max(fastest, speed);
        }
        // Lone capital, or a fleet of identically fast hulls: nothing to rank against.
        float weight = (fastest - slowest < 1f) ? 1f : (mine - slowest) / (fastest - slowest);
        return Math.random() < FLANK_CHANCE * weight;
    }

    /**
     * Hull base speed. getMaxSpeed() is unusable here: freeze() zeroes it via modifyMult
     * while a capital waits off-map, which is exactly when the ranking has to happen.
     */
    private float baseSpeed(ShipAPI other) {
        return other.getMutableStats().getMaxSpeed().getBaseValue();
    }

    /**
     * Arrival point for a flanking warp-in: FLANK_RANGE beyond the enemy formation's left
     * or right edge and level with it, so the ship comes in outside weapons range and has
     * to close from the side. Falls back to the rally point if nothing is on the map yet.
     */
    private Vector2f findFlankLocation(CombatEngineAPI engine, List<ShipAPI> escorts) {
        List<ShipAPI> enemies = findEnemies(engine);
        if (enemies.isEmpty()) return findWarpLocation(rallyPoint(escorts));

        // Take whichever side has more room between the enemy's edge and the map border,
        // measured against the same bound clampToMap uses so the two cannot disagree.
        float halfW = Math.max(0f, engine.getMapWidth() / 2f - ship.getCollisionRadius() - MAP_EDGE_MARGIN);
        float side = flankSide;
        float room      = halfW - side * flankEdge(enemies,  side);
        float otherRoom = halfW + side * flankEdge(enemies, -side);
        if (otherRoom > room) {
            side = -side;
            room = otherRoom;
        }

        // Use as much of FLANK_RANGE as the map allows. If even the roomier side cannot fit
        // a meaningful flank, arrive with the line instead of being clamped onto the border.
        float range = Math.min(FLANK_RANGE, room);
        if (range < MIN_FLANK_RANGE) return findWarpLocation(rallyPoint(escorts));

        float sumY = 0f;
        for (ShipAPI enemy : enemies) sumY += enemy.getLocation().y;

        // Spread multiple flankers along the flank line rather than across it
        float x = flankEdge(enemies, side) + side * range;
        float y = sumY / enemies.size() + spreadOffset();

        // Prefer a point the enemy cannot currently see, so the flanker is not tracked in.
        for (float dy : FLANK_Y_OFFSETS) {
            Vector2f candidate = clampToMap(engine, new Vector2f(x, y + dy));
            if (isClearArrival(engine, candidate) && isHiddenFromEnemy(engine, candidate)) {
                claimedPositions.add(candidate);
                return candidate;
            }
        }

        // Nothing unseen was free - arriving in view beats not arriving.
        Vector2f result = findClearArrival(engine, new Vector2f(x, y));
        claimedPositions.add(result);
        return result;
    }

    /** True if the enemy side's fog of war does not currently cover this point. */
    private boolean isHiddenFromEnemy(CombatEngineAPI engine, Vector2f loc) {
        FogOfWarAPI fow = engine.getFogOfWar(ship.getOwner() == 0 ? 1 : 0);
        return fow == null || !fow.isVisible(loc);
    }

    /** Outermost edge of the enemy formation on the given side (-1 = left, +1 = right). */
    private float flankEdge(List<ShipAPI> enemies, float side) {
        float edge = -Float.MAX_VALUE;
        for (ShipAPI enemy : enemies) {
            edge = Math.max(edge, side * (enemy.getLocation().x + side * enemy.getCollisionRadius()));
        }
        return side * edge;
    }

    /** Offset for the Nth claimed arrival: 0, +MIN_SEP, −MIN_SEP, +2×MIN_SEP, … */
    private float spreadOffset() {
        int idx = claimedPositions.size();
        if (idx == 0) return 0f;
        return ((idx + 1) / 2) * MIN_SEPARATION * ((idx % 2 == 1) ? 1f : -1f);
    }

    /** Keeps an arrival point inside the battle map, hull and all. */
    private Vector2f clampToMap(CombatEngineAPI engine, Vector2f loc) {
        float halfW = Math.max(0f, engine.getMapWidth()  / 2f - ship.getCollisionRadius() - MAP_EDGE_MARGIN);
        float halfH = Math.max(0f, engine.getMapHeight() / 2f - ship.getCollisionRadius() - MAP_EDGE_MARGIN);
        return new Vector2f(
                Math.max(-halfW, Math.min(halfW, loc.x)),
                Math.max(-halfH, Math.min(halfH, loc.y)));
    }

    /**
     * True if this ship can materialise at loc without overlapping another hull.
     * Checks every ship in the engine, modules included - station modules are what
     * capitals were arriving on top of during station battles.
     */
    private boolean isClearArrival(CombatEngineAPI engine, Vector2f loc) {
        for (ShipAPI other : engine.getShips()) {
            if (other.equals(ship) || other.isFighter() || other.isHulk()) continue;
            if (ship.equals(other.getParentStation())) continue; // this ship's own modules
            float needed = ship.getCollisionRadius() + other.getCollisionRadius() + ARRIVAL_CLEARANCE;
            if (MathUtils.getDistanceSquared(loc, other.getLocation()) < needed * needed) return false;
        }
        return true;
    }

    /** Nearest point to desired that is inside the map and clear of other hulls. */
    private Vector2f findClearArrival(CombatEngineAPI engine, Vector2f desired) {
        Vector2f base = clampToMap(engine, desired);
        if (isClearArrival(engine, base)) return base;

        float step = ship.getCollisionRadius() + ARRIVAL_CLEARANCE;
        for (int ring = 1; ring <= 6; ring++) {
            for (int i = 0; i < 8; i++) {
                Vector2f candidate = clampToMap(engine,
                        MathUtils.getPoint(desired, step * ring, i * 45f + ring * 22.5f));
                if (isClearArrival(engine, candidate)) return candidate;
            }
        }
        return base;
    }

    /**
     * Makes the ship intangible while it waits off-map. Without this it is still a valid
     * target 20,000u away - invisible, but lockable and damageable.
     */
    private void enterHold() {
        if (!heldCollision.isEmpty()) return;
        heldCollision.put(ship, ship.getCollisionClass());
        ship.setCollisionClass(CollisionClass.NONE);
        List<ShipAPI> modules = ship.getChildModulesCopy();
        if (modules != null) {
            for (ShipAPI module : modules) {
                heldCollision.put(module, module.getCollisionClass());
                module.setCollisionClass(CollisionClass.NONE);
            }
        }
    }

    private void exitHold() {
        for (Map.Entry<ShipAPI, CollisionClass> entry : heldCollision.entrySet()) {
            entry.getKey().setCollisionClass(entry.getValue());
        }
        heldCollision.clear();
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