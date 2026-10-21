package levianeer.draconis.data.scripts.combat.carrierdoctrine;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.apache.log4j.Logger;

/**
 * Per-battle recorder for {@link TaskForceAI}: one CSV row per sample plus a continuously-rewritten
 * summary, both written to the game's common folder via
 * {@link com.fs.starfarer.api.SettingsAPI#writeTextFileToCommon}.
 * <p>
 * <b>Must go through that API, not {@code java.io}.</b> A first version used {@code FileWriter} and
 * was refused at class-load time with {@code java.lang.SecurityException: File access and reflection
 * are not allowed to scripts} - Starsector sandboxes direct file access for mod scripts, which is
 * why {@code SettingsAPI} carries read <i>and</i> write helpers for the common folder. Files land as
 * {@code saves/common/<name>.data}.
 * <p>
 * The summary is rewritten periodically rather than once at the end, because a playtest is routinely
 * paused or abandoned mid-battle - a summary that only appeared on a clean finish would have been
 * missing for most of this system's history.
 * <p>
 * <b>Note on provenance:</b> this file was reconstructed after a bad regex deletion destroyed it.
 * {@code trackShips}, {@code tickOutcomeOnly}, the per-ship and attrition print blocks and the field
 * set are as they were; the surrounding scaffolding was rewritten from the behaviour it had to
 * reproduce, so some comment wording here is newer than the code it describes.
 */
public class TaskForceBattleLog {

    private static final Logger log = Global.getLogger(TaskForceBattleLog.class);

    public static final String PREFIX = "draconis_tf_battle_";
    /** Summary flush cadence, in samples. */
    public static final int SUMMARY_INTERVAL = 5;
    /** Consecutive samples a side must be absent from the field before it counts as eliminated.
     * <p>
     * A simulator battle never formally ends, so a verdict has to be inferred - and a side can read
     * as empty for a single sample while a hull is mid-explosion or mid-warp. */
    public static final int ELIMINATED_TICKS = 3;
    public static final float OFF_MAP_MARGIN = 500f;

    protected final int owner;
    protected final boolean minimal;
    protected final String label;
    protected final CombatEngineAPI engine;
    protected final String battleId;
    protected final String csvName;
    protected final String summaryName;
    protected boolean broken = false;
    /** Rows are accumulated and the whole CSV rewritten on each flush, because the write API takes
     * a complete file rather than appending. ~120 bytes a sample, so a long battle is tens of KB. */
    protected final StringBuilder csvRows = new StringBuilder();

    protected int ticks = 0;
    protected float firstTime = -1f, lastTime = 0f;
    protected String startComposition = "?";
    protected float ourStartDP = 0f, enemyStartDP = 0f;
    protected float ourDP = 0f, enemyDP = 0f;

    protected int regroupTicks = 0, regroupMax = 0;
    protected int cpMin = Integer.MAX_VALUE, cpMax = 0;
    protected float standoffMin = Float.MAX_VALUE, standoffMax = 0f;
    protected float lineDistMin = Float.MAX_VALUE, lineDistMax = 0f;
    protected float advanceMin = Float.MAX_VALUE, advanceMax = 0f;
    protected float ratioMin = Float.MAX_VALUE, ratioMax = 0f;
    /** Samples where no battleline or screen hull existed, so the carrier standoff point was the line. */
    protected int carriersWereTheLineTicks = 0;
    /** Hull-samples holding each station against the role totals they are drawn from. The ratio is
     * the only thing that says whether an advisory order is actually being taken up - an assignment
     * being accepted and an assignment being held are different, and have diverged silently twice. */
    protected long onCarrierStationSum = 0, carrierHullTicks = 0;
    protected long onLineSum = 0, lineHullTicks = 0;
    protected long onPicketSum = 0, picketHullTicks = 0;

