package levianeer.draconis.data.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.util.IntervalUtil;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * EveryFrameScript that monitors the player's Draconis Alliance reputation.
 * When the player turns hostile (rep <= -0.5) and Sigma Octantis is still present
 * in the fleet or storage, it fires an interrupting transmission confrontation.
 * <p>
 * Registered in XLII_ModPlugin.onGameLoad() only when the confrontation flag is unset.
 * Self-removes once the confrontation fires (flag is set).
 * <p>
 * Performance optimizations:
 * - Fleet checks (cheap) run every 5 seconds
 * - Market storage checks (expensive) run every 30 seconds
 * - Only scans player-owned or player-accessible markets (cached every 60s)
 * - Early reputation check prevents any work when player is friendly
 */
public class XLII_SigmaOctantisWatchdog implements EveryFrameScript {

    private static final Logger log = Global.getLogger(XLII_SigmaOctantisWatchdog.class);

    public static final String CONFRONTATION_FLAG = "$global.XLII_sigma_octantis_confrontation_done";
    private static final String DRACONIS_FACTION_ID = "XLII_draconis";
    private static final float HOSTILE_THRESHOLD = -0.5f;

    // Separate intervals for cheap vs expensive checks
    private final IntervalUtil fleetCheckInterval = new IntervalUtil(5f, 5f);
    private final IntervalUtil marketCheckInterval = new IntervalUtil(30f, 30f);
    private final IntervalUtil marketCacheRefreshInterval = new IntervalUtil(60f, 60f);

    // Cached list of markets to scan (only player-relevant ones)
    private final List<String> cachedMarketIds = new ArrayList<>();

    private boolean done = false;

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) return;

        // Check if confrontation was already triggered (e.g., by another path)
        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(CONFRONTATION_FLAG)) {
            done = true;
            return;
        }

        // Early return: check faction rep before doing any expensive work
        FactionAPI draconis = Global.getSector().getFaction(DRACONIS_FACTION_ID);
        if (draconis == null) return;

        float rel = draconis.getRelationship(Factions.PLAYER);
        if (rel > HOSTILE_THRESHOLD) return;

        // Refresh market cache periodically (only when hostile)
        marketCacheRefreshInterval.advance(amount);
        if (marketCacheRefreshInterval.intervalElapsed()) {
            refreshRelevantMarkets();
        }

        // Fleet checks (cheap: officers + cargo) - run every 5 seconds
        fleetCheckInterval.advance(amount);
        if (fleetCheckInterval.intervalElapsed()) {
            if (isSigmaOctantisInFleet()) {
                fireConfrontation(rel);
                return;
            }
        }

        // Market storage checks (expensive) - run every 30 seconds
        marketCheckInterval.advance(amount);
        if (marketCheckInterval.intervalElapsed()) {
            if (isSigmaOctantisInMarketStorage()) {
                fireConfrontation(rel);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Presence checks - split into fleet (cheap) and storage (expensive)
    // -------------------------------------------------------------------------

    /**
     * Checks fleet officers (assigned + unassigned) and fleet cargo.
     * This is cheap and can run frequently.
     */
    private static boolean isSigmaOctantisInFleet() {
        final String CORE_ID = XLII_SigmaOctantisOfficerPlugin.CORE_ID;
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        // 1. Check ship captains (AI core installed directly on a ship)
        for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy()) {
            PersonAPI captain = member.getCaptain();
            if (captain != null && CORE_ID.equals(captain.getAICoreId())) return true;
        }

        // 2. Check fleet officer roster (unassigned)
        for (OfficerDataAPI officerData : playerFleet.getFleetData().getOfficersCopy()) {
            if (CORE_ID.equals(officerData.getPerson().getAICoreId())) return true;
        }

        // 3. Check fleet cargo
        if (playerFleet.getCargo().getCommodityQuantity(CORE_ID) > 0f) return true;

        return false;
    }

    /**
     * Checks market storage submarkets. Only scans cached relevant markets
     * (player-owned or player-accessible) instead of all 50-200+ markets.
     * This is expensive and should run less frequently.
     */
    private boolean isSigmaOctantisInMarketStorage() {
        final String CORE_ID = XLII_SigmaOctantisOfficerPlugin.CORE_ID;

        // Only scan markets in our cached list
        for (String marketId : cachedMarketIds) {
            MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
            if (market == null) continue;

            SubmarketAPI storage = market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
            if (storage == null || storage.getCargo() == null) continue;

            if (storage.getCargo().getCommodityQuantity(CORE_ID) > 0f) {
                return true;
            }
        }

        return false;
    }

    /**
     * Refreshes the cache of markets to scan. Includes:
     * - Player-owned markets
     * - Markets where storage is accessible (player has been there)
     * - Markets in the player's current star system
     *
     * This runs every 60 seconds to balance performance with coverage.
     */
    private void refreshRelevantMarkets() {
        cachedMarketIds.clear();

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            // Always include player-owned markets
            if (market.isPlayerOwned()) {
                cachedMarketIds.add(market.getId());
                continue;
            }

            // Include markets in current star system
            if (playerFleet.getContainingLocation() != null &&
                    market.getContainingLocation() == playerFleet.getContainingLocation()) {
                cachedMarketIds.add(market.getId());
                continue;
            }

            // Include markets where storage exists and is accessible
            // (player has docked/visited and unlocked storage)
            SubmarketAPI storage = market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
            if (storage != null && !storage.getCargo().isEmpty()) {
                cachedMarketIds.add(market.getId());
            }
        }

        log.debug("Draconis: Sigma Octantis watchdog scanning " + cachedMarketIds.size() +
                " relevant markets (out of " + Global.getSector().getEconomy().getMarketsCopy().size() + " total)");
    }

    /**
     * Fires the confrontation dialog and marks this watchdog as done.
     */
    private void fireConfrontation(float currentRep) {
        log.info("Draconis: Sigma Octantis confrontation triggered (Draconis rep: " + currentRep + ")");

        // Set flag immediately to prevent double-fire across save/load
        Global.getSector().getMemoryWithoutUpdate().set(CONFRONTATION_FLAG, true);
        done = true;

        Global.getSector().getCampaignUI().showInteractionDialog(
                new XLII_SigmaOctantisConfrontation(),
                Global.getSector().getPlayerFleet()
        );
    }
}
