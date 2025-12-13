package levianeer.draconis.data.campaign.fleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration manager for the Draconis AI Core Fleet Scaling system.
 * Handles loading settings and calculating coverage/distribution based on game cycles.
 */
public class DraconisAICoreScalingConfig {
    private static final Logger log = Global.getLogger(DraconisAICoreScalingConfig.class);

    // System settings
    private boolean enabled = true;
    private float testCycleOverride = -1f; // For testing: override actual game cycle (-1 = disabled)
    private float recheckIntervalDays = 120f; // How often to recheck fleets for new empty slots

    // Cycle thresholds for scaling curve
    // NOTE: These are absolute cycle numbers (campaigns start at ~206)
    private float earlyGameEnd = 211f;      // No AI cores before this (206+5)
    private float midGameStart = 211f;      // Start adding AI cores (206+5)
    private float midGameEnd = 226f;        // Increasing AI core usage (206+20)
    private float lateGameStart = 226f;     // More AI cores (206+20)
    private float lateGameEnd = 256f;       // Heavy AI core usage (206+50)
    private float endGameStart = 256f;      // Very heavy usage (206+50)
    private float endGameEnd = 306f;        // Maximum saturation (206+100)

    // Coverage percentages (how many empty slots to fill)
    private float coverageEarlyGame = 0f;     // 0% at cycle 1-5
    private float coverageMidGame = 0.30f;    // 30% at cycle 20
    private float coverageLateGame = 0.70f;   // 70% at cycle 50
    private float coverageEndGame = 1.0f;     // 100% at cycle 100+

    // Core type distribution weights
    private final Map<String, Float> gammaWeights = new HashMap<>();
    private final Map<String, Float> betaWeights = new HashMap<>();
    private final Map<String, Float> alphaWeights = new HashMap<>();

    private static DraconisAICoreScalingConfig instance;

    public static DraconisAICoreScalingConfig getInstance() {
        if (instance == null) {
            instance = new DraconisAICoreScalingConfig();
            instance.loadSettings();
        }
        return instance;
    }

    private void loadSettings() {
        try {
            JSONObject settings = Global.getSettings().getJSONObject("draconisAICoreScaling");

            enabled = settings.optBoolean("enabled", true);
            testCycleOverride = (float) settings.optDouble("testCycleOverride", -1.0);
            recheckIntervalDays = (float) settings.optDouble("recheckIntervalDays", 120.0);

            // Load cycle thresholds
            JSONObject thresholds = settings.optJSONObject("cycleThresholds");
            if (thresholds != null) {
                earlyGameEnd = (float) thresholds.optDouble("earlyGameEnd", 211.0);
                midGameStart = (float) thresholds.optDouble("midGameStart", 211.0);
                midGameEnd = (float) thresholds.optDouble("midGameEnd", 226.0);
                lateGameStart = (float) thresholds.optDouble("lateGameStart", 226.0);
                lateGameEnd = (float) thresholds.optDouble("lateGameEnd", 256.0);
                endGameStart = (float) thresholds.optDouble("endGameStart", 256.0);
                endGameEnd = (float) thresholds.optDouble("endGameEnd", 306.0);
            }

            // Load coverage percentages
            JSONObject coverage = settings.optJSONObject("coveragePercentages");
            if (coverage != null) {
                coverageEarlyGame = (float) coverage.optDouble("earlyGame", 0.0);
                coverageMidGame = (float) coverage.optDouble("midGame", 0.25);
                coverageLateGame = (float) coverage.optDouble("lateGame", 0.75);
                coverageEndGame = (float) coverage.optDouble("endGame", 1.0);
            }

            // Load core type weights
            JSONObject coreWeights = settings.optJSONObject("coreTypeWeights");
            if (coreWeights != null) {
                loadCoreWeights(coreWeights, "earlyMidGame", gammaWeights, 1.0f, 0.0f, 0.0f);
                loadCoreWeights(coreWeights, "midGame", gammaWeights, 1.0f, 0.0f, 0.0f);
                loadCoreWeights(coreWeights, "midLateGame", gammaWeights, 0.9f, 0.1f, 0.0f);
                loadCoreWeights(coreWeights, "lateGame", gammaWeights, 0.5f, 0.4f, 0.1f);
                loadCoreWeights(coreWeights, "endGame", gammaWeights, 0.5f, 0.35f, 0.15f);
            }

            log.info("Draconis: AI Core Scaling config loaded successfully - Enabled: " + enabled);

            // Log key configuration values for diagnostics
            if (enabled) {
                log.info(String.format("Draconis:   Cycle thresholds: Early(%.1f-%.1f) Mid(%.1f-%.1f) Late(%.1f-%.1f) End(%.1f-%.1f)",
                    earlyGameEnd, midGameStart, midGameStart, midGameEnd,
                    lateGameStart, lateGameEnd, endGameStart, endGameEnd));
                log.info(String.format("Draconis:   Coverage: Early=%.0f%% Mid=%.0f%% Late=%.0f%% End=%.0f%%",
                    coverageEarlyGame * 100, coverageMidGame * 100,
                    coverageLateGame * 100, coverageEndGame * 100));

                if (testCycleOverride >= 0) {
                    log.warn("Draconis:   *** TEST MODE: Cycle override active - simulating cycle " + testCycleOverride + " ***");
                }
            }

        } catch (Exception e) {
            log.error("Draconis: Failed to load AI Core Scaling settings from JSON - SYSTEM DISABLED", e);
            enabled = false; // Disable system instead of using unreliable defaults
        }
    }