    protected int ourHulls = 0, enemyHulls = 0, ourStartHulls = 0, enemyStartHulls = 0;
    protected int ourOnField = 0, enemyOnField = 0, ourReserve = 0, enemyReserve = 0;
    protected boolean ourEverOnField = false, enemyEverOnField = false;
    /** Names of our hulls still on the field, so a lingering phantom survivor can be identified
     * rather than guessed at - a count of 1 has repeatedly disagreed with what is on screen. */
    protected final List<String> survivors = new ArrayList<>();
    protected CombatTaskManagerAPI tasks;

    /**
     * Per-ship behaviour record. The summary was fleet-aggregate only, so a report about one ship's
     * conduct - "pay attention to the actions of Musashi" - could not be answered from the data.
     * <p>
     * Keyed by ship <b>identity</b>, not by name. The simulator does not give ships unique names -
     * two hulls of the same class both come through as the same {@code SIM <name>} - so a name key
     * silently merged sister ships into one record and added their sample counts. That produced
     * {@code NONE:734} in a 446-sample battle.
     */
    protected final Map<ShipAPI, ShipRecord> shipRecords = new LinkedHashMap<>();

    protected static class ShipRecord {
        String name = "?";
        int ticks;
        int lastSeenTick;
        float maxDistFromFleet;
        float distSum;
        float minHull = 1f;
        float maxFlux;
        final Map<String, Integer> orders = new LinkedHashMap<>();
        String fate = "alive";
    }

    protected int ourGoneTicks = 0, enemyGoneTicks = 0;
    protected final List<String> attrition = new ArrayList<>();
    protected String lastBuckets = null;

    /**
     * @param label   distinguishes controllers in the filename, so a like-for-like A/B series can be
     *                run without the records colliding.
     * @param minimal record only the outcome, DP and hull counts plus the engine-read sections - for
     *                a controller this class cannot instrument from the inside.
     */
    public TaskForceBattleLog(int owner, String label, boolean minimal) {
        this.owner = owner;
        this.minimal = minimal;
        this.label = label;
        this.engine = Global.getCombatEngine();
        this.battleId = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        this.csvName = PREFIX + label + "_" + battleId + ".csv";
        this.summaryName = PREFIX + label + "_" + battleId + "_summary.txt";

        csvRows.append("t,carriers,screen,battleline,pickets,decks,")
                .append("onCarrierStation,onLine,onPicket,regrouping,")
                .append("standoff,lineDist,advanceFraction,forceRatio,carriersAreTheLine,cp,")
                .append("ourDP,enemyDP,assigned\n");
        log.info("XLII_TF battle log: common/" + summaryName);
    }

    public void tick(float time, int carriers, int screen, int battleline, int pickets, int decks,
                     int onCarrierStation, int onLine, int onPicket, int regrouping,
                     float standoff, float lineDist, float advanceFraction, float forceRatio,
                     boolean carriersAreTheLine, int commandPointsLeft,
                     String composition, String assigned, CombatTaskManagerAPI tasks,
                     CombatFleetManagerAPI ours, CombatFleetManagerAPI theirs) {
        if (broken) return;

        ticks++;
        if (firstTime < 0f) {
            firstTime = time;
            startComposition = composition;
        }
        lastTime = time;

        this.tasks = tasks;
        updateSides(ours, theirs);
        trackShips();

        regroupTicks += regrouping;
        regroupMax = Math.max(regroupMax, regrouping);
        cpMin = Math.min(cpMin, commandPointsLeft);
        cpMax = Math.max(cpMax, commandPointsLeft);

        if (standoff > 0f) {
            standoffMin = Math.min(standoffMin, standoff);
            standoffMax = Math.max(standoffMax, standoff);
        }
        if (lineDist > 0f) {
            lineDistMin = Math.min(lineDistMin, lineDist);
            lineDistMax = Math.max(lineDistMax, lineDist);
        }
        if (carriersAreTheLine) {
            carriersWereTheLineTicks++;
        } else {
            advanceMin = Math.min(advanceMin, advanceFraction);
            advanceMax = Math.max(advanceMax, advanceFraction);
            ratioMin = Math.min(ratioMin, forceRatio);
            ratioMax = Math.max(ratioMax, forceRatio);
        }

        onCarrierStationSum += onCarrierStation;
        carrierHullTicks += carriers;
        onLineSum += onLine;
        lineHullTicks += carriersAreTheLine ? 0 : (battleline > 0 ? battleline : screen);
        onPicketSum += onPicket;
        picketHullTicks += pickets;

        String buckets = carriers + "/" + screen + "/" + battleline + "/" + pickets;
        if (!buckets.equals(lastBuckets)) {
            attrition.add(String.format(
                    "  t=%-4.0f carriers=%d screen=%d battleline=%d pickets=%d  (ourDP=%.0f enemyDP=%.0f)",
                    time, carriers, screen, battleline, pickets, ourDP, enemyDP));
            lastBuckets = buckets;
        }

        csvRows.append(String.format("%.0f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.0f,%.0f,%.2f,%.2f,%s,%d,%.0f,%.0f,%s%n",
                time, carriers, screen, battleline, pickets, decks,
                onCarrierStation, onLine, onPicket, regrouping,
                standoff, lineDist, advanceFraction, forceRatio, carriersAreTheLine,
                commandPointsLeft, ourDP, enemyDP, assigned.replace(',', ' ')));

        if (ticks % SUMMARY_INTERVAL == 1) writeSummary();
    }

