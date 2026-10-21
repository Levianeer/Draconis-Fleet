package levianeer.draconis.data.scripts.combat.carrierdoctrine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.FighterWingAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

/**
 * Carrier doctrine for the FortySecond Battlegroup: a <b>macro formation layer</b> that puts
 * tag-typed hulls in the right place, layered on top of the vanilla admiral AI, which is left
 * running.
 * <p>
 * This is a port of {@link CarrierDoctrineAI}'s {@code updateFormation} with the fixes this project
 * measured the hard way, and nothing else. Everything that tried to micromanage hulls or fighters is
 * gone. The reasoning is in the record:
 * <pre>
 *   no controller at all (vanilla admiral), n=4   75% wins, median exchange ~1.7
 *   air-group concentration (STRIKE), n=4         25% wins, median exchange  0.28
 *   carrier stations + DEFEND line                        0.19 / 0.11
 *   free hulls, no positional orders                      0.27 / 0.17
 * </pre>
 * The air-group layer was built on the premise that massing wings on one target was the lever. Four
 * battles said otherwise: wing-steering compliance did not predict the result at all (54% on target
 * produced 0.13; 33% produced the single win at 2.50). So the premise was wrong, and what is left is
 * the one thing never tested in isolation - position.
 * <p>
 * <b>Why positional orders are not the thing that lost those battles.</b> The earlier stationed runs
 * changed five things at once: {@code RALLY_CIVILIAN} pinning, a 1s tick, a 1e9 command-point grant,
 * {@code setNoOrders(true)}, and a DEFEND line. The mechanism actually identified was the first -
 * {@code RALLY_CIVILIAN} is the one assignment a ship obeys unconditionally, so a pinned hull cannot
 * vent or back off and dies at maximum flux (every controller run ended with hulls at 0.03-0.19
 * hull; the admiral's ended at 0.40-1.00). {@code RALLY_CARRIER}, {@code RALLY_TASK_FORCE} and
 * {@code DEFEND} are advisory: the hull keeps its own AI and treats the waypoint as where the fight
 * is. Those are what this class issues, and what the legacy controller issued.
 * <p>
 * Fixes carried in from the record:
 * <ul>
 * <li>The carrier standoff point is computed <b>backwards from the enemy centre of mass</b>, never
 *     from our own position. Deriving it from the formation's current location gives a recurrence
 *     whose fixed point sits at ~1.67x the intended standoff. The legacy controller documents this
 *     at its own {@code carrierLoc}; it is reproduced here deliberately.</li>
 * <li>Decision cadence ~5s, matching the admiral ({@code BaseBattleStrategy.o00000()} returns 5.0f,
 *     randomised +/-25% over three staggered trackers). The old controller ticked at ~1s.</li>
 * <li>No {@code setNoOrders(true)}, no {@code clearTasks()}, no command-point grant, and every order
 *     passes {@code useCommandPoint = false}. The admiral's only per-ship pass is gated on having
 *     command points, so that cap is load-bearing.</li>
 * <li>No {@code RALLY_CIVILIAN} anywhere.</li>
 * </ul>
 */
public class TaskForceAI implements DoctrineController {

    private static final Logger log = Global.getLogger(TaskForceAI.class);

    public static final String FORTYSECOND_HULLMOD_ID = "XLII_fortysecond";
    public static final String DRACONIS_HULLMOD_ID = "XLII_draconishull";

    protected static final String DEBUG_MESSAGES_SETTING_ID = "draconisCarrierDoctrineDebugMessages";
    protected static final String BATTLE_LOG_SETTING_ID = "draconisCarrierDoctrineBattleLog";

    public static float FORTYSECOND_MAJORITY_FRACTION = 0.5f;
    public static float DECK_FP_FRACTION = 0.25f;

    /** @see TaskForceAI class javadoc - matches the vanilla admiral's own 5s +/-25%. */
    public static float DECISION_INTERVAL_MIN = 3.75f;
    public static float DECISION_INTERVAL_MAX = 6.25f;

