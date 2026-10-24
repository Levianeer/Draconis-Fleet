// Huge credit to Genir!
// Largely based on the Fleet Cohesion AI from the AI Tweaks mod (Unlicense)
package levianeer.draconis.data.scripts.combat.carrierdoctrine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.FogOfWarAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

/**
 * Keeps our big ships on the enemy's main body instead of chasing stragglers to the map edge.
 * Ported from the decompiled bytecode of AI Tweaks' {@code FleetCohesionAI} and
 * {@code FleetSegmentation}; see {@code .claude/systems/adaptive-admiral.md} for what it does and
 * why it was adopted.
 * <p>
 * The enemy fleet is clustered: ships within {@link #LINK_RANGE} of each other form a group, but a
 * frigate cannot link two groups. The largest group by DP is the main body. Every ~1s, each big ship
 * of ours that holds no order is checked: if it is attacking something in the main body, or
 * something in a substantial group close by, it is left alone. Otherwise it gets a
 * {@code RALLY_TASK_FORCE} waypoint on the nearest big main-body ship. Every order is removed again
 * on the next tick, so none outlives a second and it never touches an order it did not give.
 * <p>
 * Differences from AI Tweaks, all deliberate: it runs for the AI side (theirs is player-only) and in
 * the simulator (theirs is off there); the attack target is read from the public
 * {@code AIFlags.MANEUVER_TARGET} rather than through bytecode remapping; and the admiral only offers
 * it hulls the personality slide has at steady or above, so a hull judged to be losing is never
 * sent back at the enemy.
 * <p>
 * One addition of our own: a hull the slide has at cautious or timid that has drifted out of our
 * own main body is sent back to it instead, to a point behind its centre. Excluding pulled-back
 * hulls alone left them backing away from enemies with nothing pulling them towards friends, and
 * in play they were picked off alone more often than they were saved.
 */
public class FleetCohesion {

    private static final Logger log = Global.getLogger(FleetCohesion.class);

    private static final float LINK_RANGE = 2000f;
    /** A group counts as worth fighting if it holds a capital or at least this share of the main
     * body's DP. */
    private static final float VALID_GROUP_DP_FRACTION = 0.25f;

    private final int owner;
    /** False evaluates and logs every redirect without issuing it, for A/B against the slide alone. */
    private final boolean acting;
    private final IntervalUtil interval = new IntervalUtil(0.75f, 1.0f);
    private final List<Issued> issued = new ArrayList<>();
    private final Map<ShipAPI, Active> active = new IdentityHashMap<>();
    /** Hulls whose order from us was removed this tick; see {@link #isFree}. */
    private final Set<ShipAPI> freedThisTick =
            Collections.newSetFromMap(new IdentityHashMap<ShipAPI, Boolean>());
    private List<ShipAPI> ourMain = Collections.emptyList();

    private static final String REDIRECT = "redirect";
    private static final String FALLBACK = "fallback";
    /** How far behind our main body's centre a pulled-back hull is sent: half the grouping range, so
     * the point sits inside the fleet's footprint. */
    private static final float FALLBACK_OFFSET = 1000f;
    /** Below this the two fleets' centres are too close to say which side is the front. */
    private static final float MIN_FRONT_SEPARATION = 1000f;
    private static final float MAP_MARGIN = 1000f;

    private static class Active {
        final String kind;
        final float since;
        final String from;
        final String to;

        Active(String kind, float since, String from, String to) {
            this.kind = kind;
            this.since = since;
            this.from = from;
            this.to = to;
        }
    }

    private static class Issued {
        final ShipAPI ship;
        final AssignmentTargetAPI waypoint;
        final Vector2f location;

        Issued(ShipAPI ship, AssignmentTargetAPI waypoint) {
            this.ship = ship;
            this.waypoint = waypoint;
            this.location = new Vector2f(waypoint.getLocation());
        }
    }

    public FleetCohesion(int owner, boolean acting) {
        this.owner = owner;
        this.acting = acting;
    }