    /**
     * The controller-agnostic path, for a fleet this class cannot instrument from the inside - the
     * legacy controller, or no controller at all.
     * <p>
     * Nothing in this project measured the legacy controller or the no-orders baseline with the same
     * numbers used to judge the rewrite, so every comparison between them was an impression until
     * this existed.
     */
    public void tickOutcomeOnly(float time, CombatFleetManagerAPI ours, CombatFleetManagerAPI theirs) {
        if (broken) return;
        ticks++;
        if (firstTime < 0f) {
            firstTime = time;
            startComposition = "(not instrumented)";
        }
        lastTime = time;
        // tasks stays null on this path - the fleet is observed from outside, so the per-ship record
        // carries distance and damage but reports its orders as NONE.
        updateSides(ours, theirs);
        trackShips();
        if (ticks % SUMMARY_INTERVAL == 1) writeSummary();
    }

    /**
     * Counts both sides from the engine, and tracks peak deployment points rather than an initial
     * reading.
     * <p>
     * Reserves must not count toward "on field": {@code getReservesCopy()} in the simulator is the
     * static full roster and never empties, which masked all attrition - six of seven legacy battles
     * recorded 434 -> 434 while losing every ship. And {@code XLII_WarpInScript} holds hulls off-map
     * for 15-45s, so a recorder that starts on the first frame captures an empty field and then
     * watches DP <i>rise</i> as ships arrive. Peak-to-current is immune to both: it is the most
     * either side ever had committed against what it has left, whatever order they deployed in.
     */
    protected void updateSides(CombatFleetManagerAPI ours, CombatFleetManagerAPI theirs) {
        survivors.clear();
        float[] mine = countFieldAndDP(owner, true);
        float[] yours = countFieldAndDP(owner == 0 ? 1 : 0, false);

        ourDP = mine[0];
        ourOnField = (int) mine[1];
        enemyDP = yours[0];
        enemyOnField = (int) yours[1];

        ourReserve = ours != null ? ours.getReservesCopy().size() : 0;
        enemyReserve = theirs != null ? theirs.getReservesCopy().size() : 0;
        ourHulls = ourOnField + ourReserve;
        enemyHulls = enemyOnField + enemyReserve;

        ourStartDP = Math.max(ourStartDP, ourDP);
        enemyStartDP = Math.max(enemyStartDP, enemyDP);
        ourStartHulls = Math.max(ourStartHulls, ourHulls);
        enemyStartHulls = Math.max(enemyStartHulls, enemyHulls);
        if (ourOnField > 0) ourEverOnField = true;
        if (enemyOnField > 0) enemyEverOnField = true;
        ourGoneTicks = (ourEverOnField && ourOnField == 0) ? ourGoneTicks + 1 : 0;
        enemyGoneTicks = (enemyEverOnField && enemyOnField == 0) ? enemyGoneTicks + 1 : 0;
    }