    // ---- formation geometry, ported from CarrierDoctrineAI
    public static float CARRIER_STANDOFF_RANGE_MULT = 1.35f;
    public static float CARRIER_STANDOFF_MIN = 1500f;
    public static float CARRIER_STANDOFF_MAX_MAP_FRACTION = 0.3f;
    /** 0 = sit on the carriers, 1 = sit on the enemy. */
    public static float BATTLELINE_ADVANCE_FRACTION = 0.6f;
    public static float BATTLELINE_ADVANCE_FRACTION_PUSH = 0.75f;
    public static float BATTLELINE_ADVANCE_FRACTION_FALLBACK = 0.35f;
    public static float LOCAL_RATIO_PUSH_THRESHOLD = 1.15f;
    public static float LOCAL_RATIO_FALLBACK_THRESHOLD = 0.85f;
    public static float PICKET_FLANK_RANGE_MULT = 0.75f;
    /** How far a waypoint must move before it is recreated, so the order is not churned every tick. */
    public static float WAYPOINT_REFRESH_THRESHOLD = 150f;
    /** Radius the local force ratio is measured over, around the line. */
    public static float LOCAL_RADIUS = 1500f;
    /** The line stays within this share of our own fighter reach of the carriers, so the carriers'
     * wings can still cover it. Legacy M6: "line-to-enemy distance should stay under about half of
     * fighter engagement range". */
    public static float LINE_WITHIN_FIGHTER_RANGE_FRACTION = 0.5f;

    // ---- wave discipline, the only non-positional behaviour kept
    public static float DECK_REGROUP_FRR_LOW = 0.40f;
    public static float DECK_REGROUP_FRR_HIGH = 0.75f;
    public static float DECK_REGROUP_MAX_FRACTION = 0.5f;

    /** Hulls held off-map by {@code XLII_WarpInScript} sit at (0, +/-20000) with frozen stats, which
     * produced infinite flux readings and 15000-unit distances until they were excluded. */
    public static float OFF_MAP_MARGIN = 500f;

    protected final int owner;
    protected final CombatEngineAPI engine;
    protected final CombatFleetManagerAPI fleetManager;
    protected final CombatFleetManagerAPI enemyFleetManager;
    protected final CombatTaskManagerAPI taskManager;
    protected final boolean allyMode = false;
    protected final float mapWidth;
    protected final float mapHeight;

    protected final boolean debugMessagesEnabled;
    protected TaskForceBattleLog battleLog;

    protected final IntervalUtil tick = new IntervalUtil(DECISION_INTERVAL_MIN, DECISION_INTERVAL_MAX);
    /** The recorder samples on its own ~1s interval, not on the decision tick: matching the admiral's
     * 5s cadence cut the per-ship record's resolution fivefold and made "orders held in N ticks"
     * incomparable with a baseline record. Decision rate and measurement rate are separate concerns. */
    protected final IntervalUtil logTick = new IntervalUtil(0.8f, 1.2f);

    protected boolean abort = false;
    protected boolean active = false;
    protected float totalTime = 0f;

    protected AssignmentInfo carrierAssignment;
    protected AssignmentInfo lineAssignment;
    protected AssignmentInfo guardAssignment;
    protected AssignmentInfo picketAssignment;

    protected final Set<ShipAPI> regrouping = new LinkedHashSet<>();

    // ---- reporting only
    protected List<ShipAPI> lastCarriers = Collections.emptyList();
    protected List<ShipAPI> lastScreen = Collections.emptyList();
    protected List<ShipAPI> lastBattleline = Collections.emptyList();
    protected List<ShipAPI> lastPickets = Collections.emptyList();
    protected Vector2f lastCarrierLoc;
    protected Vector2f lastLineLoc;
    protected Vector2f lastPicketLoc;
    protected int lastDecks = 0;
    protected int lastRegrouping = 0;
    protected int lastOnCarrierStation = 0, lastOnLine = 0, lastOnPicket = 0;
    protected float lastStandoff = 0f, lastLineDist = 0f, lastAdvanceFraction = 0f, lastForceRatio = 1f;
    protected boolean lastCarriersAreTheLine = false;
    protected List<DeployedFleetMemberAPI> lastDeployed = Collections.emptyList();
    protected String lastLoggedGate = null;

