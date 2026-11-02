package levianeer.draconis.data.scripts.combat.commander;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

/**
 * Splits the fleet into up to {@link #MAX_GROUPS} task forces, each led by a cruiser or capital, and
 * sends each group at an enemy cluster. Every ship still flies and fights under vanilla's ship AI:
 * groups are held together and moved only through vanilla's assignment system. The rest of a group
 * escorts its lead, and the lead intercepts the core ship (the biggest) of the group's target cluster.
 * <p>
 * Targets, re-planned every {@link #PLAN_SECONDS}: enemy ships within {@link #LINK} of each other form
 * a cluster. Clusters are taken weakest first, and each gets the nearest free group, plus more groups
 * until ours outweigh it by {@link #WIN}. A lone weak cluster is picked off by one group while another
 * takes the next; a strong one draws everything. Groups left over join the first target.
 * <p>
 * The engine keeps one task per target and replaces the old one when another is created on it, so
 * each escort or intercept task is created once and then reused.
 */
final class TaskForces {

    private static final Logger log = Global.getLogger(TaskForces.class);

    private static final int MAX_GROUPS = 3;
    /** Enemy ships this close to each other support each other: one cluster. */
    private static final float LINK = 2000f;
    /** Groups are added to a target until our strength is this multiple of the cluster's. */
    private static final float WIN = 1.5f;
    private static final float UPDATE_SECONDS = 1f;
    private static final float PLAN_SECONDS = 5f;
    private static final float LOG_SECONDS = 5f;
    private static final float LABEL_SECONDS = 2f;
    private static final Color[] COLORS = {new Color(90, 200, 255), new Color(255, 210, 80), new Color(230, 110, 255)};

    private static final class Group {
        final int id;
        /** Lead first (the biggest), then by DP, largest first. */
        final List<FleetMemberAPI> members = new ArrayList<>();
        float dp;
        List<ShipAPI> ships = Collections.emptyList();
        ShipAPI lead;
        float own;
        /** Core ship of the target cluster; null = none (search and destroy). */
        ShipAPI target;

        Group(int id) {
            this.id = id;
        }
    }

    private static final class Cluster {
        final List<ShipAPI> ships = new ArrayList<>();
        float strength;
        ShipAPI core;
        final Vector2f centre = new Vector2f();
    }

    private final int side;
    private final List<Group> groups = new ArrayList<>();
    private float sinceUpdate;
    private float sincePlan = PLAN_SECONDS;
    private float sinceLog = LOG_SECONDS;
    private float sinceLabel = LABEL_SECONDS;
    /** Assignments re-given since the last log line; should stay near zero between re-plans. */
    private int reissued;

    TaskForces(int side) {
        this.side = side;
    }

    /**
     * Splits the ships about to deploy into groups and returns them in deployment order, group by group,
     * so each group starts together. Ships arriving after the first deployment join the smallest group.
     */
    List<List<FleetMemberAPI>> plan(List<FleetMemberAPI> ships) {
        List<FleetMemberAPI> sorted = new ArrayList<>(ships);
        sorted.sort((a, b) -> Float.compare(b.getDeploymentPointsCost(), a.getDeploymentPointsCost()));
        if (!groups.isEmpty()) {
            for (FleetMemberAPI m : sorted) add(smallest(), m);
            return Collections.singletonList(sorted);
        }
        for (FleetMemberAPI m : sorted) {
            if (groups.size() < MAX_GROUPS && (m.isCruiser() || m.isCapital())) add(new Group(groups.size() + 1), m);
        }
        if (groups.isEmpty() && !sorted.isEmpty()) add(new Group(1), sorted.get(0));
        for (FleetMemberAPI m : sorted) {
            if (groups.stream().noneMatch(g -> g.members.contains(m))) add(smallest(), m);
        }
        List<List<FleetMemberAPI>> order = new ArrayList<>();
        for (Group g : groups) {
            order.add(g.members);
            StringBuilder names = new StringBuilder();
            for (FleetMemberAPI m : g.members) names.append(names.length() == 0 ? "" : "|").append(m.getShipName());
            log.info(String.format("DraconisCommander,group,id=%d,dp=%.0f,members=%s", g.id, g.dp, names));
        }
        return order;
    }

    private void add(Group g, FleetMemberAPI m) {
        if (!groups.contains(g)) groups.add(g);
        g.members.add(m);
        g.dp += m.getDeploymentPointsCost();
    }