    /** Ships currently being steered at the enemy main body, for the fleet log line. */
    public int redirected() {
        return count(REDIRECT);
    }

    /** Pulled-back ships currently being steered back to our own main body. */
    public int fallingBack() {
        return count(FALLBACK);
    }

    private int count(String kind) {
        int n = 0;
        for (Active a : active.values()) if (a.kind.equals(kind)) n++;
        return n;
    }

    /** Distance from {@code ship} to the nearest member of our main body as of the last tick: 0 for
     * a member, -1 when there is no main body. For measuring drift, which was only ever seen. */
    public float distanceToMainBody(ShipAPI ship) {
        if (ourMain.isEmpty()) return -1f;
        if (ourMain.contains(ship)) return 0f;
        float best = Float.MAX_VALUE;
        for (ShipAPI member : ourMain) best = Math.min(best, Misc.getDistance(ship.getLocation(), member.getLocation()));
        return best;
    }

    /**
     * Every frame. {@code forward} are hulls the admiral will let be sent at the enemy main body;
     * {@code pulledBack} are hulls the slide has at cautious or timid, which instead fall back to
     * ours if they have drifted off it; {@code ours} is our whole side, for finding our main body;
     * {@code enemies} is every deployed enemy hull.
     */
    public void advance(float amount, float elapsed, List<ShipAPI> ours, List<ShipAPI> forward,
                        List<ShipAPI> pulledBack, List<ShipAPI> enemies) {
        interval.advance(amount);
        if (!interval.intervalElapsed()) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        CombatTaskManagerAPI tasks = engine.getFleetManager(owner).getTaskManager(false);
        clearIssued(engine, tasks);
        ourMain = ours.isEmpty() ? Collections.<ShipAPI>emptyList() : segment(ours).get(0);

        Map<ShipAPI, Active> now = new IdentityHashMap<>();
        if (allowed(engine, tasks) && !enemies.isEmpty()) {
            List<List<ShipAPI>> groups = segment(enemies);
            List<ShipAPI> primary = groups.get(0);
            List<ShipAPI> valid = validTargets(groups);
            FogOfWarAPI fog = engine.getFogOfWar(owner);

            for (ShipAPI ship : forward) {
                if (!eligible(engine, tasks, ship)) continue;
                ShipAPI target = attackTarget(ship);
                if (target == null || primary.contains(target)) continue;
                if (valid.contains(target) && closeToEnemy(engine, ship, target)) continue;
                ShipAPI dest = nearestBig(ship, primary, fog);
                if (dest == null || dest == target) continue;
                now.put(ship, new Active(REDIRECT, elapsed, target.getName(), dest.getName()));
                if (acting) issue(engine, tasks, ship, dest.getLocation());
            }

            Vector2f behind = behindMainBody(engine, enemies);
            if (behind != null) {
                for (ShipAPI ship : pulledBack) {
                    if (ourMain.contains(ship) || !eligibleToFallBack(engine, tasks, ship)) continue;
                    now.put(ship, new Active(FALLBACK, elapsed, "none", "behind main body"));
                    if (acting) issue(engine, tasks, ship, behind);
                }
            }
        }

        List<ShipAPI> ended = new ArrayList<>();
        for (Map.Entry<ShipAPI, Active> entry : active.entrySet()) {
            Active next = now.get(entry.getKey());
            if (next == null || !next.kind.equals(entry.getValue().kind)) ended.add(entry.getKey());
        }
        for (ShipAPI ship : ended) {
            Active a = active.remove(ship);
            logRedirect(elapsed, ship, "end", a, elapsed - a.since);
        }
        for (Map.Entry<ShipAPI, Active> entry : now.entrySet()) {
            if (active.containsKey(entry.getKey())) continue;
            active.put(entry.getKey(), entry.getValue());
            logRedirect(elapsed, entry.getKey(), "start", entry.getValue(), 0f);
        }
    }

