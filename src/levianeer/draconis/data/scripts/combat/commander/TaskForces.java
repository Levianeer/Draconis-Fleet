package levianeer.draconis.data.scripts.combat.commander;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.scripts.hullmods.XLII_SystemHullModBase;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

/**
 * The commander's slice (section 11.3 of the build doc): task forces, a fleet stance, group jobs and
 * fleet withdraw. Every ship still flies and fights under vanilla's ship AI. Groups are held together
 * and moved only through vanilla's assignment system: the rest of a group escorts its lead, and the
 * lead's own order is the group's job.
 * <p>
 * <b>Stance</b>, re-planned every {@link #PLAN_SECONDS}, held at least {@link #MIN_STANCE_SECONDS}
 * unless it turns unsafe:
 * <ul>
 * <li><b>concentrate</b>: every group attacks the enemy main body;</li>
 * <li><b>pick off</b>: one group attacks a stray it outweighs {@link #PICK_OFF_EDGE}x and can reach
 * without crossing the main body's guns, while every other group holds just outside the main body's
 * reach;</li>
 * <li><b>withdraw</b>: the battle is lost, so every ship jumps out. Final.</li>
 * </ul>
 * <b>Jobs:</b>
 * <ul>
 * <li><b>attack</b>: the lead rallies on a point at its own reach from the cluster's edge, then fights
 * freely once there ({@link #ATTACK_FREE}). Not {@code INTERCEPT}: that is vanilla's commit mode, which
 * closes to 0.7x range and skips the danger checks, so every lead charged in (step 3);</li>
 * <li><b>hold</b>: the lead rallies on a point just outside the main body's reach;</li>
 * <li><b>regroup</b>: a group outmatched where it stands falls back onto the nearest other group, its lead
 * escorting that lead, for at least {@link #REGROUP_MIN_SECONDS}. It overrides the stance's job;</li>
 * <li><b>flank</b>: the skirmish group's default. It attacks the main body from the side, at its lead's
 * reach from the edge square to the line's approach, so the main body faces the line while the
 * skirmishers take the openings it leaves ({@link Spacing});</li>
 * <li><b>screen</b>: the skirmish lead escorts the line's lead, while the line is outmatched (below
 * {@link #SCREEN_AT}, until {@link #SCREEN_END}). In core2-v1 screen was the default and the skirmishers
 * did nothing but follow (see {@link #plan} for the roles).</li>
 * </ul>
 * How each ship then fights is {@link Spacing}'s call (core 1).
 * <p>
 * The engine keeps one task per target and replaces the old one when another is created on it, so each
 * task is created once and reused; a regrouping lead joins the other lead's escort task.
 */
final class TaskForces {

    private static final Logger log = Global.getLogger(TaskForces.class);