    /**
     * Deployment points and hull count for one side, read off the engine.
     * <p>
     * Excludes fighters, drones, station modules, hulks, hulls held off-map by the warp-in script,
     * and <b>retreating hulls</b> - a ship on its way out is not on the field, and counting it is
     * what produced a phantom survivor that disagreed with the screen.
     */
    protected float[] countFieldAndDP(int side, boolean recordSurvivors) {
        float dp = 0f;
        int count = 0;
        float halfW = engine.getMapWidth() * 0.5f + OFF_MAP_MARGIN;
        float halfH = engine.getMapHeight() * 0.5f + OFF_MAP_MARGIN;
        for (ShipAPI ship : engine.getShips()) {
            if (ship.isFighter() || ship.isDrone() || ship.isStationModule()) continue;
            if (ship.getOwner() != side || !ship.isAlive() || ship.isHulk()) continue;
            if (ship.isRetreating()) continue;
            if (Math.abs(ship.getLocation().x) > halfW || Math.abs(ship.getLocation().y) > halfH) continue;

            CombatFleetManagerAPI manager = engine.getFleetManager(side);
            DeployedFleetMemberAPI member = manager != null ? manager.getDeployedFleetMember(ship) : null;
            FleetMemberAPI fm = member != null ? member.getMember() : null;
            if (fm != null) dp += fm.getDeploymentPointsCost();
            count++;
            if (recordSurvivors) survivors.add(ship.getName() + "(own=" + ship.getOwner() + ")");
        }
        return new float[] { dp, count };
    }

    protected void trackShips() {
        List<ShipAPI> mine = new ArrayList<>();
        float cx = 0f, cy = 0f;
        float halfW = engine.getMapWidth() * 0.5f + OFF_MAP_MARGIN;
        float halfH = engine.getMapHeight() * 0.5f + OFF_MAP_MARGIN;
        for (ShipAPI ship : engine.getShips()) {
            if (ship.isFighter() || ship.isDrone() || ship.isStationModule()) continue;
            if (ship.getOwner() != owner || !ship.isAlive() || ship.isHulk()) continue;
            // Off-map hulls are held by XLII_WarpInScript, which freezes their stats - so their
            // flux level divides by a zeroed capacity and reads Infinity, and their distance from
            // the fleet reads ~15000 from the holding point at (0, +/-20000). Both polluted the
            // per-ship record on its first run.
            if (Math.abs(ship.getLocation().x) > halfW || Math.abs(ship.getLocation().y) > halfH) continue;
            mine.add(ship);
            cx += ship.getLocation().x;
            cy += ship.getLocation().y;
        }
        if (mine.isEmpty()) return;
        cx /= mine.size();
        cy /= mine.size();

        for (ShipAPI ship : mine) {
            ShipRecord r = shipRecords.get(ship);
            if (r == null) {
                r = new ShipRecord();
                r.name = ship.getName();
                shipRecords.put(ship, r);
            }
            r.ticks++;
            float dist = (float) Math.hypot(ship.getLocation().x - cx, ship.getLocation().y - cy);
            r.maxDistFromFleet = Math.max(r.maxDistFromFleet, dist);
            r.distSum += dist;
            r.minHull = Math.min(r.minHull, ship.getHullLevel());
            r.maxFlux = Math.max(r.maxFlux, ship.getFluxTracker().getFluxLevel());

            String order = "NONE";
            if (tasks != null) {
                AssignmentInfo info = tasks.getAssignmentFor(ship);
                if (info != null) order = info.getType().name();
            }
            if (ship.isRetreating()) order = "RETREATING";
            r.orders.merge(order, 1, Integer::sum);
            r.fate = ship.isRetreating() ? "retreated" : "alive";
            r.lastSeenTick = ticks;
        }
    }