    private void loadCoreWeights(JSONObject coreWeights, String period,
                                 Map<String, Float> gammaMap, float defaultGamma,
                                 float defaultBeta, float defaultAlpha) {
        JSONObject periodWeights = coreWeights.optJSONObject(period);
        if (periodWeights != null) {
            gammaMap.put(period, (float) periodWeights.optDouble("gamma", defaultGamma));
            betaWeights.put(period, (float) periodWeights.optDouble("beta", defaultBeta));
            alphaWeights.put(period, (float) periodWeights.optDouble("alpha", defaultAlpha));
        } else {
            gammaMap.put(period, defaultGamma);
            betaWeights.put(period, defaultBeta);
            alphaWeights.put(period, defaultAlpha);
        }
    }

    /**
     * Calculate what percentage of empty officer slots should be filled with AI cores
     * based on the current game cycle.
     */
    public float calculateCoveragePercent(float currentCycle) {
        if (currentCycle <= earlyGameEnd) {
            // Early game: 0% coverage
            return coverageEarlyGame;
        } else if (currentCycle <= midGameEnd) {
            // Early-mid game: 0% to 30% (linear interpolation)
            return lerp(coverageEarlyGame, coverageMidGame,
                       (currentCycle - midGameStart) / (midGameEnd - midGameStart));
        } else if (currentCycle <= lateGameEnd) {
            // Mid-late game: 30% to 70% (linear interpolation)
            return lerp(coverageMidGame, coverageLateGame,
                       (currentCycle - lateGameStart) / (lateGameEnd - lateGameStart));
        } else if (currentCycle <= endGameEnd) {
            // Late-end game: 70% to 100% (linear interpolation)
            return lerp(coverageLateGame, coverageEndGame,
                       (currentCycle - endGameStart) / (endGameEnd - endGameStart));
        } else {
            // Beyond end game: 100% coverage
            return coverageEndGame;
        }
    }

    /**
     * Get the AI core type to assign based on current game cycle and random roll.
     * Returns one of: Commodities.GAMMA_CORE, BETA_CORE, or ALPHA_CORE
     */
    public String rollCoreType(float currentCycle, float random) {
        float gammaWeight, betaWeight, alphaWeight;

        if (currentCycle <= earlyGameEnd) {
            // Should not happen (0% coverage), but default to gamma
            gammaWeight = 1.0f;
            betaWeight = 0.0f;
            alphaWeight = 0.0f;
        } else if (currentCycle <= midGameEnd) {
            // Early-mid game: interpolate between earlyMidGame and midGame weights
            float t = (currentCycle - midGameStart) / (midGameEnd - midGameStart);
            gammaWeight = lerp(gammaWeights.get("earlyMidGame"), gammaWeights.get("midGame"), t);
            betaWeight = lerp(betaWeights.get("earlyMidGame"), betaWeights.get("midGame"), t);
            alphaWeight = lerp(alphaWeights.get("earlyMidGame"), alphaWeights.get("midGame"), t);
        } else if (currentCycle <= lateGameEnd) {
            // Mid-late game: interpolate between midGame and lateGame weights
            float t = (currentCycle - lateGameStart) / (lateGameEnd - lateGameStart);
            gammaWeight = lerp(gammaWeights.get("midGame"), gammaWeights.get("lateGame"), t);
            betaWeight = lerp(betaWeights.get("midGame"), betaWeights.get("lateGame"), t);
            alphaWeight = lerp(alphaWeights.get("midGame"), alphaWeights.get("lateGame"), t);
        } else if (currentCycle <= endGameEnd) {
            // Late-end game: interpolate between lateGame and endGame weights
            float t = (currentCycle - endGameStart) / (endGameEnd - endGameStart);
            gammaWeight = lerp(gammaWeights.get("lateGame"), gammaWeights.get("endGame"), t);
            betaWeight = lerp(betaWeights.get("lateGame"), betaWeights.get("endGame"), t);
            alphaWeight = lerp(alphaWeights.get("lateGame"), alphaWeights.get("endGame"), t);
        } else {
            // End game: use final weights
            gammaWeight = gammaWeights.get("endGame");
            betaWeight = betaWeights.get("endGame");
            alphaWeight = alphaWeights.get("endGame");
        }

        // Normalize weights to sum to 1.0
        float total = gammaWeight + betaWeight + alphaWeight;
        if (total > 0) {
            gammaWeight /= total;
            betaWeight /= total;
            alphaWeight /= total;
        }

        // Roll for core type using weighted random
        if (random < gammaWeight) {
            return Commodities.GAMMA_CORE;
        } else if (random < gammaWeight + betaWeight) {
            return Commodities.BETA_CORE;
        } else {
            return Commodities.ALPHA_CORE;
        }
    }

    /**
     * Linear interpolation between two values
     */
    private float lerp(float a, float b, float t) {
        return a + (b - a) * Math.max(0, Math.min(1, t));
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Get the effective cycle to use for calculations
     * If testCycleOverride is set (>= 0), returns that instead of the actual cycle
     * For testing different game stages without waiting
     */
    public float getEffectiveCycle(float actualCycle) {
        if (testCycleOverride >= 0) {
            return testCycleOverride;
        }
        return actualCycle;
    }

    /**
     * Check if test mode is active (cycle override enabled)
     * When in test mode, fleets can be re-processed to see effects at different cycles
     */
    public boolean isTestModeActive() {
        return testCycleOverride >= 0;
    }

    /**
     * Get the interval (in days) between fleet rechecks
     */
    public float getRecheckIntervalDays() {
        return recheckIntervalDays;
    }
}