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
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.apache.log4j.Logger;

/**
 * Per-battle outcome recorder for whichever controller is running - {@link DraconisAdmiralAI}, the
 * legacy {@link CarrierDoctrineAI}, or none - so every configuration is judged on the same numbers.
 * The summary is written to the game's common folder via
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
 * {@code trackShips}, {@code tickOutcomeOnly}, the per-ship print block and the field set are as
 * they were; the surrounding scaffolding was rewritten from the behaviour it had to reproduce, so
 * some comment wording here is newer than the code it describes.
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
    protected final String label;
    protected final CombatEngineAPI engine;
    protected final String battleId;
    protected final String summaryName;
    protected boolean broken = false;

    protected int ticks = 0;
    protected float firstTime = -1f, lastTime = 0f;
    protected float ourStartDP = 0f, enemyStartDP = 0f;
    protected float ourDP = 0f, enemyDP = 0f;

    protected int ourHulls = 0, enemyHulls = 0, ourStartHulls = 0, enemyStartHulls = 0;
    protected int ourOnField = 0, enemyOnField = 0, ourReserve = 0, enemyReserve = 0;
    protected boolean ourEverOnField = false, enemyEverOnField = false;
    /** Names of our hulls still on the field, so a lingering phantom survivor can be identified
     * rather than guessed at - a count of 1 has repeatedly disagreed with what is on screen. */
    protected final List<String> survivors = new ArrayList<>();

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

    /**
     * @param label distinguishes controllers in the filename, so a like-for-like A/B series can be
     *              run without the records colliding.
     */
    public TaskForceBattleLog(int owner, String label) {
        this.owner = owner;
        this.label = label;
        this.engine = Global.getCombatEngine();
        this.battleId = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        this.summaryName = PREFIX + label + "_" + battleId + "_summary.txt";
        log.info("XLII_TF battle log: common/" + summaryName);
    }

    /**
     * Observes the fleet from outside, so it works the same for every controller and for none.
     * <p>
     * Nothing in this project measured the legacy controller or the no-orders baseline with the same
     * numbers used to judge the rewrite, so every comparison between them was an impression until
     * this existed.
     */
    public void tickOutcomeOnly(float time, CombatFleetManagerAPI ours, CombatFleetManagerAPI theirs) {
        if (broken) return;
        ticks++;
        if (firstTime < 0f) firstTime = time;
        lastTime = time;
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

            String order = ship.isRetreating() ? "RETREATING" : "NONE";
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
                        + "survivors=[%s]%n",
                label, outcome(), lastTime - firstTime, ourStartDP, ourDP, enemyStartDP, enemyDP,
                exchangeRatio(),
                ourStartHulls, ourHulls, enemyStartHulls, enemyHulls,
                ourOnField, ourReserve, enemyOnField, enemyReserve,
                String.join(", ", survivors).replace(' ', '_')));

        sb.append("XLII_TF battle ").append(battleId)
                .append("  (controller owner=").append(owner).append(")\n");
        line(sb, "duration", String.format("%.0fs (%d samples)", lastTime - firstTime, ticks));
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
        } catch (Exception e) {
            log.warn("XLII_TF battle log write failed", e);
            broken = true;
        }
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
