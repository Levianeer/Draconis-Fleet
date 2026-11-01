package levianeer.draconis.data.scripts.combat.testharness;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

/**
 * Finished test-mission runs, kept in a common file shared by every save, so nobody has to count
 * runs by hand. One line per finished run, written by {@link TestBattleLog} when the battle is over:
 * {@code experiment,mission,arm,winner,seconds,dealt,taken,draconisDpLost,opponentDpLost}, where
 * {@code mission} is {@code missionId@odds} so runs at different odds stay apart. A run quit
 * early is never written. Changing {@link #EXPERIMENT} starts a fresh count without deleting the old
 * one.
 * <p>
 * Phase 0 only measures vanilla, so every run is the {@link #ARM} arm. When the commander exists,
 * the arm alternates between it and vanilla here.
 */
public final class TestRunLedger {

    private static final Logger log = Global.getLogger(TestRunLedger.class);

    /** Rename when the thing being measured changes, so older runs stop counting. */
    public static final String EXPERIMENT = "baseline-v1";
    public static final String ARM = "vanilla";
    public static final int RUNS_PER_ARM = 5;

    private static final String FILE = "draconis_commander_tests.csv";

    private TestRunLedger() {
    }

    static void record(String mission, String winner, float seconds, float dealt, float taken,
                       float draconisDpLost, float opponentDpLost) {
        String line = String.format("%s,%s,%s,%s,%.0f,%.0f,%.0f,%.0f,%.0f", EXPERIMENT, mission, ARM, winner,
                seconds, dealt, taken, draconisDpLost, opponentDpLost);
        try {
            Global.getSettings().writeTextFileToCommon(FILE, read() + line + "\n");
        } catch (Exception e) {
            log.warn("Draconis: could not record test run", e);
        }
    }

    /** Briefing lines for {@code mission}: runs and Draconis wins so far in the current experiment. */
    static String[] briefing(String mission) {
        int runs = 0;
        int wins = 0;
        for (String line : read().split("\n")) {
            String[] f = line.trim().split(",");
            if (f.length < 4 || !f[0].equals(EXPERIMENT) || !f[1].equals(mission) || !f[2].equals(ARM)) continue;
            runs++;
            if ("draconis".equals(f[3])) wins++;
        }
        String done = runs >= RUNS_PER_ARM ? " - complete, further runs still count" : "";
        return new String[]{String.format("%s, %s AI: %d/%d runs, Draconis won %d%s", EXPERIMENT, ARM, runs,
                RUNS_PER_ARM, wins, done)};
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