    /** Ships this close to a lead count towards its group's local balance. */
    private static final float LOCAL = 3000f;
    private static final float UPDATE_SECONDS = 1f;
    private static final float PLAN_SECONDS = 5f;
    private static final float MIN_STANCE_SECONDS = 10f;
    /** A pick off gives up after this long. */
    private static final float PICK_OFF_LIMIT = 45f;
    /** A group picks off a stray only if it outweighs it this much ... */
    private static final float PICK_OFF_EDGE = 1.5f;
    /** ... and its slowest ship is at least this fast relative to the stray's fastest, or it never catches it. */
    private static final float PICK_OFF_SPEED = 1f;
    /** A pick off ends once the main body gets this much closer than its reach to the picking group. */
    private static final float PICK_OFF_DANGER = 500f;
    /** How far outside the main body's reach a holding group waits. */
    private static final float HOLD_MARGIN = 800f;
    /** A rally point moves only when the wanted point is this far from the current one. */
    private static final float POINT_MOVE = 500f;
    /**
     * An attacking lead fights freely once within this of its attack point or in contact (an enemy within its
     * reach plus {@link #CONTACT}), and rallies again only beyond {@link #ATTACK_LEASH} and out of contact.
     * core1-v1 freed it only at the point, which a lead fighting its way in rarely reached, so the rally
     * kept dragging it and was re-given about twice a second.
     */
    private static final float ATTACK_FREE = 800f;
    private static final float ATTACK_LEASH = 2500f;
    private static final float CONTACT = 500f;
    private static final float OUT_OF_CONTACT = 1500f;
    /**
     * A group's other ships escort the lead only to travel together. Once one is in contact it is released
     * to fight under its spacing mode, and goes back on escort when out of contact or more than this far from
     * the lead. Held on escort, vanilla's escort maneuver kept them parked behind the lead while one or two
     * ships fought (author, core2-v2).
     */
    private static final float ESCORT_LEASH = 3000f;
    /**
     * A group regroups below this local balance and returns to the stance's job above {@link #REGROUP_END},
     * after at least {@link #REGROUP_MIN_SECONDS}; it does not regroup again within {@link #REGROUP_COOLDOWN}.
     * Step 3 flipped at a single 0.8/1.0 threshold.
     */
    private static final float REGROUP_AT = 0.7f;
    private static final float REGROUP_END = 1.1f;
    private static final float REGROUP_MIN_SECONDS = 15f;
    private static final float REGROUP_COOLDOWN = 10f;
    /**
     * Withdraw once our deployed DP is below this share of the enemy's, we have lost more DP than they have,
     * and no group has held a balance of 1 or better for {@link #WITHDRAW_AFTER}. DP rather than DP x hull, so
     * damaged ships still count: step 3 withdrew at 23 DP lost because the battered Alwaid counted as 17.
     */
    private static final float WITHDRAW_AT = 0.5f;
    private static final float SCREEN_AT = 1f;
    private static final float SCREEN_END = 1.3f;
    private static final float WITHDRAW_AFTER = 15f;
    private static final float LOG_SECONDS = 5f;
    private static final float LABEL_SECONDS = 2f;
    static final Color[] COLORS = {new Color(90, 200, 255), new Color(255, 210, 80), new Color(230, 110, 255)};
    private static final Color STANCE_COLOR = Color.WHITE;

    enum Stance { CONCENTRATE, PICK_OFF, WITHDRAW }

    enum Job { ATTACK, HOLD, REGROUP, SCREEN, FLANK }

    /** Line first: it deploys first and is listed first. */
    enum Role { LINE, SKIRMISH }

    static final class Group {
        final int id;
        final Role role;
        /** Lead first (the biggest), then by DP, largest first. */
        final List<FleetMemberAPI> members = new ArrayList<>();
        float dp;
        List<ShipAPI> ships = Collections.emptyList();
        ShipAPI lead;
        float own;
        /** Our strength near the lead over the enemy's; infinite when no enemy is near. */
        float balance;
        Job job = Job.ATTACK;
        /** Attack: the cluster attacked; null = none, search and destroy. */
        BattlePicture.Cluster attacking;
        /** Attack or hold: the rally point and its waypoint; attack drops the rally while {@link #free}. */
        Vector2f movePoint;
        AssignmentTargetAPI moveWaypoint;
        boolean free;
        /** Regroup or screen: the group fallen back onto or screened; when the group last started or ended a regroup. */
        Group regroupTo;
        float regroupChanged = -1000f;

        Group(int id, Role role) {
            this.id = id;
            this.role = role;
        }
    }

    private final int side;
    final List<Group> groups = new ArrayList<>();
    final BattlePicture picture;
    final Spacing spacing = new Spacing();
    Stance stance = Stance.CONCENTRATE;
    private float stanceSince;
    /** Pick off: the group sent and the core of the stray it chases. */
    private Group picker;
    private ShipAPI pickTarget;
    private float lastBalanceAboveOne;
    private final Set<ShipAPI> withdrawn = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Non-lead ships currently released from escort to fight. */
    private final Set<ShipAPI> released = Collections.newSetFromMap(new IdentityHashMap<>());
    private float sinceUpdate;
    private float sincePlan = PLAN_SECONDS;
    private float sinceLog = LOG_SECONDS;
    private float sinceLabel = LABEL_SECONDS;
    /** Assignments re-given since the last log line; should stay low between job changes. */
    private int reissued;

    TaskForces(int side) {
        this.side = side;
        this.picture = new BattlePicture(side);
    }