    /**
     * Where a pulled-back hull should rejoin us: behind our main body's centre, on the side away
     * from the enemy. Both centres are DP-weighted, and the enemy's uses every deployed hull rather
     * than their main group, so the direction cannot flip when two enemy groups are close in size.
     * When the centres are too close to give a front - the fleets are mixed together - it is our
     * plain centre. Kept inside the map. It only has to be roughly right: the order lifts the moment
     * the hull is back in the group, well before it arrives.
     */
    private Vector2f behindMainBody(CombatEngineAPI engine, List<ShipAPI> enemies) {
        if (ourMain.isEmpty()) return null;
        Vector2f ours = centroid(ourMain);
        Vector2f theirs = centroid(enemies);
        Vector2f point = new Vector2f(ours);
        float dx = ours.x - theirs.x;
        float dy = ours.y - theirs.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len >= MIN_FRONT_SEPARATION) {
            point.x += dx / len * FALLBACK_OFFSET;
            point.y += dy / len * FALLBACK_OFFSET;
        }
        float halfW = engine.getMapWidth() / 2f - MAP_MARGIN;
        float halfH = engine.getMapHeight() / 2f - MAP_MARGIN;
        point.x = Math.max(-halfW, Math.min(halfW, point.x));
        point.y = Math.max(-halfH, Math.min(halfH, point.y));
        return point;
    }

    /** DP-weighted, falling back to the plain mean if no hull reports DP. */
    private static Vector2f centroid(List<ShipAPI> ships) {
        float x = 0f, y = 0f, weight = 0f, mx = 0f, my = 0f;
        for (ShipAPI ship : ships) {
            FleetMemberAPI member = ship.getFleetMember();
            float dp = member == null ? 0f : member.getDeploymentPointsCost();
            x += ship.getLocation().x * dp;
            y += ship.getLocation().y * dp;
            weight += dp;
            mx += ship.getLocation().x;
            my += ship.getLocation().y;
        }
        return weight > 0f ? new Vector2f(x / weight, y / weight) : new Vector2f(mx / ships.size(), my / ships.size());
    }

    /** Off while either side is in full retreat, during a full assault, or while an AVOID order
     * exists - the same conditions AI Tweaks uses. */
    private boolean allowed(CombatEngineAPI engine, CombatTaskManagerAPI tasks) {
        if (tasks.isInFullRetreat() || tasks.isFullAssault()) return false;
        for (AssignmentInfo info : tasks.getAllAssignments()) {
            if (info.getType() == CombatAssignmentType.AVOID) return false;
        }
        int enemyOwner = owner == 0 ? 1 : 0;
        return !engine.getFleetManager(enemyOwner).getTaskManager(false).isInFullRetreat();
    }

    /** AI Tweaks' own eligibility: a destroyer or larger with weapons, not an ally, not a station or
     * module, deployed, and holding no order other than the one of ours just cleared. */
    private boolean eligible(CombatEngineAPI engine, CombatTaskManagerAPI tasks, ShipAPI ship) {
        if (!ship.isAlive() || ship.isExpired() || ship.isFighter() || ship.isStation() || ship.isStationModule()) {
            return false;
        }
        if (ship.isAlly() || !isBig(ship)) return false;
        if (findDeployed(engine, ship) == null) return false;
        return isFree(tasks, ship);
    }

    /**
     * Holds no order, or only the one of ours removed this tick. Bug fix: {@code removeAssignment}
     * does not take effect within the frame, so {@code getAssignmentFor} still reports the order
     * that was just removed. Checking it alone made every hull read as busy straight after its own
     * order was cleared, so it was re-issued only every other tick - all 472 redirects and fallbacks
     * in the first logged runs lasted exactly 1s and alternated on and off.
     */
    private boolean isFree(CombatTaskManagerAPI tasks, ShipAPI ship) {
        return tasks.getAssignmentFor(ship) == null || freedThisTick.contains(ship);
    }

    /** Not AI Tweaks' rule: frigates are included, since the small hulls that slide to timid fastest
     * are the ones most exposed when they drift off alone. */
    private boolean eligibleToFallBack(CombatEngineAPI engine, CombatTaskManagerAPI tasks, ShipAPI ship) {
        if (!ship.isAlive() || ship.isExpired() || ship.isFighter() || ship.isStation() || ship.isStationModule()) {
            return false;
        }
        if (ship.isAlly() || findDeployed(engine, ship) == null) return false;
        return isFree(tasks, ship);
    }

    private static boolean isBig(ShipAPI ship) {
        HullSize size = ship.getHullSize();
        boolean bigHull = size == HullSize.DESTROYER || size == HullSize.CRUISER || size == HullSize.CAPITAL_SHIP;
        return bigHull && !ship.getWeaponGroupsCopy().isEmpty();
    }

    /** What the ship AI is maneuvering against. Vanilla ship-system AIs read the same flag
     * ({@code AcausalDisruptorStats}, {@code DroneStrikeStats}); AI Tweaks uses it as its fallback. */
    private ShipAPI attackTarget(ShipAPI ship) {
        Object target = ship.getAIFlags() == null ? null : ship.getAIFlags().getCustom(AIFlags.MANEUVER_TARGET);
        if (!(target instanceof ShipAPI)) return null;
        ShipAPI enemy = (ShipAPI) target;
        // A hull dogfighting strike craft is not chasing a straggler. Fighters and drones are never
        // in the segmentation, so without this every one read as "outside the main body" and the
        // hull was sent at the enemy - logged as from=null, since they have no name.
        if (enemy.isFighter() || enemy.isDrone()) return null;
        return enemy.isAlive() && !enemy.isHulk() && enemy.getOwner() != owner ? enemy : null;
    }

    /**
     * Single-linkage clustering, ported as-is: two ships join a group within {@link #LINK_RANGE},
     * except that a frigate can join one group but never links two together, so a screen of
     * frigates cannot chain two separate bodies into one. Sorted largest DP first.
     */
    private static List<List<ShipAPI>> segment(List<ShipAPI> fleet) {
        int n = fleet.size();
        int[] group = new int[n];
        for (int i = 0; i < n; i++) group[i] = i;
        float linkSq = LINK_RANGE * LINK_RANGE;

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                boolean jFrigate = fleet.get(j).getHullSize() == HullSize.FRIGATE;
                boolean iFrigate = fleet.get(i).getHullSize() == HullSize.FRIGATE;
                if (jFrigate || (iFrigate && group[i] != i) || group[i] == group[j]) continue;
                if (Misc.getDistanceSq(fleet.get(i).getLocation(), fleet.get(j).getLocation()) > linkSq) continue;
                int merge = group[i];
                for (int k = 0; k < n; k++) if (group[k] == merge) group[k] = group[j];
            }
        }

        Map<Integer, List<ShipAPI>> byId = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            List<ShipAPI> members = byId.get(group[i]);
            if (members == null) {
                members = new ArrayList<>();
                byId.put(group[i], members);
            }
            members.add(fleet.get(i));
        }
        List<List<ShipAPI>> groups = new ArrayList<>(byId.values());
        Collections.sort(groups, new Comparator<List<ShipAPI>>() {
            @Override
            public int compare(List<ShipAPI> a, List<ShipAPI> b) {
                return Float.compare(dp(b), dp(a));
            }
        });
        return groups;
    }

    private static List<ShipAPI> validTargets(List<List<ShipAPI>> groups) {
        float largest = dp(groups.get(0));
        List<ShipAPI> valid = new ArrayList<>();
        for (List<ShipAPI> group : groups) {
            boolean capital = false;
            for (ShipAPI ship : group) capital |= ship.getHullSize() == HullSize.CAPITAL_SHIP;
            if (capital || dp(group) >= largest * VALID_GROUP_DP_FRACTION) valid.addAll(group);
        }
        return valid;
    }

    private static float dp(List<ShipAPI> group) {
        float sum = 0f;
        for (ShipAPI ship : group) {
            FleetMemberAPI member = ship.getFleetMember();
            if (member != null) sum += member.getDeploymentPointsCost();
        }
        return sum;
    }

    /** Within max(map size / 8, twice our longest range, the target's longest range) - AI Tweaks'
     * test for "this other group is close enough to be part of the fight". */
    private static boolean closeToEnemy(CombatEngineAPI engine, ShipAPI ship, ShipAPI enemy) {
        float mapSize = Math.max(engine.getMapWidth(), engine.getMapHeight());
        float range = Math.max(mapSize / 8f, Math.max(maxRange(ship) * 2f, maxRange(enemy)));
        return Misc.getDistanceSq(ship.getLocation(), enemy.getLocation()) <= range * range;
    }

    private static float maxRange(ShipAPI ship) {
        float max = 0f;
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (!weapon.isDecorative()) max = Math.max(max, weapon.getRange());
        }
        return max;
    }

    /** Nearest visible big ship of the main body, by distance to its edge. Small ships still count
     * as main-body membership above; only the destination is restricted to big ones. */
    private static ShipAPI nearestBig(ShipAPI ship, List<ShipAPI> primary, FogOfWarAPI fog) {
        ShipAPI best = null;
        float bestDist = Float.MAX_VALUE;
        for (ShipAPI enemy : primary) {
            if (!isBig(enemy) || (fog != null && !fog.isVisible(enemy))) continue;
            float dist = Misc.getDistance(ship.getLocation(), enemy.getLocation()) - enemy.getCollisionRadius();
            if (dist < bestDist) {
                bestDist = dist;
                best = enemy;
            }
        }
        return best;
    }

    private void issue(CombatEngineAPI engine, CombatTaskManagerAPI tasks, ShipAPI ship, Vector2f location) {
        DeployedFleetMemberAPI member = findDeployed(engine, ship);
        if (member == null) return;
        AssignmentTargetAPI waypoint = tasks.createWaypoint2(new Vector2f(location), false);
        AssignmentInfo rally = tasks.createAssignment(CombatAssignmentType.RALLY_TASK_FORCE, waypoint, false);
        if (rally == null) {
            engine.removeObject(waypoint);
            return;
        }
        tasks.giveAssignment(member, rally, false);
        issued.add(new Issued(ship, waypoint));
    }

    /** Removes only what this class gave: an order still held, still a rally, still on the same
     * spot. Compared by value, because the assignment and waypoint objects are not reliably the
     * same instances on a later read. */
    private void clearIssued(CombatEngineAPI engine, CombatTaskManagerAPI tasks) {
        freedThisTick.clear();
        for (Issued entry : issued) {
            AssignmentInfo info = entry.ship.isAlive() ? tasks.getAssignmentFor(entry.ship) : null;
            if (info != null && info.getType() == CombatAssignmentType.RALLY_TASK_FORCE
                    && info.getTarget() != null && entry.location.equals(info.getTarget().getLocation())) {
                tasks.removeAssignment(info);
                freedThisTick.add(entry.ship);
            }
            engine.removeObject(entry.waypoint);
        }
        issued.clear();
    }

    private static DeployedFleetMemberAPI findDeployed(CombatEngineAPI engine, ShipAPI ship) {
        for (DeployedFleetMemberAPI member : engine.getFleetManager(ship.getOwner()).getDeployedCopyDFM()) {
            if (member.getShip() == ship) return member;
        }
        return null;
    }

    private void logRedirect(float elapsed, ShipAPI ship, String phase, Active a, float seconds) {
        log.info(String.format("DraconisAdmiral,cohesion,t=%.0f,ship=%s,id=%s,phase=%s,kind=%s,mode=%s,from=%s,to=%s,seconds=%.0f",
                elapsed, ship.getName(), ship.getId(), phase, a.kind, acting ? "acting" : "observing",
                a.from, a.to, seconds));
    }
}