    private Group smallest() {
        Group best = groups.get(0);
        for (Group g : groups) if (g.dp < best.dp) best = g;
        return best;
    }

    void advance(CombatEngineAPI engine, float amount) {
        sinceUpdate += amount;
        if (sinceUpdate < UPDATE_SECONDS) return;
        sincePlan += sinceUpdate;
        sinceLog += sinceUpdate;
        sinceLabel += sinceUpdate;
        sinceUpdate = 0f;
        float t = engine.getTotalElapsedTime(false);
        CombatFleetManagerAPI fleet = engine.getFleetManager(side);
        CombatTaskManagerAPI tasks = fleet.getTaskManager(false);

        for (Group g : groups) {
            List<ShipAPI> ships = new ArrayList<>();
            for (FleetMemberAPI m : g.members) {
                ShipAPI s = fleet.getShipFor(m);
                if (s != null && Commander.controllable(engine, s, side) && !s.isRetreating()) ships.add(s);
            }
            g.ships = ships;
            ShipAPI lead = ships.isEmpty() ? null : ships.get(0);
            if (lead != g.lead && g.lead != null) {
                log.info(String.format("DraconisCommander,lead,t=%.1f,group=%d,from=%s,to=%s", t, g.id,
                        g.lead.getName(), lead == null ? "none" : lead.getName()));
            }
            g.lead = lead;
            g.own = 0f;
            for (ShipAPI s : ships) g.own += strength(s);
            if (g.target != null && (!g.target.isAlive() || g.target.isHulk() || !engine.isEntityInPlay(g.target))) g.target = null;
        }

        if (sincePlan >= PLAN_SECONDS || groups.stream().anyMatch(g -> g.lead != null && g.target == null)) {
            sincePlan = 0f;
            planTargets(engine, t);
        }

        for (Group g : groups) {
            if (g.lead == null) continue;
            AssignmentInfo escort = task(tasks, CombatAssignmentType.MEDIUM_ESCORT, fleet.getDeployedFleetMember(g.lead));
            for (int i = 1; i < g.ships.size(); i++) give(tasks, fleet, g.ships.get(i), escort);
            DeployedFleetMemberAPI target = g.target == null ? null : engine.getFleetManager(g.target.getOwner()).getDeployedFleetMember(g.target);
            if (target != null) {
                give(tasks, fleet, g.lead, task(tasks, CombatAssignmentType.INTERCEPT, target));
            } else if (tasks.getAssignmentFor(g.lead) != null) {
                tasks.orderSearchAndDestroy(fleet.getDeployedFleetMember(g.lead), false);
                reissued++;
            }
        }

        if (sinceLabel >= LABEL_SECONDS) {
            sinceLabel = 0f;
            for (Group g : groups) {
                Color color = COLORS[(g.id - 1) % COLORS.length];
                for (ShipAPI s : g.ships) {
                    String text = s == g.lead
                            ? "TF" + g.id + " > " + (g.target == null ? "search" : g.target.getHullSpec().getHullName())
                            : "TF" + g.id;
                    engine.addFloatingText(s.getLocation(), text, s == g.lead ? 28f : 20f, color, s, 0f, 0f);
                }
            }
        }

        if (sinceLog >= LOG_SECONDS) {
            sinceLog = 0f;
            for (Group g : groups) {
                if (g.lead == null) continue;
                float spread = 0f;
                for (int i = 1; i < g.ships.size(); i++) spread += Misc.getDistance(g.ships.get(i).getLocation(), g.lead.getLocation());
                if (g.ships.size() > 1) spread /= g.ships.size() - 1;
                log.info(String.format("DraconisCommander,tf,t=%.0f,group=%d,lead=%s,ships=%d,own=%.0f,target=%s,targetDistance=%.0f,spread=%.0f,reissued=%d",
                        t, g.id, g.lead.getName(), g.ships.size(), g.own, g.target == null ? "none" : g.target.getName(),
                        g.target == null ? -1f : Misc.getDistance(g.lead.getLocation(), g.target.getLocation()), spread, reissued));
            }
            reissued = 0;
        }
    }