    /**
     * Splits the ships about to deploy into a line group (capitals, cruisers and carriers, led by the
     * biggest) and a skirmish group (the other frigates and destroyers, led by the biggest of them), and
     * returns them in deployment order, line first, so each group starts together. Ships arriving later
     * join the group of their role.
     * <p>
     * Grouped by role rather than DP balance: balancing by DP paired the capital with one frigate and tied
     * every fast, fragile ship to the slow cruiser, leaving the capital unprotected (author, core1-v2).
     */
    List<List<FleetMemberAPI>> plan(List<FleetMemberAPI> ships) {
        List<FleetMemberAPI> sorted = new ArrayList<>(ships);
        sorted.sort((a, b) -> Float.compare(b.getDeploymentPointsCost(), a.getDeploymentPointsCost()));
        boolean first = groups.isEmpty();
        for (FleetMemberAPI m : sorted) add(groupFor(role(m)), m);
        if (!first) return Collections.singletonList(sorted);
        groups.sort((a, b) -> a.role.compareTo(b.role));
        List<List<FleetMemberAPI>> order = new ArrayList<>();
        for (Group g : groups) {
            order.add(g.members);
            StringBuilder names = new StringBuilder();
            for (FleetMemberAPI m : g.members) names.append(names.length() == 0 ? "" : "|").append(m.getShipName());
            log.info(String.format("DraconisCommander,group,id=%d,role=%s,dp=%.0f,members=%s", g.id, g.role, g.dp, names));
        }
        return order;
    }

    private static Role role(FleetMemberAPI m) {
        return m.isCapital() || m.isCruiser() || m.isCarrier() ? Role.LINE : Role.SKIRMISH;
    }

    private Group groupFor(Role role) {
        for (Group g : groups) if (g.role == role) return g;
        Group g = new Group(groups.size() + 1, role);
        groups.add(g);
        return g;
    }

    private void add(Group g, FleetMemberAPI m) {
        g.members.add(m);
        g.dp += m.getDeploymentPointsCost();
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
        picture.update(engine);
        List<ShipAPI> ours = refreshGroups(engine, fleet, t);

        if (stance != Stance.WITHDRAW) {
            spacing.update(ours, picture, t, this::isSkirmisher);
            if (sincePlan >= PLAN_SECONDS) {
                sincePlan = 0f;
                planStance(engine, t);
            }
            assignJobs(t);
        }
        if (stance == Stance.WITHDRAW) {
            withdraw(engine, fleet, ours);
        } else {
            for (Group g : groups) execute(engine, fleet, tasks, g);
        }

        if (sinceLabel >= LABEL_SECONDS) {
            sinceLabel = 0f;
            label(engine);
        }
        if (sinceLog >= LOG_SECONDS) {
            sinceLog = 0f;
            logState(t);
        }
    }

    /** Live ships per group, leads (the biggest alive), strength and local balance. Returns all our live ships. */
    private List<ShipAPI> refreshGroups(CombatEngineAPI engine, CombatFleetManagerAPI fleet, float t) {
        List<ShipAPI> ours = new ArrayList<>();
        for (ShipAPI s : engine.getShips()) if (Commander.controllable(engine, s, side) && !s.isRetreating()) ours.add(s);
        for (Group g : groups) {
            List<ShipAPI> ships = new ArrayList<>();
            for (FleetMemberAPI m : g.members) {
                ShipAPI s = fleet.getShipFor(m);
                if (s != null && ours.contains(s)) ships.add(s);
            }
            g.ships = ships;
            ShipAPI lead = ships.isEmpty() ? null : ships.get(0);
            if (lead != g.lead && g.lead != null) {
                log.info(String.format("DraconisCommander,lead,t=%.1f,group=%d,from=%s,to=%s", t, g.id,
                        g.lead.getName(), lead == null ? "none" : lead.getName()));
            }
            g.lead = lead;
            g.own = 0f;
            for (ShipAPI s : ships) g.own += BattlePicture.strength(s);
            if (lead == null) continue;
            float near = 0f;
            for (ShipAPI s : ours) if (Misc.getDistance(s.getLocation(), lead.getLocation()) <= LOCAL) near += BattlePicture.strength(s);
            float enemy = picture.enemyStrengthNear(lead.getLocation(), LOCAL);
            g.balance = enemy > 0f ? near / enemy : Float.POSITIVE_INFINITY;
            if (g.balance >= 1f) lastBalanceAboveOne = t;
        }
        return ours;
    }

    private List<Group> live() {
        List<Group> live = new ArrayList<>();
        for (Group g : groups) if (g.lead != null) live.add(g);
        return live;
    }