    public TaskForceAI(int owner) {
        this.owner = owner;
        engine = Global.getCombatEngine();
        fleetManager = engine.getFleetManager(owner);
        enemyFleetManager = engine.getFleetManager(owner == 0 ? 1 : 0);
        taskManager = fleetManager.getTaskManager(allyMode);
        mapWidth = engine.getMapWidth();
        mapHeight = engine.getMapHeight();

        if (fleetManager.getGoal() == FleetGoal.ESCAPE || enemyFleetManager.getGoal() == FleetGoal.ESCAPE) {
            abort = true;
        }

        debugMessagesEnabled = readSetting(DEBUG_MESSAGES_SETTING_ID);
        if (readSetting(BATTLE_LOG_SETTING_ID)) battleLog = new TaskForceBattleLog(owner, "tf", false);
        log.info("XLII_TF constructed for owner " + owner + (abort ? " (aborted: ESCAPE goal)" : ""));
    }

    protected static boolean readSetting(String key) {
        try {
            return Global.getSettings().getBoolean(key);
        } catch (Exception e) {
            log.warn("Draconis: failed to read " + key + ", defaulting to false", e);
            return false;
        }
    }

    @Override
    public void advance(float amount) {
        if (abort || engine.isPaused()) return;

        totalTime += amount;

        logTick.advance(amount);
        if (logTick.intervalElapsed() && active) record();

        tick.advance(amount);
        if (!tick.intervalElapsed()) return;

        List<DeployedFleetMemberAPI> deployed = new ArrayList<>();
        for (DeployedFleetMemberAPI member : fleetManager.getDeployedCopyDFM()) {
            if (member.isAlly() == allyMode) deployed.add(member);
        }
        if (deployed.isEmpty()) {
            logGate("no deployed members");
            deactivate();
            return;
        }

        String gateFailure = findGateFailure(deployed);
        if (gateFailure != null) {
            logGate("gate failed: " + gateFailure);
            deactivate();
            return;
        }

        List<DeployedFleetMemberAPI> carriers = new ArrayList<>();
        List<DeployedFleetMemberAPI> screen = new ArrayList<>();
        List<DeployedFleetMemberAPI> battleline = new ArrayList<>();
        List<DeployedFleetMemberAPI> pickets = new ArrayList<>();
        List<DeployedFleetMemberAPI> decks = new ArrayList<>();
        bucket(deployed, carriers, screen, battleline, pickets, decks);
        logGate("GATE PASSED: carriers=" + carriers.size() + " screen=" + screen.size()
                + " battleline=" + battleline.size() + " pickets=" + pickets.size()
                + " decks=" + decks.size());

        activateIfNeeded();
        updateDeckReadiness(decks);
        updateFormation(carriers, screen, battleline, pickets);

        lastCarriers = toShips(carriers);
        lastScreen = toShips(screen);
        lastBattleline = toShips(battleline);
        lastPickets = toShips(pickets);
        lastDecks = decks.size();
        lastDeployed = deployed;
    }

    // ------------------------------------------------------------------ gate

    protected String findGateFailure(List<DeployedFleetMemberAPI> deployed) {
        int fortySecondFP = 0;
        int otherDraconisFP = 0;
        int deckFP = 0;

        for (DeployedFleetMemberAPI member : deployed) {
            if (member.isFighterWing() || member.isStation() || member.isStationModule()
                    || member.getShip() == null) continue;
            ShipAPI ship = member.getShip();
            int fp = member.getMember().getFleetPointCost();

            if (ship.getVariant().hasHullMod(FORTYSECOND_HULLMOD_ID)) {
                fortySecondFP += fp;
            } else if (ship.getVariant().hasHullMod(DRACONIS_HULLMOD_ID)) {
                otherDraconisFP += fp;
            } else {
                return ship.getName() + " (hull " + ship.getHullSpec().getHullId() + ") is not Draconis-family";
            }
            if (ship.getNumFighterBays() > 0) deckFP += fp;
        }

        int totalFP = fortySecondFP + otherDraconisFP;
        if (totalFP == 0) return "no deployed non-fighter, non-station ships";
        if (fortySecondFP <= totalFP * FORTYSECOND_MAJORITY_FRACTION) {
            return "FortySecond is not the fleet-point majority (" + fortySecondFP + " of " + totalFP + ")";
        }
        if (deckFP < totalFP * DECK_FP_FRACTION) {
            return "not carrier-centric (" + deckFP + " of " + totalFP + " FP on flight decks)";
        }
        return null;
    }

