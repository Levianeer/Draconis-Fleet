package levianeer.draconis.data.scripts.combat.carrierdoctrine;

import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import org.apache.log4j.Logger;

/**
 * A/B bookkeeping for the admiral test missions ({@code data/missions/xlii_test_*}), so nobody has
 * to flip a setting between runs or keep count by hand. In a test mission the {@link #SETTING} under
 * test is decided here instead of by the player's settings: each run gets whichever arm has fewer
 * finished runs in that mission, on first. A run counts once the battle is over; one quit early is
 * not recorded and its arm comes up again.
 * <p>
 * Finished runs are kept in a common file, shared by every save. Lines are
 * {@code experiment,mission,arm}; changing {@link #EXPERIMENT} starts a fresh count without
 * deleting the old one.
 */
public final class TestRunLedger {

    private static final Logger log = Global.getLogger(TestRunLedger.class);

    /** The setting the test missions alternate. */
    public static final String SETTING = "draconisAdmiralActs";
    /** Rename whenever the lever changes, so runs of an older version stop counting. */
    public static final String EXPERIMENT = "admiral-vs-vanilla-v1";
    public static final int RUNS_PER_ARM = 3;

    private static final String FILE = "draconis_admiral_tests.csv";
    private static final String MISSION_PREFIX = "xlii_test_";
    private static final String ARM_KEY = "draconis_testRunArm";
    private static final String RECORDED_KEY = "draconis_testRunRecorded";

    private TestRunLedger() {
    }

    /**
     * The value {@code settingId} takes in this battle if it is the one under test in a test mission,
     * else null. Fixed for the battle at the first call.
     */
    public static Boolean forced(String settingId) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (!SETTING.equals(settingId) || !isTestMission(engine)) return null;
        Map<String, Object> data = engine.getCustomData();
        Boolean arm = (Boolean) data.get(ARM_KEY);
        if (arm == null) {
            String mission = engine.getMissionId();
            int[] done = finished(mission);
            arm = nextArm(done);
            data.put(ARM_KEY, arm);
            log.info(String.format("DraconisAdmiral,testRun,phase=start,experiment=%s,mission=%s,arm=%s,run=%d",
                    EXPERIMENT, mission, arm ? "on" : "off", (arm ? done[1] : done[0]) + 1));
        }
        return arm;
    }

    /** Every frame. Records the run once its battle is over. */
    public static void recordIfOver() {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || !engine.isCombatOver()) return;
        Map<String, Object> data = engine.getCustomData();
        Boolean arm = (Boolean) data.get(ARM_KEY);
        if (arm == null || data.containsKey(RECORDED_KEY)) return;
        data.put(RECORDED_KEY, true);
        String mission = engine.getMissionId();
        try {
            String old = read();
            Global.getSettings().writeTextFileToCommon(FILE,
                    old + EXPERIMENT + "," + mission + "," + (arm ? "on" : "off") + "\n");
        } catch (Exception e) {
            log.warn("Draconis: could not record test run", e);
        }
        log.info(String.format("DraconisAdmiral,testRun,phase=end,experiment=%s,mission=%s,arm=%s,winner=%d",
                EXPERIMENT, mission, arm ? "on" : "off", engine.getWinningSideId()));
    }

    /** Briefing lines for {@code mission}: progress and the arm the next run gets. */
    public static String[] briefing(String mission) {
        int[] done = finished(mission);
        String progress = String.format("A/B %s (%s): on %d/%d, off %d/%d", EXPERIMENT, SETTING,
                done[1], RUNS_PER_ARM, done[0], RUNS_PER_ARM);
        String next = done[0] >= RUNS_PER_ARM && done[1] >= RUNS_PER_ARM
                ? "Both arms complete - further runs keep alternating"
                : "This run: " + SETTING + " " + (nextArm(done) ? "ON" : "OFF") + " (set automatically)";
        return new String[]{progress, next};
    }

    private static boolean nextArm(int[] done) {
        return done[1] <= done[0];
    }

    /** Finished runs of the current experiment in {@code mission}: [off, on]. */
    private static int[] finished(String mission) {
        int[] done = new int[2];
        for (String line : read().split("\n")) {
            String[] f = line.trim().split(",");
            if (f.length < 3 || !f[0].equals(EXPERIMENT) || !f[1].equals(mission)) continue;
            done["on".equals(f[2]) ? 1 : 0]++;
        }
        return done;
    }

    private static String read() {
        try {
            String text = Global.getSettings().readTextFileFromCommon(FILE);
            return text == null ? "" : text;
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean isTestMission(CombatEngineAPI engine) {
        return engine != null && engine.isMission() && engine.getMissionId() != null
                && engine.getMissionId().startsWith(MISSION_PREFIX);
    }
}
