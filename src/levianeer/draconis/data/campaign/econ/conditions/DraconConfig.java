package levianeer.draconis.data.campaign.econ.conditions;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONObject;

/**
 * Singleton configuration loader for the DRACON (Draconis Readiness Condition) system.
 * Loads tuning values from settings.json under the "draconisReadinessCondition" key.
 */
public class DraconConfig {

    private static final Logger log = Global.getLogger(DraconConfig.class);
    private static DraconConfig instance;

    // System toggles
    private boolean enabled = true;
    private int testLevelOverride = -1;

    // Factor weights
    private int warScorePerWar = 12;
    private int lostColonyScore = 20;
    private int unstableMarketScore = 4;
    private int maxFleetWeaknessScore = 20;
    private int invasionPressureScore = 15;

    // Decay rates
    private int peacetimeDecay = -5;
    private int wartimeDecay = -2;

    // Level thresholds (score >= threshold = that level)
    private int level4Min = 30;
    private int level3Min = 50;
    private int level2Min = 70;
    private int level1Min = 90;

    // Other settings
    private int instabilityThreshold = 4;
    private float checkIntervalDays = 30f;

    public static DraconConfig getInstance() {
        if (instance == null) {
            instance = new DraconConfig();
            instance.loadSettings();
        }
        return instance;
    }

    private void loadSettings() {
        try {
            JSONObject config = Global.getSettings().getJSONObject("draconisReadinessCondition");

            enabled = config.optBoolean("enabled", true);
            testLevelOverride = config.optInt("testLevelOverride", -1);

            // Factor weights
            JSONObject factors = config.optJSONObject("factorWeights");
            if (factors != null) {
                warScorePerWar = factors.optInt("warScorePerWar", 12);
                lostColonyScore = factors.optInt("lostColonyScore", 20);
                unstableMarketScore = factors.optInt("unstableMarketScore", 4);
                maxFleetWeaknessScore = factors.optInt("maxFleetWeaknessScore", 20);
                invasionPressureScore = factors.optInt("invasionPressureScore", 15);
            }

            // Decay rates
            JSONObject decay = config.optJSONObject("decayRates");
            if (decay != null) {
                peacetimeDecay = decay.optInt("peacetimeDecay", -5);
                wartimeDecay = decay.optInt("wartimeDecay", -2);
            }

            // Level thresholds
            JSONObject thresholds = config.optJSONObject("thresholds");
            if (thresholds != null) {
                level4Min = thresholds.optInt("level4Min", 20);
                level3Min = thresholds.optInt("level3Min", 40);
                level2Min = thresholds.optInt("level2Min", 60);
                level1Min = thresholds.optInt("level1Min", 80);
            }

            instabilityThreshold = config.optInt("instabilityThreshold", 4);
            checkIntervalDays = (float) config.optDouble("checkIntervalDays", 30.0);

            log.info("Draconis: DRACON config loaded successfully");
            log.info("Draconis:   Enabled: " + enabled);
            if (testLevelOverride >= 1 && testLevelOverride <= 5) {
                log.info("Draconis:   TEST OVERRIDE: Forcing DRACON level " + testLevelOverride);
            }
            log.info("Draconis:   Check interval: " + checkIntervalDays + " days");
            log.info("Draconis:   Thresholds: L4=" + level4Min + ", L3=" + level3Min +
                    ", L2=" + level2Min + ", L1=" + level1Min);

        } catch (Exception e) {
            log.error("Draconis: Failed to load DRACON config, using defaults", e);
        }
    }

    // Go Getters
    public boolean isEnabled() { return enabled; }
    public int getTestLevelOverride() { return testLevelOverride; }

    public int getWarScorePerWar() { return warScorePerWar; }
    public int getLostColonyScore() { return lostColonyScore; }
    public int getUnstableMarketScore() { return unstableMarketScore; }
    public int getMaxFleetWeaknessScore() { return maxFleetWeaknessScore; }
    public int getInvasionPressureScore() { return invasionPressureScore; }

    public int getPeacetimeDecay() { return peacetimeDecay; }
    public int getWartimeDecay() { return wartimeDecay; }

    public int getLevel4Min() { return level4Min; }
    public int getLevel3Min() { return level3Min; }
    public int getLevel2Min() { return level2Min; }
    public int getLevel1Min() { return level1Min; }

    public int getInstabilityThreshold() { return instabilityThreshold; }
    public float getCheckIntervalDays() { return checkIntervalDays; }

    /**
     * Maps a threat score (0-100) to a DRACON level (1-5).
     * Lower level = higher threat.
     */
    public int scoreToLevel(int score) {
        if (score >= level1Min) return 1;
        if (score >= level2Min) return 2;
        if (score >= level3Min) return 3;
        if (score >= level4Min) return 4;
        return 5;
    }

    /**
     * Returns the stability modifier applied by DRACON at a given level.
     * Used to exclude DRACON's own penalty from the instability factor calculation.
     */
    public static float getStabilityModifierForLevel(int level) {
        return switch (level) {
            case 5 -> 1f;
            case 3 -> -1f;
            case 2 -> -2f;
            case 1 -> -3f;
            default -> 0f;
        };
    }

    /**
     * Returns the human-readable name for a DRACON level.
     */
    public static String getLevelName(int level) {
        return switch (level) {
            case 5 -> "COLD FORGE";
            case 4 -> "LONG WATCH";
            case 3 -> "DRAWN SWORD";
            case 2 -> "BARE STEEL";
            case 1 -> "DEAD LIGHT";
            default -> "";
        };
    }
}