    /** Note what this does <i>not</i> do: no {@code setNoOrders(true)}, no {@code clearTasks()}, and
     * no {@code getCommandPointsStat()} grant. The previous version did all three. */
    protected void activateIfNeeded() {
        if (active) return;
        active = true;
        message("Task Force Doctrine: ACTIVE (formation layer)");
    }

    protected void deactivate() {
        if (!active) return;
        active = false;

        carrierAssignment = drop(carrierAssignment);
        lineAssignment = drop(lineAssignment);
        guardAssignment = drop(guardAssignment);
        picketAssignment = drop(picketAssignment);
        for (ShipAPI ship : regrouping) {
            if (ship.isAlive()) ship.setPullBackFighters(false);
        }
        regrouping.clear();
        lastRegrouping = 0;
        lastCarriers = Collections.emptyList();
        lastScreen = Collections.emptyList();
        lastBattleline = Collections.emptyList();
        lastPickets = Collections.emptyList();
        lastCarrierLoc = null;
        lastLineLoc = null;
        lastPicketLoc = null;
        lastDecks = 0;
        if (battleLog != null) battleLog.writeSummary();
        message("Task Force Doctrine: INACTIVE");
    }

    protected void bucket(List<DeployedFleetMemberAPI> deployed, List<DeployedFleetMemberAPI> carriers,
                          List<DeployedFleetMemberAPI> screen, List<DeployedFleetMemberAPI> battleline,
                          List<DeployedFleetMemberAPI> pickets, List<DeployedFleetMemberAPI> decks) {
        for (DeployedFleetMemberAPI member : deployed) {
            if (member.isFighterWing() || member.getShip() == null) continue;
            if (member.isStation() || member.isStationModule()) continue;
            ShipAPI ship = member.getShip();
            if (!ship.isAlive() || ship.isHulk()) continue;
            if (ship.isRetreating()) continue;
            if (!member.canBeGivenOrders()) continue;
            if (isOffMap(ship)) continue; // held off-map by the warp-in script; not here yet

            // Any hull with bays counts toward wave discipline whatever its role tag. Tianlong is a
            // CAPITAL_SHIP tagged XLII_BATTLELINE that carries two.
            if (ship.getNumFighterBays() > 0) decks.add(member);

            if (ship.getHullSpec().hasTag(CarrierDoctrineTags.CARRIER)) {
                carriers.add(member);
            } else if (ship.getHullSpec().hasTag(CarrierDoctrineTags.SCREEN)) {
                screen.add(member);
            } else if (ship.getHullSpec().hasTag(CarrierDoctrineTags.PICKET)) {
                pickets.add(member);
            } else {
                // BATTLELINE-tagged and anything untagged. Untagged hulls default to the line rather
                // than to no role at all, because a hull with no order is a hull the admiral has to
                // place, and the whole point of this layer is placing them.
                battleline.add(member);
            }
        }
    }

    // --------------------------------------------------------------- formation

