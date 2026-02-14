package levianeer.draconis.data.campaign.econ.conditions;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.intel.invasion.InvasionIntel;
import exerelin.campaign.intel.raid.NexRaidIntel;
import levianeer.draconis.data.campaign.characters.XLII_Characters;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Core engine for the DRACON (Draconis Readiness Condition) system.
 * Runs monthly, calculates a threat score (0-100), maps it to a DRACON level (1-5),
 * and manages the DRACON condition on all DDA markets.
 * <p>
 * Threat score factors:
 *   1. Active wars:        +12 per faction at war with DDA
 * <p>
 *   2. Lost colonies:      +20 per baseline market no longer controlled
 * <p>
 *   3. Colony instability: +4 per DDA market with base stability <= threshold
 * <p>
 *   4. Fleet weakness:     +0 to +20 scaled by strongest enemy ratio
 * <p>
 *   5. Invasion pressure:  +15 if Nex invasion/raid targets a DDA market
 * <p>
 * Monthly decay: -5 (peace) or -2 (at war)
 */
public class DraconManager implements EveryFrameScript {

    private static final Logger log = Global.getLogger(DraconManager.class);

    // Sector memory keys
    public static final String SCORE_KEY = "$dracon_threatScore";
    public static final String LEVEL_KEY = "$dracon_currentLevel";
    public static final String PREVIOUS_LEVEL_KEY = "$dracon_previousLevel";
    public static final String BASELINE_KEY = "$dracon_baselineMarkets";

    private static final String CONDITION_ID = "draconis_dracon";
    private static final String STEEL_CURTAIN_ID = "draconis_steel_curtain";

    private float daysElapsed = 0f;
    private boolean initialized = false;

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        float days = Global.getSector().getClock().convertToDays(amount);
        daysElapsed += days;

        // First-run initialization
        if (!initialized) {
            initialize();
            initialized = true;
        }

        DraconConfig config = DraconConfig.getInstance();

        if (daysElapsed < config.getCheckIntervalDays()) return;
        daysElapsed = 0f;

        // Calculate and apply
        int newScore = calculateThreatScore();
        int newLevel = config.scoreToLevel(newScore);

        // Store results
        Global.getSector().getMemoryWithoutUpdate().set(SCORE_KEY, newScore);
        Global.getSector().getMemoryWithoutUpdate().set(LEVEL_KEY, newLevel);

        // Check for level transition
        Object prevObj = Global.getSector().getMemoryWithoutUpdate().get(PREVIOUS_LEVEL_KEY);
        int previousLevel = (prevObj instanceof Number) ? ((Number) prevObj).intValue() : 5;

        if (newLevel != previousLevel) {
            log.debug("Draconis: === DRACON LEVEL CHANGE ===");
            log.debug("Draconis: DRACON " + previousLevel + " (" + DraconConfig.getLevelName(previousLevel) +
                    ") -> DRACON " + newLevel + " (" + DraconConfig.getLevelName(newLevel) + ")");
            log.debug("Draconis: Threat score: " + newScore);
            Global.getSector().getMemoryWithoutUpdate().set(PREVIOUS_LEVEL_KEY, newLevel);
        }

        log.debug("Draconis: Monthly DRACON update - Score: " + newScore +
                ", Level: " + newLevel + " (" + DraconConfig.getLevelName(newLevel) + ")");

        // Manage conditions on all markets
        updateMarketConditions();

