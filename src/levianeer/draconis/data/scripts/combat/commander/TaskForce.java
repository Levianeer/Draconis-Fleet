package levianeer.draconis.data.scripts.combat.commander;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.scripts.combat.commander.WorldModel.Unit;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * One task force: every ship the commander controls. It gives ships intent, not formation slots: it
 * picks the enemy group it beats locally and moves the group's anchor to a standoff from it, or moves
 * the anchor away to regroup; each ship gets a minimum gap from its nearest threat (kiting just outside
 * it when we out-range it, stepping out of reach to recover flux) and the anchor to stay near. Vanilla
 * flies the ships, and chooses their targets, inside that intent. See {@code draconis-commander.md},
 * Phase 2 restructure and strip-down.
 */
final class TaskForce {

    private static final Logger log = Global.getLogger(TaskForce.class);

    enum Mode { ATTACK, REGROUP }

    /** Local strength at which the force goes after a fight. */
    private static final float ACCEPT = 1.0f;
    /** Local strength down to which a fight already under way is kept. The gap to {@link #ACCEPT} is
     * wide on purpose: at 0.9 / 0.75 the ratio's swings flipped attack and regroup every few seconds. */
    private static final float KEEP = 0.6f;
    /** Minimum time in a mode before the other may be chosen. */
    private static final float MIN_MODE_SECONDS = 10f;
    /** Another group must score this many times the current one to switch to it. */
    private static final float SWITCH = 1.3f;
    /** An enemy this far beyond its own reach of a cluster can still join that fight. */
    private static final float SUPPORT_MARGIN = 1500f;
    /** An enemy within its reach of one of ours plus this counts as threatening it. */
    private static final float THREAT_MARGIN = 300f;
    private static final float DISTANCE_PENALTY = 6000f;
    /** A ship that does not out-range its threat fights from about this fraction of its gun range. */
    private static final float STANDOFF = 0.85f;
    /** Out-ranging the threat by at least this makes a ship kite: stay outside its reach, inside ours. */
    private static final float KITE_EDGE = 250f;
    /** Kiting ships keep this far outside the threat's reach. */
    private static final float KITE_OUTSIDE = 100f;
    private static final float RECOVER_AT = 0.7f;
    private static final float RECOVERED_AT = 0.35f;
    /** A recovering ship steps this far outside its threat's reach to vent. */
    private static final float RECOVER_CLEARANCE = 300f;
    private static final float VENT_AT = 0.4f;
    /** Within this beyond either side's reach of the nearest enemy, a ship is in contact. */
    private static final float CONTACT_MARGIN = 1500f;
    private static final float COHESION = 2000f;
    private static final float REGROUP_DISTANCE = 1500f;
    private static final float LINE_GAP = 300f;
    private static final float MAP_MARGIN = 800f;

    private final WorldModel world;
    Mode mode = Mode.REGROUP;
    private float modeSince = -Float.MAX_VALUE;
    /** One ship of the enemy group being fought; clusters are rebuilt every tick, so it is tracked by a member. */
    ShipAPI clusterShip;
    List<Unit> cluster = Collections.emptyList();
    float clusterRatio;
    float threatRatio;
    boolean threatened;
    Vector2f anchor;
    final Set<ShipAPI> recovering = Collections.newSetFromMap(new IdentityHashMap<ShipAPI, Boolean>());
    private float lastDecide = -1f;

    TaskForce(WorldModel world) {
        this.world = world;
    }

    /** Every decision tick, after the world model is rebuilt. */
    void decide(float t, Map<ShipAPI, ShipControl> controls) {
        recovering.removeIf(ship -> !ship.isAlive() || ship.isHulk());
        List<Unit> members = world.ours;
        if (members.isEmpty()) return;
        Vector2f centre = WorldModel.centroid(members);
        if (anchor == null) anchor = new Vector2f(centre);
        float dt = lastDecide < 0f ? 0f : t - lastDecide;
        lastDecide = t;

        Set<Unit> threats = new LinkedHashSet<>();
        Set<Unit> engaging = new LinkedHashSet<>();
        for (Unit m : members) {
            threats.addAll(world.enemiesReaching(m.loc(), THREAT_MARGIN + m.ship.getCollisionRadius()));
            engaging.addAll(world.enemiesReaching(m.loc(), SUPPORT_MARGIN));
        }
        threatened = !threats.isEmpty();
        threatRatio = engaging.isEmpty() ? Float.MAX_VALUE : WorldModel.ratio(members, engaging);

        chooseFight(t, members, centre);
        moveAnchor(dt, members, centre);
        assign(members, controls);
    }