    /** Withdraw if lost; otherwise end an unsafe or finished pick off, or start a safe one. */
    private void planStance(CombatEngineAPI engine, float t) {
        CombatFleetManagerAPI ourFleet = engine.getFleetManager(side);
        CombatFleetManagerAPI theirFleet = engine.getFleetManager(1 - side);
        float our = deployedDp(ourFleet);
        float their = deployedDp(theirFleet);
        float ourLost = lostDp(ourFleet);
        float theirLost = lostDp(theirFleet);
        if (their > 0f && our < their * WITHDRAW_AT && ourLost > theirLost && t - lastBalanceAboveOne >= WITHDRAW_AFTER) {
            setStance(Stance.WITHDRAW, t, String.format("ourDp=%.0f,theirDp=%.0f,ourLost=%.0f,theirLost=%.0f,noWinningGroupFor=%.0f",
                    our, their, ourLost, theirLost, t - lastBalanceAboveOne));
            return;
        }
        if (stance == Stance.PICK_OFF) {
            String end = pickOffEnd(t);
            if (end != null) {
                picker = null;
                pickTarget = null;
                setStance(Stance.CONCENTRATE, t, end);
            }
            return;
        }
        if (t - stanceSince < MIN_STANCE_SECONDS || picture.main == null) return;
        List<Group> live = live();
        if (live.size() < 2) return;
        Group best = null;
        BattlePicture.Cluster bestStray = null;
        for (BattlePicture.Cluster c : picture.clusters) {
            if (!c.stray) continue;
            float strayTop = 0f;
            for (ShipAPI s : c.ships) strayTop = Math.max(strayTop, s.getMaxSpeed());
            for (Group g : live) {
                if (g.own < c.strength * PICK_OFF_EDGE || !picture.catchable(c, g.lead.getLocation())) continue;
                float slowest = Float.MAX_VALUE;
                for (ShipAPI s : g.ships) slowest = Math.min(slowest, s.getMaxSpeed());
                if (slowest < strayTop * PICK_OFF_SPEED) continue;
                if (best == null || g.own < best.own
                        || g.own == best.own && Misc.getDistance(g.lead.getLocation(), c.centre) < Misc.getDistance(best.lead.getLocation(), bestStray.centre)) {
                    best = g;
                    bestStray = c;
                }
            }
        }
        if (best == null) return;
        picker = best;
        pickTarget = bestStray.core;
        setStance(Stance.PICK_OFF, t, String.format("group=%d(%.0f),stray=%s(%d,%.0f),gap=%.0f,distance=%.0f",
                best.id, best.own, bestStray.core.getName(), bestStray.ships.size(), bestStray.strength, bestStray.gapToMain,
                Misc.getDistance(best.lead.getLocation(), bestStray.centre)));
    }

    private static float deployedDp(CombatFleetManagerAPI fleet) {
        float dp = 0f;
        for (FleetMemberAPI m : fleet.getDeployedCopy()) if (!m.isFighterWing()) dp += m.getDeploymentPointsCost();
        return dp;
    }

    /** A ship can be both disabled and destroyed, so each member counts once. */
    private static float lostDp(CombatFleetManagerAPI fleet) {
        Set<String> counted = new java.util.HashSet<>();
        float dp = 0f;
        List<FleetMemberAPI> lost = new ArrayList<>(fleet.getDisabledCopy());
        lost.addAll(fleet.getDestroyedCopy());
        for (FleetMemberAPI m : lost) if (!m.isFighterWing() && counted.add(m.getId())) dp += m.getDeploymentPointsCost();
        return dp;
    }

    /** Why the current pick off must end, or null to carry on. */
    private String pickOffEnd(float t) {
        if (picker == null || picker.lead == null) return "pickerGone";
        BattlePicture.Cluster c = pickTarget == null ? null : picture.clusterOf(pickTarget);
        if (c == null) return "strayDead";
        if (c.main) return "strayJoinedMain";
        if (picture.main != null) {
            for (ShipAPI m : picture.main.ships) {
                if (Misc.getDistance(m.getLocation(), picker.lead.getLocation()) < picture.main.reach - PICK_OFF_DANGER) return "mainBodyClosing";
            }
        }
        if (t - stanceSince > PICK_OFF_LIMIT) return "timeout";
        return null;
    }

    private void setStance(Stance next, float t, String reason) {
        log.info(String.format("DraconisCommander,stance,t=%.1f,from=%s,to=%s,%s", t, stance, next, reason));
        stance = next;
        stanceSince = t;
    }

