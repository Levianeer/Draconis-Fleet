package levianeer.draconis.data.campaign.intel.events.aicore;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission;
import levianeer.draconis.data.campaign.ids.FleetTypes;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;
import static levianeer.draconis.data.campaign.ids.Factions.FORTYSECOND;

/**
 * Custom raid intel for AI Core acquisition raids
 * Uses Shadow Fleet (FORTYSECOND) faction via params
 */
public class DraconisAICoreRaidIntel extends GenericRaidFGI {

    private final MarketAPI target;

    public DraconisAICoreRaidIntel(GenericRaidParams params, MarketAPI target) {
        super(params);
        this.target = target;
    }

    @Override
    public String getBaseName() {
        return "Shadow Fleet AI Core Raid";
    }

    public MarketAPI getTarget() {
        return target;
    }

    @Override
    protected void notifyEnding() {
        super.notifyEnding();

        // Handle cleanup for standalone raids (those without a listener)
        // If there's a listener, it will handle the cleanup
        if (getListener() == null) {
            Global.getLogger(this.getClass()).info("Standalone AI Core Raid ending");

            boolean success = isSucceeded();

            // Decrement active raid count
            DraconisAICoreRaidManager.decrementActiveRaidCount();

            // Start cooldown based on success/failure
            DraconisAICoreRaidManager.startCooldown(success);

            Global.getLogger(this.getClass()).info("Standalone raid " + (success ? "succeeded" : "failed") + " - cooldown started");
        }
    }

    public static String RAIDER_FLEET = "$draconisRaider";

    /**
     * Configure fleet during creation to have Shadow Fleet characteristics
     * Sets transponders off and fleet type
     */
    @Override
    protected void configureFleet(int size, FleetCreatorMission m) {
        super.configureFleet(size, m);

        m.triggerSetFleetType(FleetTypes.SHADOW_FLEET);

        m.triggerFleetSetNoFactionInName();
        m.triggerSetPirateFleet();
        m.triggerMakeHostile();
        m.triggerMakeNonHostileToFaction(DRACONIS);
        m.triggerMakeNonHostileToFaction(FORTYSECOND);
        m.triggerMakeNoRepImpact();
        m.triggerFleetAllowLongPursuit();
        m.triggerMakeHostileToAllTradeFleets();
        m.triggerMakeEveryoneJoinBattleAgainst();

        m.triggerSetFleetFlag(RAIDER_FLEET);

        m.triggerFleetMakeFaster(true, 0, true);
    }

    /**
     * Configure spawned fleet to ensure transponders remain off
     */
    @Override
    protected void configureFleet(int size, CampaignFleetAPI fleet) {
        super.configureFleet(size, fleet);

        // Ensure transponders are off
        if (fleet != null) {
            fleet.setTransponderOn(false);
        }
    }
}