    /** Keep the current fight while it holds, switch for a clearly better one, else regroup. */
    private void chooseFight(float t, List<Unit> members, Vector2f centre) {
        List<Unit> current = clusterShip == null || !clusterShip.isAlive() ? null : clusterOf(clusterShip);
        boolean settled = t - modeSince >= MIN_MODE_SECONDS;

        List<Unit> best = null;
        float bestRatio = 0f;
        float bestScore = -1f;
        float currentRatio = 0f;
        float currentScore = -1f;
        for (List<Unit> c : world.clusters) {
            Vector2f cc = WorldModel.centroid(c);
            float r = WorldModel.ratio(members, world.enemiesReaching(cc, SUPPORT_MARGIN));
            float score = r / (1f + Misc.getDistance(centre, cc) / DISTANCE_PENALTY);
            if (c == current) {
                currentRatio = r;
                currentScore = score;
            }
            if (score > bestScore) {
                bestScore = score;
                bestRatio = r;
                best = c;
            }
        }

        boolean keep = current != null && (currentRatio >= KEEP || !settled)
                && !(best != current && bestScore > currentScore * SWITCH);
        if (keep) {
            cluster = current;
            clusterRatio = currentRatio;
            setMode(t, Mode.ATTACK, "keep, ratio " + fmt(currentRatio));
        } else if (best != null && bestRatio >= ACCEPT && (mode == Mode.ATTACK || settled)) {
            cluster = best;
            clusterRatio = bestRatio;
            clusterShip = nearestMember(best, centre);
            setMode(t, Mode.ATTACK, "best ratio " + fmt(bestRatio));
        } else if (mode == Mode.REGROUP || settled || current == null) {
            clusterShip = null;
            cluster = Collections.emptyList();
            clusterRatio = bestRatio;
            setMode(t, Mode.REGROUP, "best ratio " + fmt(bestRatio));
        }
    }

    private static ShipAPI nearestMember(List<Unit> c, Vector2f point) {
        Unit best = null;
        float bestDist = Float.MAX_VALUE;
        for (Unit u : c) {
            float d = Misc.getDistance(point, u.loc());
            if (d < bestDist) {
                bestDist = d;
                best = u;
            }
        }
        return best == null ? null : best.ship;
    }

    private void setMode(float t, Mode next, String why) {
        if (next == mode) return;
        log.info(String.format("DraconisCommander,mode,t=%.1f,from=%s,to=%s,why=%s,clusterRatio=%s,threatRatio=%s",
                t, mode, next, why, fmt(clusterRatio), fmt(threatRatio)));
        mode = next;
        modeSince = t;
    }

    /**
     * ATTACK: the anchor heads for a point on our side of the target group, at the group's standoff
     * from it. REGROUP: away from the enemy's centre. Either way it moves no faster than the slowest
     * member, so the group stays together.
     */
    private void moveAnchor(float dt, List<Unit> members, Vector2f centre) {
        Vector2f desired;
        if (mode == Mode.ATTACK && !cluster.isEmpty()) {
            Vector2f cc = WorldModel.centroid(cluster);
            float reach = 0f;
            for (Unit u : cluster) reach = Math.max(reach, u.gunRange);
            float standoff = 0f;
            for (Unit m : members) {
                standoff += m.gunRange >= reach + KITE_EDGE ? (reach + m.gunRange) / 2f : m.gunRange * STANDOFF;
            }
            standoff /= members.size();
            Vector2f dir = Misc.getUnitVector(cc, centre);
            dir.scale(standoff + meanRadius(cluster) + meanRadius(members));
            desired = Vector2f.add(cc, dir, null);
        } else if (!world.theirs.isEmpty()) {
            Vector2f away = Misc.getUnitVector(WorldModel.centroid(world.theirs), centre);
            away.scale(REGROUP_DISTANCE);
            desired = Vector2f.add(centre, away, null);
        } else {
            desired = new Vector2f(centre);
        }
        clamp(desired);
        float step = slowest(members) * dt;
        float d = Misc.getDistance(anchor, desired);
        if (d <= step || dt <= 0f) {
            anchor.set(desired);
        } else {
            Vector2f move = Misc.getUnitVector(anchor, desired);
            move.scale(step);
            Vector2f.add(anchor, move, anchor);
        }
    }