    protected void updateFormation(List<DeployedFleetMemberAPI> carriers, List<DeployedFleetMemberAPI> screen,
                                   List<DeployedFleetMemberAPI> battleline, List<DeployedFleetMemberAPI> pickets) {
        if (!hasAwareEnemy()) return; // nothing to form up against yet; leave existing orders alone

        List<DeployedFleetMemberAPI> body = carriers.isEmpty() ? battleline : carriers;
        Vector2f ourCenter = centerOf(body);
        Vector2f enemyCenter = getEnemyCenterOfMass();
        if (Misc.getDistance(ourCenter, enemyCenter) < 1f) return;

        Vector2f axis = Misc.getUnitVector(ourCenter, enemyCenter);
        Vector2f perp = new Vector2f(axis.y, -axis.x);

        float enemyRange = getEnemyLongestNonMissileRange();
        float standoff = Math.min(
                Math.max(CARRIER_STANDOFF_MIN, enemyRange * CARRIER_STANDOFF_RANGE_MULT),
                Math.min(mapWidth, mapHeight) * CARRIER_STANDOFF_MAX_MAP_FRACTION);
        lastStandoff = standoff;

        // Backwards from the enemy, never from our own position - see the class javadoc. Only the
        // axis direction is taken from where we are, which does not compound.
        Vector2f carrierLoc = new Vector2f(axis);
        carrierLoc.scale(-standoff);
        Vector2f.add(carrierLoc, enemyCenter, carrierLoc);

        // Who actually forms the line. With no battleline hull deployed the screen takes it over;
        // with neither, the carriers' own standoff point *is* the line and nothing is pushed
        // forward. The test fleets run 4-7 carriers against 0-1 screen and 0-6 battleline, so the
        // no-line case is the common one, not an edge case.
        List<DeployedFleetMemberAPI> lineHolders = battleline;
        List<DeployedFleetMemberAPI> reservedGuard = Collections.emptyList();
        if (lineHolders.isEmpty() && !screen.isEmpty()) {
            // Leyte rule: never send the entire screen forward - hold one back for the carriers.
            if (screen.size() > 1) {
                reservedGuard = new ArrayList<>(screen.subList(0, 1));
                lineHolders = new ArrayList<>(screen.subList(1, screen.size()));
            } else {
                lineHolders = screen;
            }
        }
        lastCarriersAreTheLine = lineHolders.isEmpty();

        Vector2f lineLoc;
        if (lastCarriersAreTheLine) {
            lineLoc = new Vector2f(carrierLoc);
            lastAdvanceFraction = 0f;
            lastForceRatio = 1f;
        } else {
            float ratio = localForceRatio(lastLineLoc != null ? lastLineLoc : carrierLoc);
            float fraction = resolveBattlelineAdvanceFraction(ratio);
            lastForceRatio = ratio;
            lastAdvanceFraction = fraction;

            Vector2f toEnemy = Vector2f.sub(enemyCenter, carrierLoc, new Vector2f());
            toEnemy.scale(fraction);
            lineLoc = Vector2f.add(carrierLoc, toEnemy, new Vector2f());

            // Keep the line inside the carriers' own wing reach, so the air group can cover it.
            float fighterRange = ownFighterReach(carriers);
            if (fighterRange > 0f) {
                float cap = fighterRange * LINE_WITHIN_FIGHTER_RANGE_FRACTION;
                if (Misc.getDistance(carrierLoc, lineLoc) > cap) {
                    Vector2f capped = Misc.getUnitVector(carrierLoc, lineLoc);
                    capped.scale(cap);
                    lineLoc = Vector2f.add(capped, carrierLoc, new Vector2f());
                }
            }
        }

        lastCarrierLoc = new Vector2f(carrierLoc);
        lastLineLoc = new Vector2f(lineLoc);
        lastLineDist = Misc.getDistance(lineLoc, enemyCenter);

        carrierAssignment = holdPosition(carriers, CombatAssignmentType.RALLY_CARRIER,
                carrierLoc, carrierAssignment);
        lastOnCarrierStation = countOn(carriers, carrierAssignment);

        if (!lineHolders.isEmpty()) {
            lineAssignment = holdPosition(lineHolders, CombatAssignmentType.RALLY_TASK_FORCE,
                    lineLoc, lineAssignment);
            lastOnLine = countOn(lineHolders, lineAssignment);
        } else {
            lineAssignment = drop(lineAssignment);
            lastOnLine = 0;
        }

        if (!reservedGuard.isEmpty()) {
            guardAssignment = holdPosition(reservedGuard, CombatAssignmentType.DEFEND,
                    carrierLoc, guardAssignment);
        } else {
            guardAssignment = drop(guardAssignment);
        }

        if (!pickets.isEmpty()) {
            float basis = ownFighterReach(carriers);
            if (basis <= 0f) basis = standoff;
            Vector2f picketLoc = new Vector2f(perp);
            picketLoc.scale(basis * PICKET_FLANK_RANGE_MULT);
            Vector2f.add(picketLoc, lineLoc, picketLoc);
            lastPicketLoc = new Vector2f(picketLoc);
            picketAssignment = holdPosition(pickets, CombatAssignmentType.DEFEND,
                    picketLoc, picketAssignment);
            lastOnPicket = countOn(pickets, picketAssignment);
        } else {
            picketAssignment = drop(picketAssignment);
            lastPicketLoc = null;
            lastOnPicket = 0;
        }
    }

