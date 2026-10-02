package levianeer.draconis.data.campaign.econ.conditions.longsight;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Monitors all markets and dynamically applies/removes the Longsight Optimization condition
 * based on Draconis ownership, gated on the Pristine Nanoforge being installed on Kori
 * ({@code $XLII_nanoforgeQuestComplete}) - same ownership-sync shape as
 * {@link levianeer.draconis.data.campaign.econ.conditions.DraconisSteelCurtainMonitor}.
 * <p>
 * Separately, every {@link #OPTIMIZATION_INTERVAL_DAYS} days, picks {@link #OPTIMIZATION_SHARE}
 * of the markets currently carrying the condition at random and improves one random eligible
 * industry on each - Longsight working overtime optimizing the colonies under its management now
 * that it's repaired and running again.
 */
public class LongsightOptimizationMonitor implements EveryFrameScript {

    private static final Logger log = Global.getLogger(LongsightOptimizationMonitor.class);

    public static final String CONDITION_ID = "draconis_longsight_optimization";
    public static final String NANOFORGE_INSTALLED_FLAG = "$XLII_nanoforgeQuestComplete";

    private static final float SYNC_INTERVAL_DAYS = 1f;
    private static final float OPTIMIZATION_INTERVAL_DAYS = 30f;
    private static final float OPTIMIZATION_SHARE = 0.2f;

    private final Random random = new Random();

    private float daysSinceSync = 0f;
    private float daysSinceOptimization = 0f;

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
        daysSinceSync += days;
        daysSinceOptimization += days;

        if (daysSinceSync >= SYNC_INTERVAL_DAYS) {
            daysSinceSync = 0f;
            syncConditions();
        }

        if (daysSinceOptimization >= OPTIMIZATION_INTERVAL_DAYS) {
            daysSinceOptimization = 0f;
            runMonthlyOptimization();
        }
    }

    private boolean isNanoforgeInstalled() {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(NANOFORGE_INSTALLED_FLAG);
    }

    private void syncConditions() {
        boolean nanoforgeInstalled = isNanoforgeInstalled();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market == null || market.isHidden()) continue;

            boolean shouldHave = nanoforgeInstalled && DRACONIS.equals(market.getFactionId());
            boolean hasCondition = market.hasCondition(CONDITION_ID);

            if (shouldHave && !hasCondition) {
                market.addCondition(CONDITION_ID);
                log.info("Draconis: Added Longsight Optimization to " + market.getName());
            } else if (!shouldHave && hasCondition) {
                market.removeCondition(CONDITION_ID);
                log.info("Draconis: Removed Longsight Optimization from " + market.getName());
            }
        }
    }

    private void runMonthlyOptimization() {
        List<MarketAPI> eligible = new ArrayList<>();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market != null && !market.isHidden() && market.hasCondition(CONDITION_ID)) {
                eligible.add(market);
            }
        }
        if (eligible.isEmpty()) return;

        int pickCount = Math.max(1, Math.round(eligible.size() * OPTIMIZATION_SHARE));
        Collections.shuffle(eligible, random);

        int improved = 0;
        for (MarketAPI market : eligible) {
            if (improved >= pickCount) break;

            Industry industry = pickImprovableIndustry(market);
            if (industry == null) continue;

            industry.setImproved(true);
            improved++;
            log.info("Draconis: Longsight Optimization improved " + industry.getCurrentName()
                    + " at " + market.getName());
        }
    }

    /** Null if the market has nothing left for Longsight to improve - already-improved and
     *  non-improvable industries (e.g. Population & Infrastructure) are both excluded by
     *  {@code canImprove()}/{@code isImproved()}, so eligible markets naturally dry up over
     *  time instead of being re-picked forever. */
    private Industry pickImprovableIndustry(MarketAPI market) {
        List<Industry> candidates = new ArrayList<>();
        for (Industry industry : market.getIndustries()) {
            if (industry.canImprove() && !industry.isImproved()) {
                candidates.add(industry);
            }
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(random.nextInt(candidates.size()));
    }
}
