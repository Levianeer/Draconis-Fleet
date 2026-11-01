package levianeer.draconis.data.scripts.combat.probe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageListener;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * Phase 1 probe 1-2, run by the {@code xlii_probe_control} mission: every AI-side (owner 1) ship is
 * steered by a {@link ProbeSteering} beside its vanilla AI, and the whole line is moved through a fixed schedule under fire - hold a line
 * abreast ahead of the start, then shift it every {@link #PHASE_SECONDS}s sideways, back, across and
 * forward again. Answers, from the summary lines at the end of the battle:
 * <ul>
 *   <li>Does vanilla still fire and shield while we move the ship? The same measures are taken for
 *   the opposing side, which in the mirror flies identical hulls under plain vanilla AI.</li>
 *   <li>How well does raw-command steering arrive at and hold a point and a facing?</li>
 *   <li>What does steering cost per frame?</li>
 * </ul>
 * Removable once answered.
 */
public class ControlProbe extends BaseEveryFrameCombatPlugin implements DamageListener {

    private static final Logger log = Global.getLogger(ControlProbe.class);

    private static final int PROBE_SIDE = 1;
    private static final int VANILLA_SIDE = 0;
    private static final float FIRST_HOLD_SECONDS = 40f;
    private static final float PHASE_SECONDS = 20f;
    /** How far ahead of our starting centre the line is first placed. */
    private static final float FIRST_ADVANCE = 2500f;
    private static final float LINE_GAP = 400f;
    /** Line offsets after the first hold, {lateral, forward} relative to the starting axis. */
    private static final float[][] OFFSETS = {{0f, 0f}, {2000f, 0f}, {2000f, -1500f}, {-2000f, -1500f}, {-2000f, 0f}};
    /** Within this of its point a ship counts as arrived. */
    private static final float ARRIVED = 300f;
    /** Hold error is measured only after this long into a phase. */
    private static final float SETTLE_SECONDS = 10f;
    private static final float MAP_MARGIN = 1000f;

    private static class Stats {
        final int side;
        final String name;
        int inRange;
        int firing;
        int underFire;
        int shieldUnderFire;
        // probe side only
        final List<Float> arrivals = new ArrayList<>();
        int missed;
        boolean arrivedThisPhase;
        float holdSum;
        int holdN;
        float facingSum;
        int facingN;

        Stats(ShipAPI ship) {
            side = ship.getOwner();
            name = ship.getName();
        }
    }

    private final Map<ShipAPI, ProbeSteering> probes = new IdentityHashMap<>();
    private final Map<ShipAPI, Stats> stats = new IdentityHashMap<>();
    private final Map<ShipAPI, Float> slots = new IdentityHashMap<>();
    private final Set<ShipAPI> hit = Collections.newSetFromMap(new IdentityHashMap<ShipAPI, Boolean>());
    private final IntervalUtil second = new IntervalUtil(1f, 1f);
    private Vector2f origin;
    private Vector2f axis;
    private Vector2f perp;
    private float startedAt;
    private float nextSlot;
    private int phase = -1;
    private float phaseStart;
    private boolean reported;

    @Override
    public void init(CombatEngineAPI engine) {
        probes.clear();
        stats.clear();
        slots.clear();
        hit.clear();
        origin = axis = perp = null;
        nextSlot = 0f;
        phase = -1;
        reported = false;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;
        float t = engine.getTotalElapsedTime(false);

        List<ShipAPI> ours = new ArrayList<>();
        List<ShipAPI> theirs = new ArrayList<>();
        for (ShipAPI ship : engine.getShips()) {
            if (ship.isFighter() || ship.isStationModule() || !ship.isAlive() || ship.isHulk()) continue;
            if (!stats.containsKey(ship)) {
                stats.put(ship, new Stats(ship));
                ship.addListener(this);
            }
            if (ship.getOwner() == PROBE_SIDE) {
                ours.add(ship);
                if (!probes.containsKey(ship)) {
                    probes.put(ship, new ProbeSteering(ship));
                    log.info(String.format("DraconisProbe,steer,t=%.1f,ship=%s", t, ship.getName()));
                }
            } else if (ship.getOwner() == VANILLA_SIDE) {
                theirs.add(ship);
            }
        }

        if (origin == null && !ours.isEmpty() && !theirs.isEmpty()) setUpLine(engine, t, ours, theirs);
        if (origin != null) {
            int now = t < startedAt + FIRST_HOLD_SECONDS ? 0 : 1 + (int) ((t - startedAt - FIRST_HOLD_SECONDS) / PHASE_SECONDS);
            if (now != phase) startPhase(t, now);
            float[] offset = OFFSETS[phase % OFFSETS.length];
            for (ShipAPI ship : ours) {
                ProbeSteering ai = probes.get(ship);
                Float slot = slots.get(ship);
                if (slot == null) {
                    slot = nextSlot;
                    nextSlot += ship.getCollisionRadius() * 2f + LINE_GAP;
                    slots.put(ship, slot);
                }
                float lateral = offset[0] + slot;
                ai.point = clamp(engine, new Vector2f(origin.x + perp.x * lateral + axis.x * offset[1],
                        origin.y + perp.y * lateral + axis.y * offset[1]));
                ShipAPI enemy = nearest(ship, theirs);
                ai.facing = enemy != null ? Misc.getAngleInDegrees(ship.getLocation(), enemy.getLocation())
                        : Misc.getAngleInDegrees(axis);
                ai.advance();
            }
        }

        second.advance(amount);
        if (second.intervalElapsed()) sample(t, ours, theirs);

        if (!reported && engine.isCombatOver()) {
            reported = true;
            report();
        }
    }

    /** The line is centred on our side's starting centre, with slots running along the perpendicular. */
    private void setUpLine(CombatEngineAPI engine, float t, List<ShipAPI> ours, List<ShipAPI> theirs) {
        Vector2f a = centroid(ours);
        Vector2f b = centroid(theirs);
        axis = Misc.getUnitVector(a, b);
        perp = new Vector2f(-axis.y, axis.x);
        startedAt = t;
        List<ShipAPI> ordered = new ArrayList<>(ours);
        ordered.sort((x, y) -> Float.compare(lateral(x, a), lateral(y, a)));
        float width = 0f;
        for (ShipAPI ship : ordered) width += ship.getCollisionRadius() * 2f + LINE_GAP;
        float offset = -width / 2f;
        for (ShipAPI ship : ordered) {
            float w = ship.getCollisionRadius() * 2f + LINE_GAP;
            slots.put(ship, offset + w / 2f);
            offset += w;
        }
        nextSlot = offset;
        origin = new Vector2f(a.x + axis.x * FIRST_ADVANCE, a.y + axis.y * FIRST_ADVANCE);
        log.info(String.format("DraconisProbe,line,t=%.1f,ships=%d,width=%.0f", t, ours.size(), width));
    }

    private float lateral(ShipAPI ship, Vector2f centre) {
        return (ship.getLocation().x - centre.x) * perp.x + (ship.getLocation().y - centre.y) * perp.y;
    }

    private void startPhase(float t, int now) {
        if (phase >= 0) {
            for (Map.Entry<ShipAPI, ProbeSteering> entry : probes.entrySet()) {
                Stats s = stats.get(entry.getKey());
                if (entry.getKey().isAlive() && !s.arrivedThisPhase) s.missed++;
                s.arrivedThisPhase = false;
            }
        }
        phase = now;
        phaseStart = t;
        float[] offset = OFFSETS[phase % OFFSETS.length];
        log.info(String.format("DraconisProbe,phase,t=%.1f,phase=%d,lateral=%.0f,forward=%.0f", t, phase, offset[0], offset[1]));
    }

    private void sample(float t, List<ShipAPI> ours, List<ShipAPI> theirs) {
        for (ShipAPI ship : ours) sampleShip(ship, theirs);
        for (ShipAPI ship : theirs) sampleShip(ship, ours);
        if (origin != null) {
            for (ShipAPI ship : ours) {
                ProbeSteering ai = probes.get(ship);
                Stats s = stats.get(ship);
                if (ai == null || ai.point == null || ship.isRetreating()) continue;
                float dist = Misc.getDistance(ship.getLocation(), ai.point);
                float facingErr = Math.abs(MathUtils.getShortestRotation(ship.getFacing(), ai.facing));
                if (!s.arrivedThisPhase && dist <= ARRIVED) {
                    s.arrivedThisPhase = true;
                    s.arrivals.add(t - phaseStart);
                }
                if (t - phaseStart >= SETTLE_SECONDS) {
                    s.holdSum += dist;
                    s.holdN++;
                }
                s.facingSum += facingErr;
                s.facingN++;
                log.info(String.format("DraconisProbe,row,t=%.0f,ship=%s,phase=%d,dist=%.0f,facingErr=%.0f,speed=%.0f,flux=%.2f,shield=%d,hit=%d",
                        t, ship.getName(), phase, dist, facingErr, ship.getVelocity().length(), ship.getFluxLevel(),
                        shieldOn(ship) ? 1 : 0, hit.contains(ship) ? 1 : 0));
            }
        }
        hit.clear();
    }

    private void sampleShip(ShipAPI ship, List<ShipAPI> enemies) {
        Stats s = stats.get(ship);
        ShipAPI enemy = nearest(ship, enemies);
        if (enemy != null && edge(ship, enemy) <= gunRange(ship)) {
            s.inRange++;
            if (firing(ship)) s.firing++;
        }
        if (hit.contains(ship) && hasShield(ship)) {
            s.underFire++;
            if (shieldOn(ship)) s.shieldUnderFire++;
        }
    }

    private void report() {
        int[][] side = new int[2][4];
        for (Map.Entry<ShipAPI, Stats> entry : stats.entrySet()) {
            Stats s = entry.getValue();
            if (s.side != PROBE_SIDE && s.side != VANILLA_SIDE) continue;
            int[] sum = side[s.side];
            sum[0] += s.inRange;
            sum[1] += s.firing;
            sum[2] += s.underFire;
            sum[3] += s.shieldUnderFire;
            ProbeSteering ai = probes.get(entry.getKey());
            List<Float> arrivals = new ArrayList<>(s.arrivals);
            Collections.sort(arrivals);
            log.info(String.format("DraconisProbe,summary,side=%s,ship=%s,firing=%s,shieldUnderFire=%s,arriveMedian=%s,arrived=%d,missed=%d,"
                            + "holdErr=%s,facingErr=%s,usPerFrame=%s",
                    s.side == PROBE_SIDE ? "probe" : "vanilla", s.name, share(s.firing, s.inRange),
                    share(s.shieldUnderFire, s.underFire),
                    arrivals.isEmpty() ? "-" : String.format("%.0f", arrivals.get(arrivals.size() / 2)), arrivals.size(), s.missed,
                    s.holdN == 0 ? "-" : String.format("%.0f", s.holdSum / s.holdN),
                    s.facingN == 0 ? "-" : String.format("%.0f", s.facingSum / s.facingN),
                    ai == null || ai.steerFrames == 0 ? "-" : String.format("%.1f", ai.steerNanos / 1000f / ai.steerFrames)));
        }
        for (int i = 0; i < 2; i++) {
            log.info(String.format("DraconisProbe,sideSummary,side=%s,firing=%s,shieldUnderFire=%s",
                    i == PROBE_SIDE ? "probe" : "vanilla", share(side[i][1], side[i][0]), share(side[i][3], side[i][2])));
        }
    }

    @Override
    public void reportDamageApplied(Object source, CombatEntityAPI target, ApplyDamageResultAPI result) {
        if (target instanceof ShipAPI) hit.add((ShipAPI) target);
    }

    private static String share(int part, int whole) {
        return whole == 0 ? "-" : String.format("%.2f(%d)", part / (float) whole, whole);
    }

    private static boolean hasShield(ShipAPI ship) {
        ShieldAPI shield = ship.getShield();
        return shield != null && shield.getType() != ShieldAPI.ShieldType.NONE && shield.getType() != ShieldAPI.ShieldType.PHASE;
    }

    private static boolean shieldOn(ShipAPI ship) {
        return hasShield(ship) && ship.getShield().isOn();
    }

    private static boolean firing(ShipAPI ship) {
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (weapon.isDecorative() || weapon.getType() == WeaponAPI.WeaponType.MISSILE) continue;
            if (weapon.isFiring()) return true;
        }
        return false;
    }

    /** Longest non-missile range. */
    private static float gunRange(ShipAPI ship) {
        float max = 0f;
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (weapon.isDecorative() || weapon.getType() == WeaponAPI.WeaponType.MISSILE) continue;
            max = Math.max(max, weapon.getRange());
        }
        return max;
    }

    private static float edge(ShipAPI a, ShipAPI b) {
        return Misc.getDistance(a.getLocation(), b.getLocation()) - a.getCollisionRadius() - b.getCollisionRadius();
    }

    private static ShipAPI nearest(ShipAPI ship, List<ShipAPI> others) {
        ShipAPI best = null;
        float bestDist = Float.MAX_VALUE;
        for (ShipAPI other : others) {
            float d = Misc.getDistance(ship.getLocation(), other.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = other;
            }
        }
        return best;
    }

    private static Vector2f centroid(List<ShipAPI> ships) {
        float x = 0f;
        float y = 0f;
        for (ShipAPI ship : ships) {
            x += ship.getLocation().x;
            y += ship.getLocation().y;
        }
        return new Vector2f(x / ships.size(), y / ships.size());
    }

    private static Vector2f clamp(CombatEngineAPI engine, Vector2f point) {
        float halfW = engine.getMapWidth() / 2f - MAP_MARGIN;
        float halfH = engine.getMapHeight() / 2f - MAP_MARGIN;
        point.x = Math.max(-halfW, Math.min(halfW, point.x));
        point.y = Math.max(-halfH, Math.min(halfH, point.y));
        return point;
    }
}