    /**
     * One shared advisory waypoint order, re-given to each member every decision tick.
     * <p>
     * Ported from {@code CarrierDoctrineAI.holdPosition}. The waypoint is only recreated once the
     * intended point has moved more than {@link #WAYPOINT_REFRESH_THRESHOLD}, so a hull is not
     * handed a brand new order every tick. {@code useCommandPoint} is false throughout.
     */
    protected AssignmentInfo holdPosition(List<DeployedFleetMemberAPI> members, CombatAssignmentType type,
                                          Vector2f loc, AssignmentInfo existing) {
        if (members.isEmpty()) return drop(existing);
        if (existing != null) {
            AssignmentTargetAPI target = existing.getTarget();
            if (target == null
                    || Misc.getDistance(target.getLocation(), loc) > WAYPOINT_REFRESH_THRESHOLD) {
                taskManager.removeAssignment(existing);
                existing = null;
            }
        }
        if (existing == null) {
            AssignmentTargetAPI wp = taskManager.createWaypoint2(loc, allyMode);
            existing = taskManager.createAssignment(type, wp, false);
        }
        if (existing == null) return null;
        for (DeployedFleetMemberAPI member : members) {
            if (taskManager.getAssignmentFor(member.getShip()) != existing) {
                taskManager.giveAssignment(member, existing, false);
            }
        }
        return existing;
    }

    protected AssignmentInfo drop(AssignmentInfo existing) {
        if (existing != null) taskManager.removeAssignment(existing);
        return null;
    }

    /** How many of {@code members} are actually holding {@code assignment} - read back from the task
     * manager rather than assumed from having issued it, because an order being accepted and an
     * order being held are different things and have diverged before. */
    protected int countOn(List<DeployedFleetMemberAPI> members, AssignmentInfo assignment) {
        if (assignment == null) return 0;
        int count = 0;
        for (DeployedFleetMemberAPI member : members) {
            if (taskManager.getAssignmentFor(member.getShip()) == assignment) count++;
        }
        return count;
    }

    /**
     * How far forward the line sits, scaled by whether we are locally winning there.
     * <p>
     * Ported from the legacy M9 rule: a fixed geometry point regardless of whether the line is
     * winning or losing is exactly what lets an opponent concentrate on one part of it for free.
     */
    protected float resolveBattlelineAdvanceFraction(float ratio) {
        if (ratio <= LOCAL_RATIO_FALLBACK_THRESHOLD) return BATTLELINE_ADVANCE_FRACTION_FALLBACK;
        if (ratio >= LOCAL_RATIO_PUSH_THRESHOLD) return BATTLELINE_ADVANCE_FRACTION_PUSH;
        if (ratio < 1f) {
            float t = (ratio - LOCAL_RATIO_FALLBACK_THRESHOLD) / (1f - LOCAL_RATIO_FALLBACK_THRESHOLD);
            return BATTLELINE_ADVANCE_FRACTION_FALLBACK
                    + t * (BATTLELINE_ADVANCE_FRACTION - BATTLELINE_ADVANCE_FRACTION_FALLBACK);
        }
        float t = (ratio - 1f) / (LOCAL_RATIO_PUSH_THRESHOLD - 1f);
        return BATTLELINE_ADVANCE_FRACTION
                + t * (BATTLELINE_ADVANCE_FRACTION_PUSH - BATTLELINE_ADVANCE_FRACTION);
    }

