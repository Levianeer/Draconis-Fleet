package levianeer.draconis.data.campaign.intel.aicore.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Canonical persistent counter for AI cores acquired by Draconis but not yet installed.
 * <p>
 * All data lives in faction memory under CORE_STOCKPILE_KEY, so it persists across save/load automatically.
 * Old saves with no key are initialized to zero and accumulate going forward.
 * <p>
 * CME SAFETY: Starsector is single-threaded; the CME risk is exclusively from iterating a
 * collection while modifying it in the same call stack.
 *   - add() and consume() only call put() on the backing map — never iterate it.
 *   - getStockpileCopy() returns new HashMap<>(backing) — callers iterate the copy, not the live map.
 *   - tryInstallStockpiledCores() builds its work list from the copy, iterates local lists only,
 *     and calls consume() (put() only) on the live map mid-loop.
 */
public class DraconisAICoreStockpile {

    private static final Logger log = Global.getLogger(DraconisAICoreStockpile.class);

    /** Faction memory key for the stockpile map. Shared with DraconisAICoreDonationListener. */
    public static final String CORE_STOCKPILE_KEY = "$draconis_coreStockpile";

    private static final int MAX_INSTALL_ROUNDS = 100;

    // Static utility class — no instances
    private DraconisAICoreStockpile() {}

    // -------------------------------------------------------------------------
    // Core API
    // -------------------------------------------------------------------------

    /**
     * Adds cores to the stockpile.
     * Safe to call from any acquisition source (theft, Remnant, donation).
     * No iteration of the backing map — only calls put().
     *
     * @param coreId Commodity ID (e.g. Commodities.ALPHA_CORE)
     * @param count  Number to add. Values <= 0 are ignored.
     */
    public static void add(String coreId, int count) {
        if (count <= 0) return;
        if (coreId == null || coreId.isEmpty()) return;

        Map<String, Integer> stockpile = getOrInitStockpile();
        if (stockpile == null) return;

        int current = stockpile.getOrDefault(coreId, 0);
        stockpile.put(coreId, current + count);

        log.info("Draconis: Stockpile ADD " + count + "x " + DraconisAICorePriorityManager.getCoreDisplayName(coreId) +
                " (total: " + stockpile.get(coreId) + ")");
    }

    /**
     * Removes cores from the stockpile after a confirmed installation.
     * No iteration — only calls put().
     *
     * @param coreId Commodity ID
     * @param count  Number to consume
     * @return true if consumed successfully; false if insufficient (stockpile NOT modified).
     */
    public static boolean consume(String coreId, int count) {
        if (count <= 0) return true;
        if (coreId == null || coreId.isEmpty()) return false;

        Map<String, Integer> stockpile = getOrInitStockpile();
        if (stockpile == null) return false;

        int current = stockpile.getOrDefault(coreId, 0);
        if (current < count) {
            log.warn("Draconis: Stockpile CONSUME failed — requested " + count + "x " +
                    DraconisAICorePriorityManager.getCoreDisplayName(coreId) +
                    " but only " + current + " available");
            return false;
        }

        // Math.max guard: safety floor against negative counts in edge cases
        stockpile.put(coreId, Math.max(0, current - count));

        log.info("Draconis: Stockpile CONSUME " + count + "x " + DraconisAICorePriorityManager.getCoreDisplayName(coreId) +
                " (remaining: " + stockpile.get(coreId) + ")");
        return true;
    }

    /**
     * Returns the current count for a specific core type.
     * Reads a single key — no iteration, CME-safe.
     *
     * @param coreId Commodity ID
     * @return Count, or 0 if absent or faction unavailable.
     */
    public static int getCount(String coreId) {
        Map<String, Integer> stockpile = getOrInitStockpile();
        if (stockpile == null) return 0;
        return stockpile.getOrDefault(coreId, 0);
    }

    /**
     * Returns a defensive copy of the full stockpile for safe external iteration.
     * Modifications to the returned map do NOT affect the live stockpile.
     *
     * @return New HashMap snapshot; empty map if faction unavailable.
     */
    public static Map<String, Integer> getStockpileCopy() {
        Map<String, Integer> stockpile = getOrInitStockpile();
        if (stockpile == null) return new HashMap<>();
        return new HashMap<>(stockpile);
    }

    /**
     * Weighted score across all stockpiled cores for buff calculations.
     * Formula: (alpha * 3) + (beta * 2) + (gamma * 1)
     * Reads three specific keys — no entry-set iteration, CME-safe.
     *
     * @return Weighted score; 0 if stockpile is empty or faction unavailable.
     */
    public static int getTotalWeightedScore() {
        Map<String, Integer> stockpile = getOrInitStockpile();
        if (stockpile == null) return 0;

        int alpha = stockpile.getOrDefault(Commodities.ALPHA_CORE, 0);
        int beta  = stockpile.getOrDefault(Commodities.BETA_CORE,  0);
        int gamma = stockpile.getOrDefault(Commodities.GAMMA_CORE, 0);

        return (alpha * 3) + (beta * 2) + (gamma);
    }

