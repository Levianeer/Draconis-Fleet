package levianeer.draconis.data.campaign.intel.events.crisis.core;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.BombardType;
import levianeer.draconis.data.campaign.econ.conditions.DraconManager;
import levianeer.draconis.data.campaign.fleet.DraconisAICoreFleetInflater;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.intel.events.crisis.AIOStrings;
import levianeer.draconis.data.campaign.intel.aicore.theft.DraconisAICoreTheftListener;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.List;

public class DraconisPunitiveExpedition extends GenericRaidFGI {
    private static final Logger log = Global.getLogger(DraconisPunitiveExpedition.class);

    private boolean theftProcessed = false;

    public DraconisPunitiveExpedition(GenericRaidParams params) {
        super(params);
    }

    @Override
    public String getBaseName() {
        return AIOStrings.INTEL_EXPEDITION_BASE_NAME;
    }

    public static DraconisPunitiveExpedition get() {
        return (DraconisPunitiveExpedition) Global.getSector().getIntelManager()
                .getFirstIntel(DraconisPunitiveExpedition.class);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        // Process AI core theft when raid succeeds
        if (isSucceeded() && !theftProcessed) {
            log.info("Draconis: Punitive expedition succeeded");
            handleRaidSuccess();
            DraconManager.restoreRaidSavedLevel();
            // Reset crisis so it can be re-triggered via HAE (does NOT permanently end it)
            DraconisAIOTracker tracker = DraconisAIOTracker.get();
            if (tracker != null) {
                tracker.resetCrisisAfterSuccessfulInvasion();
            }
            theftProcessed = true;
        }

        // Grant bonus if raid failed (player defeated it) - notify AIO Tracker
        if (isFailed() && !theftProcessed) {
            log.info("Draconis: Punitive expedition defeated by player");
            DraconisAIOTracker tracker = DraconisAIOTracker.get();
            if (tracker != null) {
                tracker.onExpeditionDefeated();
            }
            DraconManager.restoreRaidSavedLevel();
            theftProcessed = true;
        }
    }

    private void handleRaidSuccess() {
        if (params == null) {
            log.error("Draconis: Params is null!");
            return;
        }

        if (params.raidParams == null) {
            log.error("Draconis: RaidParams is null!");
            return;
        }

        List<MarketAPI> targets = params.raidParams.allowedTargets;

        if (targets == null || targets.isEmpty()) {
            log.error("Draconis: No raid targets found!");
            return;
        }

        for (MarketAPI target : targets) {
            if (target == null) continue;
            DraconisAICoreTheftListener.checkAndStealAICores(target, target.isPlayerOwned(), "raid");
        }
    }

    @Override
    public void addAssessmentSection(TooltipMakerAPI info, float width, float height, float opad) {
        Color h = Misc.getHighlightColor();
        Color n = Misc.getNegativeHighlightColor();

        int fleetCount = (params != null) ? params.fleetSizes.size() : 0;
        String fleetCountStr = fleetCount + (fleetCount == 1 ? " fleet" : " fleets");

        info.addPara(AIOStrings.EXPEDITION_ASSESS_PARA1_FMT, opad, h, fleetCountStr);

        info.addPara(AIOStrings.EXPEDITION_ASSESS_PARA2_FMT, opad, h,
                AIOStrings.EXPEDITION_ASSESS_PARA2_H1, AIOStrings.EXPEDITION_ASSESS_PARA2_H2);

        DraconisAIOTracker tracker = DraconisAIOTracker.get();
        boolean isFinalRaid = tracker != null && tracker.getExpeditionDefeats() >= 2;
        String para3 = isFinalRaid
                ? AIOStrings.EXPEDITION_ASSESS_PARA3_FMT
                : AIOStrings.EXPEDITION_ASSESS_PARA3_NO_DISRUPT_FMT;
        info.addPara(para3, opad, n, AIOStrings.EXPEDITION_ASSESS_PARA3_HIGHLIGHT);
    }

    /**
     * Bypass the base game's fuel-capacity check for bombardment.
     * FGRaidAction.performRaid() checks fleet.getCargo().getMaxFuel() * 0.5f >= bombardCost
     * when fleets are spawned (player in system). FORTYSECOND combat ships have too little
     * fuel capacity to pass this, causing the raid to fail even after defeating all defenders.
     * By using the custom action hook instead, success is determined by raidCount (always
     * incremented) rather than bombardCount (only incremented if fuel check passes).
     */
    @Override
    public boolean hasCustomRaidAction() {
        return true;
    }

    @Override
    public void doCustomRaidAction(CampaignFleetAPI fleet, MarketAPI market, float raidStr) {
        log.info("Draconis: Performing tactical bombardment on " + market.getName());
        new MarketCMD(market.getPrimaryEntity()).doBombardment(getFaction(), BombardType.TACTICAL);
        disruptMilitaryStation(market);
    }

    /**
     * Disrupts the highest-tier military station at the target market.
     * Checked in descending order so star fortresses are hit before battlestations.
     */
    private void disruptMilitaryStation(MarketAPI market) {
        String[] ids = {
                Industries.STARFORTRESS,
                Industries.STARFORTRESS_MID,
                Industries.STARFORTRESS_HIGH,
                Industries.BATTLESTATION,
                Industries.BATTLESTATION_MID,
                Industries.BATTLESTATION_HIGH,
                Industries.ORBITALSTATION,
                Industries.ORBITALSTATION_MID,
                Industries.ORBITALSTATION_HIGH,
        };
        for (String id : ids) {
            Industry station = market.getIndustry(id);
            if (station != null) {
                station.setDisrupted(120f);
                log.info("Draconis: Disrupted " + station.getCurrentName() + " at " + market.getName());
                return;
            }
        }
    }

    /**
     * Configure fleet quality - all expedition fleets get SMOD_3 (elite quality).
     * Also marks the fleet as an expedition so the combat listener doesn't double-count
     * its defeat on top of the onExpeditionDefeated() tracker reset.
     */
    @Override
    protected void configureFleet(int size, FleetCreatorMission m) {
        super.configureFleet(size, m);

        // All expedition fleets are elite quality with SMOD_3
        m.triggerSetFleetQuality(HubMissionWithTriggers.FleetQuality.SMOD_3);
        // Mark as expedition so DraconisFleetCombatListener skips it
        m.triggerSetFleetFlag("$dda_expedition_fleet");
    }

    /**
     * Configure spawned fleet
     * Adds marines for ground raid capability
     */
    @Override
    protected void configureFleet(int size, CampaignFleetAPI fleet) {
        super.configureFleet(size, fleet);

        if (fleet != null) {
            // Add marines for ground raid capability
            int estimatedFP = size * 10; // Rough estimate: difficulty 10 ≈ 100 FP
            int marineCount = Math.max(estimatedFP * 2, 100);  // ~2 marines per estimated FP
            fleet.getCargo().addMarines(marineCount);

            fleet.addScript(new DraconisAICoreFleetInflater.DeferredInflateScript(fleet, true));
        }
    }
}