    /**
     * Our combat weight against theirs within {@link #LOCAL_RADIUS} of {@code where}.
     * <p>
     * Simplified from the legacy controller's battle-group version: this measures one radius around
     * the line rather than resolving enemy groups, which is enough for the push/fall-back decision
     * the fraction above makes. Measured around <i>last</i> tick's line point, so the ratio lags one
     * tick - acceptable because it only clamps a fraction, and it does not feed the position
     * recurrence that the standoff computation was careful to avoid.
     */
    protected float localForceRatio(Vector2f where) {
        float theirs = Misc.countEnemyWeightInArcAroundLocation(
                owner, where, 0f, 360f, LOCAL_RADIUS, null, true, true);
        float ours = 0f;
        for (ShipAPI ship : engine.getShips()) {
            if (ship.isFighter() || ship.isDrone() || ship.getOwner() != owner) continue;
            if (!ship.isAlive() || ship.isHulk() || ship.isStationModule()) continue;
            if (isOffMap(ship)) continue;
            if (Misc.getDistance(ship.getLocation(), where) > LOCAL_RADIUS) continue;
            ours += Misc.getShipWeight(ship, true);
        }
        if (theirs <= 0f) return LOCAL_RATIO_PUSH_THRESHOLD;
        return ours / theirs;
    }

    /** Shortest live wing reach across the carriers - the line has to be coverable by all of them,
     * not just the longest-legged one. */
    protected float ownFighterReach(List<DeployedFleetMemberAPI> carriers) {
        float min = Float.MAX_VALUE;
        for (DeployedFleetMemberAPI member : carriers) {
            if (member.getShip() == null) continue;
            for (FighterWingAPI wing : member.getShip().getAllWings()) {
                if (wing.isDestroyed() || wing.getSpec() == null) continue;
                float range = wing.getRange();
                if (range <= 0f) range = wing.getSpec().getRange();
                if (range > 0f) min = Math.min(min, range);
            }
        }
        return min == Float.MAX_VALUE ? 0f : min;
    }

    /** Longest non-missile gun range among deployed, aware enemy ships. Stations and their modules
     * are excluded: a station's range is a siege-balance number, not a line-of-battle one. */
    protected float getEnemyLongestNonMissileRange() {
        float max = 0f;
        for (ShipAPI ship : engine.getShips()) {
            if (ship.isFighter() || ship.isDrone() || ship.getOwner() == owner) continue;
            if (ship.isStation() || ship.isStationModule()) continue;
            if (!ship.isAlive() || ship.isHulk()) continue;
            if (!engine.isAwareOf(owner, ship)) continue;
            for (WeaponAPI weapon : ship.getAllWeapons()) {
                WeaponType type = weapon.getType();
                if (type == WeaponType.MISSILE || type == WeaponType.LAUNCH_BAY) continue;
                if (weapon.getRange() > max) max = weapon.getRange();
            }
        }
        return max;
    }

    // ------------------------------------------------------------ wave discipline

    /**
     * Holds a depleted deck's wings so they land and re-form into a wave instead of trickling into
     * the fight one at a time. Hysteretic on each deck's own replacement rate.
     * <p>
     * The only non-positional behaviour kept from the air-group work: it never touches the
     * assignment slot, and it addresses a directly reported failure - "they get reinforced by a
     * trickle of fighters 1 by 1, instead of recalling back to the carrier".
     */
    protected void updateDeckReadiness(List<DeployedFleetMemberAPI> decks) {
        Set<ShipAPI> present = shipsOf(decks);
        for (ShipAPI ship : regrouping) {
            if (!present.contains(ship) && ship.isAlive()) ship.setPullBackFighters(false);
        }
        regrouping.retainAll(present);

        int regroupBudget = Math.max(1, (int) (decks.size() * DECK_REGROUP_MAX_FRACTION));
        for (DeployedFleetMemberAPI member : decks) {
            ShipAPI ship = member.getShip();
            float frr = ship.getSharedFighterReplacementRate();
            if (regrouping.contains(ship)) {
                if (frr >= DECK_REGROUP_FRR_HIGH) regrouping.remove(ship);
            } else if (frr < DECK_REGROUP_FRR_LOW && regrouping.size() < regroupBudget) {
                regrouping.add(ship);
                message("Task Force Doctrine: REGROUP " + ship.getName());
            }
            ship.setPullBackFighters(regrouping.contains(ship));
        }
        lastRegrouping = regrouping.size();
    }