    /**
     * Attempts to drain the stockpile into available Draconis industries and admin slots.
     * For each core successfully installed, calls consume() to decrement the stockpile.
     * Cores that cannot be installed (all slots full) remain in the stockpile.
     * <p>
     * Safe to call frequently — returns immediately if stockpile is empty.
     * <p>
     * Uses the round-based loop pattern from DraconisAICoreDonationListener.handleDonatedCores():
     * - Deferred removeAll() after inner loop (no CME from list modification during iteration)
     * - Displaced cores circulate in a local list; they are NOT re-added via add() (no double-count)
     * - Breaks when installedThisRound == 0 to prevent infinite loops
     * - MAX_INSTALL_ROUNDS safety ceiling
     */
    public static void tryInstallStockpiledCores() {
        // Build work list from a snapshot — never iterate the live backing map
        Map<String, Integer> snapshot = getStockpileCopy();

        List<String> coresToInstall = new ArrayList<>();
        for (int i = 0; i < snapshot.getOrDefault(Commodities.ALPHA_CORE, 0); i++) {
            coresToInstall.add(Commodities.ALPHA_CORE);
        }
        for (int i = 0; i < snapshot.getOrDefault(Commodities.BETA_CORE, 0); i++) {
            coresToInstall.add(Commodities.BETA_CORE);
        }
        for (int i = 0; i < snapshot.getOrDefault(Commodities.GAMMA_CORE, 0); i++) {
            coresToInstall.add(Commodities.GAMMA_CORE);
        }

        if (coresToInstall.isEmpty()) return;

        log.info("Draconis: Stockpile drain — attempting to install " + coresToInstall.size() + " core(s)");

        List<MarketAPI> availableAdminMarkets = findAvailableDraconisAdminMarkets();
        List<Industry>  availableIndustries   = findAvailableDraconisIndustries();
        List<Industry>  upgradeableIndustries = findUpgradeableDraconisIndustries();

        if (availableAdminMarkets.isEmpty() && availableIndustries.isEmpty() && upgradeableIndustries.isEmpty()) {
            log.info("Draconis: Stockpile drain — no available slots, stockpile unchanged");
            return;
        }

        int totalInstalled = 0;
        List<String> remainingCores = new ArrayList<>(coresToInstall);
        int round = 0;

        while (!remainingCores.isEmpty() && round < MAX_INSTALL_ROUNDS) {
            round++;
            List<String> displacedCores  = new ArrayList<>();
            List<String> failedCores     = new ArrayList<>();

            // Deferred removal — safe to call removeAll after the inner loop finishes
            Set<Industry>  industriesToRemove = new HashSet<>();
            Set<MarketAPI> marketsToRemove    = new HashSet<>();

            int installedThisRound = 0;

            for (String coreId : remainingCores) {
                boolean installed = false;

                // Alpha cores: try admin slot first (HYPERCOGNITION)
                if (coreId.equals(Commodities.ALPHA_CORE) && !availableAdminMarkets.isEmpty()) {
                    MarketAPI target = DraconisAICorePriorityManager.pickTargetAdminMarket(availableAdminMarkets);
                    if (target != null && DraconisAICorePriorityManager.installAICoreAdmin(target, coreId, DRACONIS)) {
                        if (consume(coreId, 1)) {
                            marketsToRemove.add(target);
                            totalInstalled++;
                            installedThisRound++;
                            installed = true;
                            log.info("Draconis: Stockpile installed Alpha Core as admin at " + target.getName());
                        }
                    }
                }

                // All core types: try industry slot
                if (!installed && (!availableIndustries.isEmpty() || !upgradeableIndustries.isEmpty())) {
                    Industry target = DraconisAICorePriorityManager.pickTargetIndustryByPriority(
                            availableIndustries, upgradeableIndustries, coreId);

                    if (target != null) {
                        String displacedCore = null;
                        if (target.getAICoreId() != null && !target.getAICoreId().isEmpty()) {
                            displacedCore = target.getAICoreId();
                        }

                        if (tryInstallCore(target, coreId)) {
                            if (consume(coreId, 1)) {
                                industriesToRemove.add(target);
                                totalInstalled++;
                                installedThisRound++;
                                installed = true;

                                // Displaced cores circulate within this drain pass only.
                                // They are NOT re-added via add() — they were already consumed
                                // from the stockpile in a previous cycle when first installed.
                                if (displacedCore != null) {
                                    displacedCores.add(displacedCore);
                                    log.info("Draconis: Stockpile displaced " +
                                            DraconisAICorePriorityManager.getCoreDisplayName(displacedCore) +
                                            " from " + target.getCurrentName() + " — re-queuing for redistribution");
                                }
                            }
                        }
                    }
                }

                if (!installed) {
                    failedCores.add(coreId);
                }
            }

            // Safe deferred removal — inner loop is finished
            availableAdminMarkets.removeAll(marketsToRemove);
            availableIndustries.removeAll(industriesToRemove);
            upgradeableIndustries.removeAll(industriesToRemove);

            // Next round: only displaced cores from this round
            remainingCores.clear();
            remainingCores.addAll(displacedCores);

            if (installedThisRound == 0 && !failedCores.isEmpty()) {
                log.info("Draconis: Stockpile drain stalled — " + failedCores.size() +
                        " core(s) remain in stockpile pending available slot");
                break;
            }
        }

        if (round >= MAX_INSTALL_ROUNDS) {
            log.error("Draconis: Stockpile drain exceeded MAX_INSTALL_ROUNDS — possible logic error");
        }

        log.info("Draconis: Stockpile drain complete — installed " + totalInstalled +
                " core(s) in " + round + " round(s). Remaining score: " + getTotalWeightedScore());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Gets or initializes the live backing map from faction memory.
     * NEVER iterate the returned map directly — use getStockpileCopy() for iteration.
     *
     * @return Live backing map, or null if the Draconis faction is unavailable.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Integer> getOrInitStockpile() {
        FactionAPI faction = Global.getSector().getFaction(DRACONIS);
        if (faction == null) {
            log.error("Draconis: DraconisAICoreStockpile — Draconis faction not found");
            return null;
        }

        if (faction.getMemoryWithoutUpdate().contains(CORE_STOCKPILE_KEY)) {
            Map<String, Integer> existing = (Map<String, Integer>)
                    faction.getMemoryWithoutUpdate().get(CORE_STOCKPILE_KEY);
            if (existing != null) return existing;
        }

        // Initialize fresh — save-compatible: old saves start at zero and accumulate forward
        Map<String, Integer> stockpile = new HashMap<>();
        stockpile.put(Commodities.ALPHA_CORE, 0);
        stockpile.put(Commodities.BETA_CORE,  0);
        stockpile.put(Commodities.GAMMA_CORE, 0);
        faction.getMemoryWithoutUpdate().set(CORE_STOCKPILE_KEY, stockpile);

        log.info("Draconis: DraconisAICoreStockpile initialized (all counts at zero)");
        return stockpile;
    }

    private static boolean tryInstallCore(Industry industry, String coreId) {
        String oldCore = industry.getAICoreId();
        industry.setAICoreId(coreId);

        if (coreId.equals(industry.getAICoreId())) {
            log.info("Draconis: Stockpile INSTALLED " + DraconisAICorePriorityManager.getCoreDisplayName(coreId) +
                    " on " + industry.getCurrentName() + " at " + industry.getMarket().getName());
            return true;
        }

        industry.setAICoreId(oldCore);
        log.error("Draconis: Stockpile FAILED to install " + DraconisAICorePriorityManager.getCoreDisplayName(coreId) +
                " on " + industry.getCurrentName());
        return false;
    }

    private static List<MarketAPI> findAvailableDraconisAdminMarkets() {
        List<MarketAPI> available = new ArrayList<>();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.getFactionId().equals(DRACONIS)) continue;
            if (market.isHidden()) continue;
            if ("kori_market".equals(market.getId())) continue;
            if (market.getAdmin() != null &&
                    market.getAdmin().getStats().getSkillLevel(Skills.HYPERCOGNITION) > 0) continue;
            available.add(market);
        }
        return available;
    }

    private static List<Industry> findAvailableDraconisIndustries() {
        List<Industry> available = new ArrayList<>();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.getFactionId().equals(DRACONIS)) continue;
            if (market.isHidden()) continue;
            for (Industry industry : new ArrayList<>(market.getIndustries())) {
                if (industry.getAICoreId() != null && !industry.getAICoreId().isEmpty()) continue;
                if (!industry.isFunctional()) continue;
                available.add(industry);
            }
        }
        return available;
    }

    private static List<Industry> findUpgradeableDraconisIndustries() {
        List<Industry> upgradeable = new ArrayList<>();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.getFactionId().equals(DRACONIS)) continue;
            if (market.isHidden()) continue;
            for (Industry industry : new ArrayList<>(market.getIndustries())) {
                String currentCore = industry.getAICoreId();
                if (currentCore == null || currentCore.isEmpty()) continue;
                if (currentCore.equals(Commodities.ALPHA_CORE)) continue;
                if (!industry.isFunctional()) continue;
                upgradeable.add(industry);
            }
        }
        return upgradeable;
    }
}