        // Update character placements
        XLII_Characters.updateCharacterPlacements();
    }

    /**
     * First-run initialization: snapshot baseline markets, migrate from Steel Curtain.
     */
    private void initialize() {
        // Check if baseline already exists (not first run)
        Object baselineObj = Global.getSector().getMemoryWithoutUpdate().get(BASELINE_KEY);
        if (baselineObj == null) {
            // First run: snapshot current DDA markets as baseline
            snapshotBaselineMarkets();

            // Set initial state
            Global.getSector().getMemoryWithoutUpdate().set(SCORE_KEY, 0);
            Global.getSector().getMemoryWithoutUpdate().set(LEVEL_KEY, 5);
            Global.getSector().getMemoryWithoutUpdate().set(PREVIOUS_LEVEL_KEY, 5);

            log.debug("Draconis: DRACON system initialized at level 5 (COLD FORGE)");
        }

        // Migrate from Steel Curtain (runs every game load for save compat)
        migrateFromSteelCurtain();

        // Ensure DRACON condition is on all DDA markets
        updateMarketConditions();
    }

    /**
     * Captures all current DDA market IDs as the baseline for lost colony detection.
     */
    private void snapshotBaselineMarkets() {
        StringBuilder sb = new StringBuilder();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (DRACONIS.equals(market.getFactionId()) && !market.isHidden()) {
                if (!sb.isEmpty()) sb.append(",");
                sb.append(market.getId());
            }
        }
        Global.getSector().getMemoryWithoutUpdate().set(BASELINE_KEY, sb.toString());
        log.debug("Draconis: Baseline markets captured: " + sb);
    }

    /**
     * Remove Steel Curtain from any market that still has it.
     */
    private void migrateFromSteelCurtain() {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.hasCondition(STEEL_CURTAIN_ID)) {
                market.removeCondition(STEEL_CURTAIN_ID);
                log.info("Draconis: Migrated Steel Curtain to DRACON on " + market.getName());
            }
        }
    }

    /**
     * Apply/remove DRACON condition based on DDA ownership.
     * Same pattern as DraconisSteelCurtainMonitor.
     */
    private void updateMarketConditions() {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market == null || market.isHidden()) continue;

            boolean isDraconisOwned = DRACONIS.equals(market.getFactionId());
            boolean hasCondition = market.hasCondition(CONDITION_ID);

            if (isDraconisOwned && !hasCondition) {
                market.addCondition(CONDITION_ID);
            } else if (!isDraconisOwned && hasCondition) {
                market.removeCondition(CONDITION_ID);
            }
        }
    }

    // =========================================================================
    // Threat Score Calculation
    // =========================================================================

    /**
     * Calculates the new threat score from all five factors plus decay.
     * Formula: newScore = clamp(rawContributions + decay, 0, 100)
     */
    private int calculateThreatScore() {
        DraconConfig config = DraconConfig.getInstance();

        // Test override
        int testOverride = config.getTestLevelOverride();
        if (testOverride >= 1 && testOverride <= 5) {
            // Return a score in the middle of the target level's range
            switch (testOverride) {
                case 5: return 10;
                case 4: return 30;
                case 3: return 50;
                case 2: return 70;
                case 1: return 90;
            }
        }

        FactionAPI draconis = Global.getSector().getFaction(DRACONIS);
        if (draconis == null) return 0;

        // Factor 1: Active wars
        int warCount = countActiveWars(draconis);
        int warScore = warCount * config.getWarScorePerWar();

        // Factor 2: Lost colonies
        int lostCount = countLostColonies();
        int lostScore = lostCount * config.getLostColonyScore();

        // Factor 3: Colony instability (excluding DRACON's own penalty)
        int unstableCount = countUnstableColonies();
        int instabilityScore = unstableCount * config.getUnstableMarketScore();

        // Factor 4: Fleet weakness
        int fleetWeaknessScore = calculateFleetWeakness(draconis, config.getMaxFleetWeaknessScore());

        // Factor 5: Invasion pressure
        int invasionScore = checkInvasionPressure() ? config.getInvasionPressureScore() : 0;

        // Raw contributions
        int rawScore = warScore + lostScore + instabilityScore + fleetWeaknessScore + invasionScore;

        // Apply decay
        boolean atWar = warCount > 0;
        int decay = atWar ? config.getWartimeDecay() : config.getPeacetimeDecay();
        int finalScore = Math.max(0, Math.min(100, rawScore + decay));

        if (log.isDebugEnabled()) {
            log.debug("Draconis: DRACON score breakdown - Wars: " + warScore +
                    " (" + warCount + "), Lost: " + lostScore + " (" + lostCount +
                    "), Unstable: " + instabilityScore + " (" + unstableCount +
                    "), Fleet: " + fleetWeaknessScore +
                    ", Invasion: " + invasionScore +
                    ", Decay: " + decay +
                    ", Final: " + finalScore);
        }

        return finalScore;
    }

    /**
     * Factor 1: Count factions currently at war with the DDA.
     */
    private int countActiveWars(FactionAPI draconis) {
        int count = 0;
        for (FactionAPI faction : Global.getSector().getAllFactions()) {
            if (faction == draconis) continue;
            if (faction.isNeutralFaction()) continue;
            // Only count actual hostile relationships (war), not just unfriendly
            if (draconis.isHostileTo(faction)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Factor 2: Count baseline markets no longer controlled by DDA.
     */
    private int countLostColonies() {
        Object baselineObj = Global.getSector().getMemoryWithoutUpdate().get(BASELINE_KEY);
        if (!(baselineObj instanceof String) || ((String) baselineObj).isEmpty()) return 0;

        String[] baselineIds = ((String) baselineObj).split(",");
        int lostCount = 0;

        for (String marketId : baselineIds) {
            String trimmed = marketId.trim();
            if (trimmed.isEmpty()) continue;

            MarketAPI market = Global.getSector().getEconomy().getMarket(trimmed);
            if (market == null || !DRACONIS.equals(market.getFactionId())) {
                lostCount++;
            }
        }
        return lostCount;
    }

    /**
     * Factor 3: Count DDA markets with base stability at or below the threshold.
     * Excludes DRACON's own stability modifier to prevent feedback loops.
     */
    private int countUnstableColonies() {
        DraconConfig config = DraconConfig.getInstance();
        Object levelObj = Global.getSector().getMemoryWithoutUpdate().get(LEVEL_KEY);
        int currentLevel = (levelObj instanceof Number) ? ((Number) levelObj).intValue() : 5;
        float draconStabilityMod = DraconConfig.getStabilityModifierForLevel(currentLevel);

        int count = 0;
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!DRACONIS.equals(market.getFactionId())) continue;
            if (market.isHidden()) continue;

            // Subtract DRACON's own stability modifier to get "base" stability
            float baseStability = market.getStabilityValue() - draconStabilityMod;
            if (baseStability <= config.getInstabilityThreshold()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Factor 4: Compare DDA fleet strength against strongest hostile faction.
     * Uses market-based fleet size as a proxy for total military power.
     * Returns 0-maxScore scaled linearly: ratio 1x=0, 2x=half, 3x+=max.
     */
    private int calculateFleetWeakness(FactionAPI draconis, int maxScore) {
        float ddaStrength = 0f;

        // Track per-faction enemy strength to find the strongest
        Map<String, Float> enemyStrength = new HashMap<>();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.isHidden()) continue;

            // Use market size as base fleet contribution
            float contribution = market.getSize();

            String factionId = market.getFactionId();
            if (DRACONIS.equals(factionId)) {
                ddaStrength += contribution;
            } else if (draconis.isHostileTo(market.getFaction())) {
                enemyStrength.merge(factionId, contribution, Float::sum);
            }
        }

        if (ddaStrength <= 0) return maxScore; // No markets = maximum vulnerability

        // Find strongest single enemy faction
        float strongestEnemy = 0f;
        for (float strength : enemyStrength.values()) {
            strongestEnemy = Math.max(strongestEnemy, strength);
        }

        if (strongestEnemy <= 0) return 0; // No enemies with markets

        float ratio = strongestEnemy / ddaStrength;
        if (ratio <= 1.0f) return 0;
        if (ratio >= 3.0f) return maxScore;

        // Linear scale: 1.0 -> 0, 2.0 -> maxScore/2, 3.0 -> maxScore
        return Math.round((ratio - 1.0f) / 2.0f * maxScore);
    }

    /**
     * Factor 5: Check for active Nexerelin invasion or raid fleets targeting DDA markets.
     * Returns true if any inbound invasion/raid is targeting a DDA market.
     */
    private boolean checkInvasionPressure() {
        try {
            for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel()) {
                if (intel instanceof InvasionIntel invasion) {
                    if (!invasion.isEnding() && !invasion.isEnded()) {
                        MarketAPI target = invasion.getTarget();
                        if (target != null && DRACONIS.equals(target.getFactionId())) {
                            return true;
                        }
                    }
                }
                if (intel instanceof NexRaidIntel raid) {
                    if (!raid.isEnding() && !raid.isEnded()) {
                        MarketAPI target = raid.getTarget();
                        if (target != null && DRACONIS.equals(target.getFactionId())) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Draconis: Error checking invasion pressure", e);
        }
        return false;
    }
}