    public void writeSummary() {
        if (broken || ticks == 0) return;

        StringBuilder sb = new StringBuilder();
        // Single parseable line first: a 10+ battle test series is read by grepping this one row per
        // file rather than the whole summary.
        sb.append(String.format(
                "RESULT ctrl=%s outcome=%s dur=%.0f ourDP=%.0f/%.0f enemyDP=%.0f/%.0f ratio=%s "
                        + "ourHulls=%d/%d enemyHulls=%d/%d ourField=%d+%dres enemyField=%d+%dres "
                        + "onStation=%d%% onLine=%d%% onPicket=%d%% carriersWereLine=%d/%d "
                        + "standoff=%.0f..%.0f lineDist=%.0f..%.0f cp=%d..%d comp=%s "
                        + "survivors=[%s]%n",
                label, outcome(), lastTime - firstTime, ourStartDP, ourDP, enemyStartDP, enemyDP,
                exchangeRatio(),
                ourStartHulls, ourHulls, enemyStartHulls, enemyHulls,
                ourOnField, ourReserve, enemyOnField, enemyReserve,
                pct(onCarrierStationSum, carrierHullTicks), pct(onLineSum, lineHullTicks),
                pct(onPicketSum, picketHullTicks), carriersWereTheLineTicks, ticks,
                standoffMin == Float.MAX_VALUE ? 0f : standoffMin, standoffMax,
                lineDistMin == Float.MAX_VALUE ? 0f : lineDistMin, lineDistMax,
                cpMin == Integer.MAX_VALUE ? 0 : cpMin, cpMax, startComposition.replace(' ', ','),
                String.join(", ", survivors).replace(' ', '_')));

        sb.append("XLII_TF battle ").append(battleId)
                .append("  (controller owner=").append(owner).append(")\n");
        line(sb, "duration", String.format("%.0fs (%d samples)", lastTime - firstTime, ticks));
        line(sb, "composition", startComposition);
        line(sb, "DP  ours", String.format("peak %.0f -> %.0f on field", ourStartDP, ourDP));
        line(sb, "DP  enemy", String.format("peak %.0f -> %.0f on field", enemyStartDP, enemyDP));
        line(sb, "DP  exchange", String.format("we lost %.0f, they lost %.0f",
                ourStartDP - ourDP, enemyStartDP - enemyDP));
        line(sb, "hulls", String.format("ours %d -> %d, theirs %d -> %d",
                ourStartHulls, ourHulls, enemyStartHulls, enemyHulls));
        line(sb, "outcome", outcome());
        line(sb, "on field", String.format("ours %d (+%d reserve), theirs %d (+%d reserve)",
                ourOnField, ourReserve, enemyOnField, enemyReserve));
        line(sb, "our survivors", survivors.isEmpty() ? "none" : String.join(", ", survivors));

        // Only the controller-specific block is gated. Everything below - attrition, the per-ship
        // record - is read from the engine rather than from the controller, so it is written on
        // every path. That is what makes a baseline or legacy record comparable to a task-force one.
        if (!minimal) {
            sb.append('\n');
            line(sb, "carrier standoff", range(standoffMin, standoffMax));
            line(sb, "line to enemy", range(lineDistMin, lineDistMax));
            line(sb, "advance fraction", carriersWereTheLineTicks == ticks ? "no line held"
                    : String.format("%.2f..%.2f", advanceMin, advanceMax));
            line(sb, "local force ratio", carriersWereTheLineTicks == ticks ? "not measured"
                    : String.format("%.2f..%.2f", ratioMin, ratioMax));
            line(sb, "carriers were the line",
                    carriersWereTheLineTicks + " of " + ticks + " samples (no battleline or screen hull)");
            line(sb, "holding station", String.format("carriers %d%%, line %d%%, pickets %d%%",
                    pct(onCarrierStationSum, carrierHullTicks), pct(onLineSum, lineHullTicks),
                    pct(onPicketSum, picketHullTicks)));
            line(sb, "deck regroup", String.format("max %d decks at once, %d deck-samples total",
                    regroupMax, regroupTicks));
            // Watched because this controller deliberately does not grant itself command points and
            // leaves the admiral's own escort pass, which is gated on having them, able to run.
            line(sb, "command points", (cpMin == Integer.MAX_VALUE ? 0 : cpMin) + ".." + cpMax);
        }

        sb.append('\n').append("ATTRITION (carriers/screen/battleline/pickets)\n");
        for (String row : attrition) sb.append(row).append('\n');

        sb.append('\n').append("PER-SHIP (orders held, in samples | dist from fleet | worst hull/flux)\n");
        for (Map.Entry<ShipAPI, ShipRecord> e : shipRecords.entrySet()) {
            ShipAPI ship = e.getKey();
            ShipRecord r = e.getValue();
            StringBuilder ord = new StringBuilder();
            for (Map.Entry<String, Integer> o : r.orders.entrySet()) {
                ord.append(ord.isEmpty() ? "" : " ").append(o.getKey()).append(':').append(o.getValue());
            }
            // A retreating hull leaves the engine, after which isAlive() reads false - so checking
            // liveness first labelled every withdrawal "destroyed". The last observed state wins;
            // liveness only decides the cases it never saw.
            String fate = r.fate;
            if (!"retreated".equals(fate)) {
                if (!ship.isAlive() || ship.isHulk()) fate = "destroyed";
                else if (r.lastSeenTick < ticks - 2) fate = "gone";
            }
            sb.append(String.format("  %-26s %-44s | mean %4.0f max %5.0f | hull %.2f flux %.2f | %s%n",
                    r.name, ord, r.distSum / Math.max(1, r.ticks), r.maxDistFromFleet,
                    r.minHull, r.maxFlux, fate));
        }

        try {
            Global.getSettings().writeTextFileToCommon(summaryName, sb.toString());
            Global.getSettings().writeTextFileToCommon(csvName, csvRows.toString());
        } catch (Exception e) {
            log.warn("XLII_TF battle log write failed", e);
            broken = true;
        }
    }