    /** Weakest cluster first, each gets the nearest free groups until they outweigh it by {@link #WIN}. */
    private void planTargets(CombatEngineAPI engine, float t) {
        List<Cluster> clusters = clusters(engine);
        clusters.sort((a, b) -> Float.compare(a.strength, b.strength));
        List<Group> free = new ArrayList<>();
        for (Group g : groups) if (g.lead != null) free.add(g);
        Map<Group, ShipAPI> before = new HashMap<>();
        for (Group g : free) before.put(g, g.target);
        StringBuilder plan = new StringBuilder();
        Cluster first = null;
        for (Cluster c : clusters) {
            if (free.isEmpty()) break;
            if (first == null) first = c;
            float sum = 0f;
            StringBuilder sent = new StringBuilder();
            while (!free.isEmpty() && (sum == 0f || sum < c.strength * WIN)) {
                Group nearest = free.get(0);
                for (Group g : free) {
                    if (Misc.getDistance(g.lead.getLocation(), c.centre) < Misc.getDistance(nearest.lead.getLocation(), c.centre)) nearest = g;
                }
                free.remove(nearest);
                nearest.target = c.core;
                sum += nearest.own;
                sent.append(sent.length() == 0 ? "" : "+").append(nearest.id);
            }
            plan.append(String.format(";%s(%d ships,%.0f)<-TF%s(%.0f)", c.core.getName(), c.ships.size(), c.strength, sent, sum));
        }
        for (Group g : free) g.target = first == null ? null : first.core;
        boolean changed = false;
        for (Map.Entry<Group, ShipAPI> e : before.entrySet()) changed |= e.getKey().target != e.getValue();
        if (changed) log.info(String.format("DraconisCommander,plan,t=%.1f,clusters=%d%s%s", t, clusters.size(), plan,
                free.isEmpty() ? "" : ";leftoverJoinFirst=" + free.size()));
    }

    private List<Cluster> clusters(CombatEngineAPI engine) {
        List<ShipAPI> enemies = new ArrayList<>();
        for (ShipAPI s : engine.getShips()) {
            if (s.getOwner() == side || s.getOwner() == 100 || s.isFighter() || !s.isAlive() || s.isHulk()) continue;
            if (engine.getFleetManager(s.getOwner()).getDeployedFleetMember(s) == null) continue;
            enemies.add(s);
        }
        List<Cluster> clusters = new ArrayList<>();
        while (!enemies.isEmpty()) {
            Cluster c = new Cluster();
            c.ships.add(enemies.remove(0));
            for (int i = 0; i < c.ships.size(); i++) {
                ShipAPI s = c.ships.get(i);
                for (int j = enemies.size() - 1; j >= 0; j--) {
                    if (Misc.getDistance(s.getLocation(), enemies.get(j).getLocation()) <= LINK) c.ships.add(enemies.remove(j));
                }
            }
            for (ShipAPI s : c.ships) {
                float w = strength(s);
                c.strength += w;
                c.centre.x += s.getLocation().x * w;
                c.centre.y += s.getLocation().y * w;
                if (c.core == null || w > strength(c.core)) c.core = s;
            }
            if (c.strength > 0f) c.centre.scale(1f / c.strength);
            else c.centre.set(c.core.getLocation());
            clusters.add(c);
        }
        return clusters;
    }

    /** The task of {@code type} on {@code target}, created if there is none. */
    private AssignmentInfo task(CombatTaskManagerAPI tasks, CombatAssignmentType type, DeployedFleetMemberAPI target) {
        if (target == null) return null;
        AssignmentInfo info = tasks.getAssignmentInfoForTarget(target);
        if (info != null && info.getType() == type) return info;
        return tasks.createAssignment(type, target, false);
    }

    private void give(CombatTaskManagerAPI tasks, CombatFleetManagerAPI fleet, ShipAPI ship, AssignmentInfo info) {
        if (info == null) return;
        DeployedFleetMemberAPI member = fleet.getDeployedFleetMember(ship);
        if (member == null) return;
        AssignmentInfo current = tasks.getAssignmentFor(ship);
        if (current != null && current.getTarget() == info.getTarget()) return;
        tasks.giveAssignment(member, info, false);
        reissued++;
    }

    /** DP scaled by remaining hull: a rough like-for-like measure that also counts damage taken. */
    private static float strength(ShipAPI ship) {
        float dp = ship.getFleetMember() == null ? 0f : ship.getFleetMember().getDeploymentPointsCost();
        return dp * ship.getHullLevel();
    }
}