    /** Intent for every member: contact, minimum gap, anchor, formation slot, venting. */
    private void assign(List<Unit> members, Map<ShipAPI, ShipControl> controls) {
        Vector2f toward = !cluster.isEmpty() ? WorldModel.centroid(cluster)
                : world.theirs.isEmpty() ? null : WorldModel.centroid(world.theirs);
        float heading = toward == null ? members.get(0).ship.getFacing() : Misc.getAngleInDegrees(anchor, toward);
        Map<ShipAPI, Vector2f> slots = lineSlots(members, anchor, heading);

        for (Unit m : members) {
            ShipControl c = controls.get(m.ship);
            if (c == null) continue;
            float flux = m.ship.getFluxLevel();
            if (flux >= RECOVER_AT) recovering.add(m.ship);
            else if (flux <= RECOVERED_AT) recovering.remove(m.ship);
            boolean recover = recovering.contains(m.ship);

            Unit threat = nearestEnemy(m);
            float gap = threat == null ? Float.MAX_VALUE : MathUtils.getDistance(m.ship, threat.ship);
            c.threat = threat == null ? null : threat.ship;
            c.inContact = threat != null && gap <= Math.max(m.gunRange, threat.gunRange) + CONTACT_MARGIN;

            boolean kite = threat != null && m.gunRange >= threat.gunRange + KITE_EDGE;
            if (threat == null) c.minGap = 0f;
            else if (recover) c.minGap = threat.gunRange + RECOVER_CLEARANCE;
            else if (kite) c.minGap = threat.gunRange + KITE_OUTSIDE;
            else c.minGap = 0f;


            c.anchor = anchor;
            c.cohesion = COHESION;
            c.slot = slots.get(m.ship);
            c.slotFacing = heading;
            c.ventAllowed = flux >= VENT_AT && world.enemiesReaching(m.loc(), THREAT_MARGIN + m.ship.getCollisionRadius()).isEmpty();
        }
    }

    /** A line abreast through {@code centre}, perpendicular to {@code heading}, in the order ships already stand. */
    private static Map<ShipAPI, Vector2f> lineSlots(List<Unit> members, Vector2f centre, float heading) {
        final Vector2f perp = Misc.getUnitVectorAtDegreeAngle(heading + 90f);
        List<Unit> ordered = new ArrayList<>(members);
        ordered.sort((a, b) -> Float.compare(Vector2f.dot(a.loc(), perp), Vector2f.dot(b.loc(), perp)));
        float total = 0f;
        for (Unit m : ordered) total += m.ship.getCollisionRadius() * 2f + LINE_GAP;
        float offset = -total / 2f;
        Map<ShipAPI, Vector2f> slots = new IdentityHashMap<>();
        for (Unit m : ordered) {
            float w = m.ship.getCollisionRadius() * 2f + LINE_GAP;
            offset += w / 2f;
            slots.put(m.ship, clamp(new Vector2f(centre.x + perp.x * offset, centre.y + perp.y * offset)));
            offset += w / 2f;
        }
        return slots;
    }

    private List<Unit> clusterOf(ShipAPI ship) {
        for (List<Unit> c : world.clusters) {
            for (Unit u : c) if (u.ship == ship) return c;
        }
        return null;
    }

    private Unit nearestEnemy(Unit m) {
        Unit best = null;
        float bestDist = Float.MAX_VALUE;
        for (Unit u : world.theirs) {
            float d = Misc.getDistance(m.loc(), u.loc());
            if (d < bestDist) {
                bestDist = d;
                best = u;
            }
        }
        return best;
    }

    private static float meanRadius(List<Unit> units) {
        float sum = 0f;
        for (Unit u : units) sum += u.ship.getCollisionRadius();
        return sum / units.size();
    }

    private static float slowest(List<Unit> members) {
        float min = Float.MAX_VALUE;
        for (Unit m : members) min = Math.min(min, m.speed);
        return min;
    }

    private static Vector2f clamp(Vector2f p) {
        CombatEngineAPI engine = Global.getCombatEngine();
        float halfW = engine.getMapWidth() / 2f - MAP_MARGIN;
        float halfH = engine.getMapHeight() / 2f - MAP_MARGIN;
        p.x = Math.max(-halfW, Math.min(halfW, p.x));
        p.y = Math.max(-halfH, Math.min(halfH, p.y));
        return p;
    }

    static String fmt(float ratio) {
        return ratio >= 1000f ? "inf" : String.format("%.2f", ratio);
    }
}