    /**
     * The stance's job for each group, overridden by regroup while a group is outmatched. A skirmish group
     * that is not picking off screens the line: its lead escorts the line's lead, so the fast ships cover the
     * capital instead of fighting the main body on their own.
     */
    private void assignJobs(float t) {
        List<Group> live = live();
        Group line = null;
        for (Group g : live) if (g.role == Role.LINE) line = g;
        for (Group g : live) {
            Job job;
            BattlePicture.Cluster attacking = null;
            Group regroupTo = null;
            boolean outmatched = g.job == Job.REGROUP
                    ? g.balance < REGROUP_END || t - g.regroupChanged < REGROUP_MIN_SECONDS
                    : g.balance < REGROUP_AT && t - g.regroupChanged >= REGROUP_COOLDOWN;
            Group nearest = null;
            for (Group other : live) {
                if (other == g || other.regroupTo == g) continue;
                if (nearest == null || Misc.getDistance(other.lead.getLocation(), g.lead.getLocation()) < Misc.getDistance(nearest.lead.getLocation(), g.lead.getLocation())) nearest = other;
            }
            if (outmatched && nearest != null) {
                job = Job.REGROUP;
                regroupTo = nearest;
            } else if (stance == Stance.PICK_OFF && g != picker) {
                job = Job.HOLD;
            } else if (g.role == Role.SKIRMISH && line != null && g != picker
                    && (line.balance < SCREEN_AT || g.job == Job.SCREEN && line.balance < SCREEN_END)) {
                job = Job.SCREEN;
                regroupTo = line;
            } else if (g.role == Role.SKIRMISH && line != null && g != picker && picture.main != null) {
                job = Job.FLANK;
                attacking = picture.main;
            } else {
                job = Job.ATTACK;
                attacking = stance == Stance.PICK_OFF ? picture.clusterOf(pickTarget) : picture.main;
            }
            if (job != g.job || regroupTo != g.regroupTo) {
                log.info(String.format("DraconisCommander,job,t=%.1f,group=%d,from=%s,to=%s,balance=%s%s", t, g.id, g.job, job,
                        balanceText(g.balance), regroupTo == null ? "" : ",onto=" + regroupTo.id));
            }
            if ((job == Job.REGROUP) != (g.job == Job.REGROUP)) g.regroupChanged = t;
            g.job = job;
            g.attacking = attacking;
            g.regroupTo = regroupTo;
            if (job == Job.HOLD) g.movePoint = holdPoint(g);
            else if (job == Job.ATTACK) g.movePoint = attacking == null ? null : attackPoint(g, attacking);
            else if (job == Job.FLANK) g.movePoint = flankPoint(g, line, attacking);
        }
    }

    /** Skirmishers are the opportunists that take openings ({@link Spacing}). */
    private boolean isSkirmisher(ShipAPI ship) {
        for (Group g : groups) if (g.role == Role.SKIRMISH && g.ships.contains(ship)) return true;
        return false;
    }

    /**
     * Square to the line's approach on the cluster: from its centre, perpendicular to the line lead's
     * bearing, on the skirmish lead's side, at the cluster's radius plus the skirmish lead's reach.
     */
    private Vector2f flankPoint(Group g, Group line, BattlePicture.Cluster c) {
        Vector2f approach = Vector2f.sub(line.lead.getLocation(), c.centre, new Vector2f());
        if (approach.lengthSquared() < 1f) approach.set(0f, side == 1 ? 1f : -1f);
        approach.normalise();
        Vector2f flank = new Vector2f(-approach.y, approach.x);
        Vector2f toSkirmish = Vector2f.sub(g.lead.getLocation(), c.centre, new Vector2f());
        if (Vector2f.dot(flank, toSkirmish) < 0f) flank.negate();
        flank.scale(c.radius + BattlePicture.reach(g.lead));
        return Vector2f.add(c.centre, flank, flank);
    }

    /** At the lead's own reach from the cluster's edge, on the lead's side. */
    private Vector2f attackPoint(Group g, BattlePicture.Cluster c) {
        Vector2f dir = Vector2f.sub(g.lead.getLocation(), c.centre, new Vector2f());
        if (dir.lengthSquared() < 1f) dir.set(0f, side == 1 ? 1f : -1f);
        dir.normalise();
        dir.scale(c.radius + BattlePicture.reach(g.lead));
        return Vector2f.add(c.centre, dir, dir);
    }