    protected static int pct(long part, long whole) {
        return whole <= 0L ? 0 : (int) Math.round(100.0 * part / whole);
    }

    protected static String range(float min, float max) {
        if (min == Float.MAX_VALUE) return "not measured";
        return String.format("%.0f..%.0f", min, max);
    }

    /**
     * DP killed per DP lost, as text.
     * <p>
     * Returned as a string because losing nothing at all is not a number. A flawless win printed
     * {@code ratio=-1.00} from an earlier divide-by-zero sentinel, which reads as an error and,
     * worse, averages as a negative - it would have dragged the median of a test series downward for
     * the single best possible result.
     */
    protected String exchangeRatio() {
        float lost = ourStartDP - ourDP;
        float killed = enemyStartDP - enemyDP;
        if (lost > 0f) return String.format("%.2f", killed / lost);
        return killed > 0f ? "perfect" : "n/a";
    }

    /**
     * Who won, by elimination from the field.
     * <p>
     * <b>A simulator battle never formally ends</b> - there is no victory state, so a result only
     * exists when one side has no ships left on the field and none left to deploy, whether they were
     * destroyed or warped out. An earlier version counted reserves toward "hulls remaining", which
     * reported every battle in a series as INCONCLUSIVE because a side holding undeployed ships
     * never reads zero.
     */
    protected String outcome() {
        boolean weAreGone = ourGoneTicks >= ELIMINATED_TICKS;
        boolean theyAreGone = enemyGoneTicks >= ELIMINATED_TICKS;
        if (weAreGone && theyAreGone) return "MUTUAL";
        if (theyAreGone) return "WIN";
        if (weAreGone) return "LOSS";
        return "INCONCLUSIVE";
    }

    protected void line(StringBuilder sb, String key, String value) {
        sb.append(String.format("%-16s%s%n", key, value));
    }
}
