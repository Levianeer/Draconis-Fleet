package levianeer.draconis.data.scripts.combat.testharness;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

/**
 * Finished test-mission runs, kept in a common file shared by every save, so nobody has to flip a
 * setting between runs or count them by hand. One line per finished run, written by
 * {@link TestBattleLog} when the battle is over:
 * {@code experiment,mission,arm,winner,seconds,dealt,taken,draconisDpLost,opponentDpLost}, where
 * {@code mission} is {@code missionId@odds} so runs at different odds stay apart. A run quit early is
 * never written. Changing {@link #EXPERIMENT} starts a fresh count without deleting the old one.
 * <p>
 * The arm says what flew the Draconis side. Each run gets whichever arm in {@link #ARMS} has the fewest
 * finished runs in that mission (the first on a tie), so A/B runs alternate on their own.
 */
public final class TestRunLedger {

    private static final Logger log = Global.getLogger(TestRunLedger.class);

    /** Rename when the thing being measured changes, so older runs stop counting. */
    public static final String EXPERIMENT = "slice-step3";
    /** Vanilla AI with vanilla's admiral. Phase 0 baseline; not run any more. */
    public static final String VANILLA = "vanilla";
    /** The commander's admiral only (deploys everything, no orders or retreats); ships fly vanilla. */
    public static final String FLOOR = "floor";
    /** The floor plus whatever the commander currently adds. */
    public static final String COMMANDER = "commander";
    /** The arms the test missions alternate between. */
    static final String[] ARMS = {FLOOR, COMMANDER};
    public static final int RUNS_PER_ARM = 5;

    private static final String FILE = "draconis_commander_tests.csv";

    private TestRunLedger() {
    }

    static void record(String mission, String arm, String winner, float seconds, float dealt, float taken,
                       float draconisDpLost, float opponentDpLost) {
        String line = String.format("%s,%s,%s,%s,%.0f,%.0f,%.0f,%.0f,%.0f", EXPERIMENT, mission, arm, winner,
                seconds, dealt, taken, draconisDpLost, opponentDpLost);
        try {
            Global.getSettings().writeTextFileToCommon(FILE, read() + line + "\n");
        } catch (Exception e) {
            log.warn("Draconis: could not record test run", e);
        }
    }

    /** The arm the next run of {@code mission} gets. */
    static String nextArm(String mission) {
        int[][] done = finished(mission);
        int best = 0;
        for (int i = 1; i < ARMS.length; i++) if (done[i][0] < done[best][0]) best = i;
        return ARMS[best];
    }

    /** Briefing lines for {@code mission}: runs and Draconis wins per arm, and the next run's arm. */
    static String[] briefing(String mission) {
        int[][] done = finished(mission);
        StringBuilder progress = new StringBuilder(EXPERIMENT).append(":");
        for (int i = 0; i < ARMS.length; i++) {
            progress.append(String.format(" %s %d/%d (won %d)", ARMS[i], done[i][0], RUNS_PER_ARM, done[i][1]));
        }
        return new String[]{progress.toString(), "This run: " + nextArm(mission) + " (set automatically)"};
    }

    /** Per arm in {@link #ARMS}: {runs, Draconis wins} in the current experiment. */
    private static int[][] finished(String mission) {
        int[][] done = new int[ARMS.length][2];
        for (String line : read().split("\n")) {
            String[] f = line.trim().split(",");
            if (f.length < 4 || !f[0].equals(EXPERIMENT) || !f[1].equals(mission)) continue;
            for (int i = 0; i < ARMS.length; i++) {
                if (!ARMS[i].equals(f[2])) continue;
                done[i][0]++;
                if ("draconis".equals(f[3])) done[i][1]++;
            }
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
}