    /**
     * Just outside the main body's reach, on the side facing the group: from the main body's centre
     * towards the lead, at its radius plus its reach plus {@link #HOLD_MARGIN}.
     */
    private Vector2f holdPoint(Group g) {
        BattlePicture.Cluster m = picture.main;
        if (m == null) return new Vector2f(g.lead.getLocation());
        Vector2f dir = Vector2f.sub(g.lead.getLocation(), m.centre, new Vector2f());
        if (dir.lengthSquared() < 1f) dir.set(0f, side == 1 ? 1f : -1f);
        dir.normalise();
        dir.scale(m.radius + m.reach + HOLD_MARGIN);
        return Vector2f.add(m.centre, dir, dir);
    }

    private void execute(CombatEngineAPI engine, CombatFleetManagerAPI fleet, CombatTaskManagerAPI tasks, Group g) {
        if (g.lead == null) return;
        DeployedFleetMemberAPI leadMember = fleet.getDeployedFleetMember(g.lead);
        AssignmentInfo escort = task(tasks, CombatAssignmentType.MEDIUM_ESCORT, leadMember);
        for (int i = 1; i < g.ships.size(); i++) {
            ShipAPI ship = g.ships.get(i);
            float gap = enemyGap(ship);
            if (Misc.getDistance(ship.getLocation(), g.lead.getLocation()) > ESCORT_LEASH || gap > OUT_OF_CONTACT) released.remove(ship);
            else if (gap <= CONTACT) released.add(ship);
            if (!released.contains(ship)) {
                give(tasks, fleet, ship, escort);
            } else if (tasks.getAssignmentFor(ship) != null) {
                tasks.orderSearchAndDestroy(fleet.getDeployedFleetMember(ship), false);
                reissued++;
            }
        }
        boolean rally = false;
        switch (g.job) {
            case ATTACK:
            case FLANK:
                if (g.movePoint != null) {
                    float distance = Misc.getDistance(g.lead.getLocation(), g.movePoint);
                    float gap = enemyGap(g.lead);
                    if (distance <= ATTACK_FREE || gap <= CONTACT) g.free = true;
                    else if (distance > ATTACK_LEASH && gap > OUT_OF_CONTACT) g.free = false;
                    rally = !g.free;
                }
                if (!rally && tasks.getAssignmentFor(g.lead) != null) {
                    tasks.orderSearchAndDestroy(leadMember, false);
                    reissued++;
                }
                break;
            case HOLD:
                g.free = false;
                rally = true;
                break;
            case REGROUP:
            case SCREEN:
                g.free = false;
                give(tasks, fleet, g.lead, task(tasks, CombatAssignmentType.MEDIUM_ESCORT, fleet.getDeployedFleetMember(g.regroupTo.lead)));
                break;
        }
        if (rally) {
            if (g.moveWaypoint == null || Misc.getDistance(g.moveWaypoint.getLocation(), g.movePoint) > POINT_MOVE) {
                if (g.moveWaypoint != null) tasks.removeAssignment(tasks.getAssignmentInfoForTarget(g.moveWaypoint));
                g.moveWaypoint = tasks.createWaypoint2(g.movePoint, false);
            }
            give(tasks, fleet, g.lead, task(tasks, CombatAssignmentType.RALLY_TASK_FORCE, g.moveWaypoint));
        } else if (g.moveWaypoint != null) {
            tasks.removeAssignment(tasks.getAssignmentInfoForTarget(g.moveWaypoint));
            g.moveWaypoint = null;
        }
    }

    /**
     * How far the nearest enemy is outside the ship's own reach (negative inside it). For a capital,
     * frigates do not count: a capital is never "in contact" with a frigate, so one chasing a frigate falls
     * out of contact and is pulled back to its group or attack point (see {@link Spacing} for the rest).
     */
    private float enemyGap(ShipAPI ship) {
        float reach = BattlePicture.reach(ship) + ship.getCollisionRadius();
        float best = Float.MAX_VALUE;
        for (BattlePicture.Cluster c : picture.clusters) {
            for (ShipAPI e : c.ships) {
                if (ship.isCapital() && e.isFrigate()) continue;
                best = Math.min(best, Misc.getDistance(e.getLocation(), ship.getLocation()) - reach);
            }
        }
        return best;
    }