    // ----------------------------------------------------------- reporting

    protected void record() {
        if (battleLog == null) return;
        battleLog.tick(totalTime, lastCarriers.size(), lastScreen.size(), lastBattleline.size(),
                lastPickets.size(), lastDecks,
                lastOnCarrierStation, lastOnLine, lastOnPicket, lastRegrouping,
                lastStandoff, lastLineDist, lastAdvanceFraction, lastForceRatio,
                lastCarriersAreTheLine, taskManager.getCommandPointsLeft(),
                "carriers=" + lastCarriers.size() + " screen=" + lastScreen.size()
                        + " battleline=" + lastBattleline.size() + " pickets=" + lastPickets.size()
                        + " decks=" + lastDecks,
                assignmentCensus(lastDeployed), taskManager, fleetManager, enemyFleetManager);
    }

    protected String assignmentCensus(List<DeployedFleetMemberAPI> deployed) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (DeployedFleetMemberAPI member : deployed) {
            ShipAPI ship = member.getShip();
            if (ship == null) continue;
            AssignmentInfo info = taskManager.getAssignmentFor(ship);
            String key = info == null ? "NONE" : info.getType().name();
            counts.merge(key, 1, Integer::sum);
        }
        return counts.toString().replace(", ", " ");
    }

    protected void logGate(String reason) {
        if (reason.equals(lastLoggedGate)) return;
        lastLoggedGate = reason;
        log.info("XLII_TF " + reason);
    }

    protected void message(String text) {
        log.info("XLII_TF " + text);
        if (debugMessagesEnabled && engine.getCombatUI() != null) {
            engine.getCombatUI().addMessage(0, text);
        }
    }

    // -------------------------------------------------------------- helpers

    protected boolean hasAwareEnemy() {
        for (ShipAPI ship : engine.getShips()) {
            if (ship.isFighter() || ship.isDrone() || ship.getOwner() == owner) continue;
            if (!ship.isAlive() || ship.isHulk()) continue;
            if (engine.isAwareOf(owner, ship)) return true;
        }
        return false;
    }

    protected Vector2f getEnemyCenterOfMass() {
        Vector2f com = new Vector2f();
        int count = 0;
        for (ShipAPI ship : engine.getShips()) {
            if (ship.isFighter() || ship.isDrone() || ship.getOwner() == owner) continue;
            if (ship.isStationModule()) continue;
            if (!ship.isAlive() || ship.isHulk()) continue;
            if (!engine.isAwareOf(owner, ship)) continue;
            Vector2f.add(ship.getLocation(), com, com);
            count++;
        }
        if (count > 0) com.scale(1f / count);
        return com;
    }

    protected Vector2f centerOf(List<DeployedFleetMemberAPI> members) {
        Vector2f com = new Vector2f();
        int count = 0;
        for (DeployedFleetMemberAPI member : members) {
            if (member.getShip() == null) continue;
            Vector2f.add(member.getShip().getLocation(), com, com);
            count++;
        }
        if (count > 0) com.scale(1f / count);
        return com;
    }

    protected boolean isOffMap(ShipAPI ship) {
        Vector2f loc = ship.getLocation();
        return Math.abs(loc.x) > mapWidth * 0.5f + OFF_MAP_MARGIN
                || Math.abs(loc.y) > mapHeight * 0.5f + OFF_MAP_MARGIN;
    }

    protected Set<ShipAPI> shipsOf(List<DeployedFleetMemberAPI> members) {
        Set<ShipAPI> ships = new LinkedHashSet<>();
        for (DeployedFleetMemberAPI member : members) {
            if (member.getShip() != null) ships.add(member.getShip());
        }
        return ships;
    }

    protected List<ShipAPI> toShips(List<DeployedFleetMemberAPI> members) {
        List<ShipAPI> ships = new ArrayList<>(members.size());
        for (DeployedFleetMemberAPI member : members) {
            if (member.getShip() != null) ships.add(member.getShip());
        }
        return ships;
    }
}
