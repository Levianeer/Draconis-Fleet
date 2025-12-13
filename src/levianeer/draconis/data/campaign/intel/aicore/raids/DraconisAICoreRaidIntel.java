package levianeer.draconis.data.campaign.intel.aicore.raids;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.ids.FleetTypes;
import levianeer.draconis.data.campaign.intel.aicore.scanner.DraconisSingleTargetScanner;
import levianeer.draconis.data.campaign.intel.aicore.theft.DraconisAICoreTheftListener;

import java.io.Serial;

/**
 * AI Core acquisition raid intel
 * Uses standard base game raid mechanics with tactical bombardment
 * AI core theft occurs via finish() override on successful completion
 */
public class DraconisAICoreRaidIntel extends GenericRaidFGI {
    private transient IntervalUtil interval;

    /**
     * Serializable custom data class to survive save/load cycles
     * Stored in params.custom field which IS properly serialized by base game
     */
    public static class CustomRaidData implements java.io.Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        public String targetMarketId;
        public boolean isPlayerMarket;
    }

    public DraconisAICoreRaidIntel(GenericRaidParams params, MarketAPI target) {
        super(params);

        // Store target data in params.custom for proper serialization
        CustomRaidData data = new CustomRaidData();
        data.targetMarketId = target != null ? target.getId() : null;
        data.isPlayerMarket = target != null && target.isPlayerOwned();
        params.custom = data;

        interval = new IntervalUtil(0.1f, 0.3f);
    }

    @Override
    public String getBaseName() {
        return "Shadow Fleet AI Core Raid";
    }

    /**
     * Get the raid target market (retrieved from serialized data)
     */
    public MarketAPI getTarget() {
        CustomRaidData data = getCustomData();
        if (data != null && data.targetMarketId != null) {
            return Global.getSector().getEconomy().getMarket(data.targetMarketId);
        }
        return null;
    }

    /**
     * Check if target was player-owned (retrieved from serialized data)
     */
    private boolean isPlayerMarket() {
        CustomRaidData data = getCustomData();
        return data != null && data.isPlayerMarket;
    }

    /**
     * Get custom raid data from params
     */
    private CustomRaidData getCustomData() {
        if (params != null && params.custom instanceof CustomRaidData) {
            return (CustomRaidData) params.custom;
        }
        return null;
    }

    /**
     * Ensure interval is recreated after deserialization
     */
    public Object readResolve() {
        if (interval == null) {
            interval = new IntervalUtil(0.1f, 0.3f);
        }
        return this;
    }

    /**
     * Override finish() to intercept raid completion and handle AI core theft
     * This is called exactly once when the raid ends, with isAbort indicating success/failure
     * @param isAbort false = raid succeeded, true = raid failed/aborted
     */
    public void finish(boolean isAbort) {
        // Retrieve target from serialized data
        MarketAPI target = getTarget();
        boolean isPlayerMarket = isPlayerMarket();

        // Check raid outcome using base game success tracking
        boolean succeeded = !isAbort && isSucceeded();

        // Handle raid success - steal AI cores
        if (succeeded && target != null) {
            // Steal AI cores from the successfully raided market
            DraconisAICoreTheftListener.checkAndStealAICores(
                target, isPlayerMarket, "ai_core_raid"
            );

            // Clear high-value target flags after successful raid
            DraconisSingleTargetScanner.clearTargetAfterRaid(target);

            // Decrement active raid count
            DraconisAICoreRaidManager.decrementActiveRaidCount();

            // Start success cooldown (75 days)
            DraconisAICoreRaidManager.startCooldown(true);
        }
        // Handle raid failure/abort - cleanup without theft
        else {
            // Clear high-value target flags (target gets breathing room)
            if (target != null) {
                DraconisSingleTargetScanner.clearTargetAfterRaid(target);
            }

            // Decrement active raid count
            DraconisAICoreRaidManager.decrementActiveRaidCount();

            // Start failure cooldown (150 days)
            DraconisAICoreRaidManager.startCooldown(false);
        }

        // Call parent implementation to complete the raid lifecycle
        super.finish(isAbort);
    }

    public static String RAIDER_FLEET = "$draconisRaider";

    /**
     * Advance method - handles raid lifecycle
     */
    public void advance(float amount) {
        super.advance(amount);

        // Ensure interval exists after deserialization
        if (interval == null) {
            interval = new IntervalUtil(0.1f, 0.3f);
        }

        float days = Misc.getDays(amount);
        interval.advance(days);
    }

    /**
     * Configure fleet during creation
     * Sets fleet type and behavior flags for standard raider operations
     */
    protected void configureFleet(int size, FleetCreatorMission m) {
        super.configureFleet(size, m);

        // Set fleet type for identification
        m.triggerSetFleetType(FleetTypes.SHADOW_FLEET);

        // Allow long pursuit (committed raiders)
        m.triggerFleetAllowLongPursuit();

        // Mark as raider fleet for AI behavior
        m.triggerSetFleetFlag(RAIDER_FLEET);

        // Make fleet faster for hit-and-run tactics
        m.triggerFleetMakeFaster(true, 0, true);
    }
}