    /**
     * Orders every ship still in the fight out by the Transverse Jump, once each, marked so the jump does
     * not refund a command point it never cost. Once each because the jump's charge clears the retreat flag,
     * so a charging ship looks like it has no order.
     */
    private void withdraw(CombatEngineAPI engine, CombatFleetManagerAPI fleet, List<ShipAPI> ours) {
        for (ShipAPI s : ours) {
            if (!withdrawn.add(s)) continue;
            DeployedFleetMemberAPI member = fleet.getDeployedFleetMember(s);
            if (member == null) continue;
            s.setCustomData(XLII_SystemHullModBase.WarpDriveScript.NO_CP_REFUND_KEY, true);
            fleet.getTaskManager(false).orderRetreat(member, false, true);
        }
    }

    private AssignmentInfo task(CombatTaskManagerAPI tasks, CombatAssignmentType type, AssignmentTargetAPI target) {
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

    /** Text labels: each lead's group, job and balance, and the stance over the biggest ship. Shapes are drawn by {@link CommanderOverlay}. */
    private void label(CombatEngineAPI engine) {
        ShipAPI flagship = null;
        for (Group g : groups) {
            if (g.lead == null) continue;
            if (flagship == null) flagship = g.lead;
            String text = "TF" + g.id + " " + g.role + " " + jobText(g) + " " + balanceText(g.balance);
            engine.addFloatingText(g.lead.getLocation(), text, 26f, COLORS[(g.id - 1) % COLORS.length], g.lead, 0f, 0f);
        }
        if (flagship != null) {
            Vector2f above = new Vector2f(flagship.getLocation().x, flagship.getLocation().y + flagship.getCollisionRadius() + 150f);
            engine.addFloatingText(above, stance.toString(), 34f, STANCE_COLOR, flagship, 0f, 0f);
        }
    }

    private static String jobText(Group g) {
        switch (g.job) {
            case ATTACK: return g.attacking == null ? "SEARCH" : "ATTACK " + g.attacking.core.getHullSpec().getHullName() + (g.free ? "" : " (moving)");
            case HOLD: return "HOLD";
            case SCREEN: return "SCREEN>TF" + g.regroupTo.id;
            case FLANK: return "FLANK " + g.attacking.core.getHullSpec().getHullName() + (g.free ? "" : " (moving)");
            default: return "REGROUP>TF" + g.regroupTo.id;
        }
    }

    private void logState(float t) {
        StringBuilder others = new StringBuilder();
        for (BattlePicture.Cluster c : picture.clusters) {
            if (c.main) continue;
            others.append(String.format(";%s%s(%d,%.0f,gap=%.0f)", c.stray ? "stray:" : "near:", c.core.getName(), c.ships.size(), c.strength, c.gapToMain));
        }
        BattlePicture.Cluster m = picture.main;
        log.info(String.format("DraconisCommander,picture,t=%.0f,stance=%s,clusters=%d,main=%s%s",
                t, stance, picture.clusters.size(),
                m == null ? "none" : String.format("%s(%d,%.0f,reach=%.0f)", m.core.getName(), m.ships.size(), m.strength, m.reach),
                others));
        for (Group g : groups) {
            if (g.lead == null) continue;
            float spread = 0f;
            for (int i = 1; i < g.ships.size(); i++) spread += Misc.getDistance(g.ships.get(i).getLocation(), g.lead.getLocation());
            if (g.ships.size() > 1) spread /= g.ships.size() - 1;
            float mainGap = Float.MAX_VALUE;
            if (m != null) for (ShipAPI s : m.ships) mainGap = Math.min(mainGap, Misc.getDistance(s.getLocation(), g.lead.getLocation()));
            log.info(String.format("DraconisCommander,tf,t=%.0f,group=%d,lead=%s,ships=%d,own=%.0f,job=%s,balance=%s,spread=%.0f,mainGap=%.0f,reissued=%d",
                    t, g.id, g.lead.getName(), g.ships.size(), g.own, jobText(g), balanceText(g.balance), spread,
                    m == null ? -1f : mainGap, reissued));
        }
        reissued = 0;
        spacing.logState(t);
    }

    static String balanceText(float balance) {
        return Float.isInfinite(balance) ? "clear" : String.format("%.1fx", balance);
    }
}
