package levianeer.draconis.data.campaign.intel.events.crisis;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.intel.aicore.theft.DraconisAICoreTheftListener;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.List;

public class DraconisPunitiveExpedition extends GenericRaidFGI {
    private static final Logger log = Global.getLogger(DraconisPunitiveExpedition.class);

    private boolean theftProcessed = false;

    public DraconisPunitiveExpedition(GenericRaidParams params) {
        super(params);

        if (params != null && params.raidParams != null && params.raidParams.allowedTargets != null) {
            log.info(
                    "Draconis: Draconis expedition created targeting " + params.raidParams.allowedTargets.size() + " markets"
            );

            // Log each target for debugging
            for (MarketAPI target : params.raidParams.allowedTargets) {
                log.info(
                        "Draconis:   Target: " + target.getName() + " (" + target.getFactionId() + ")"
                );
            }
        }
    }

    @Override
    public String getBaseName() {
        return "Draconis Alliance Strike";
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
            log.info(
                    "Draconis: Expedition succeeded - initiating AI core theft"
            );

            handleRaidSuccess();
            theftProcessed = true;
        }

        // Grant bonus if raid failed (player defeated it)
        if (isFailed() && !theftProcessed) {
            log.info(
                    "Draconis: Expedition defeated by player - granting bonus"
            );

            if (!DraconisFleetHostileActivityFactor.isPlayerDefeatedDraconisAttack()) {
                DraconisFleetHostileActivityFactor.setPlayerDefeatedDraconisAttack();
                DraconisArmamentsBonus.grantBonus(true);
            }

            theftProcessed = true;
        }

        // Handle reset conditions
        if (DraconisFleetHostileActivityFactor.meetsResetConditions() &&
                !isEnding() && !isEnded()) {
            endAfterDelay();
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

        log.info(
                "Draconis: Processing " + targets.size() + " raid targets for AI core theft"
        );

        for (MarketAPI target : targets) {
            if (target == null) {
                log.warn("Draconis: Null target in list, skipping");
                continue;
            }

            boolean isPlayerTarget = target.isPlayerOwned();

            log.info(
                    "Draconis: Stealing AI cores from " + target.getName() +
                            " (Player owned: " + isPlayerTarget + ")"
            );

            DraconisAICoreTheftListener.checkAndStealAICores(
                    target, isPlayerTarget, "raid"
            );
        }
    }

    @Override
    public void addAssessmentSection(TooltipMakerAPI info, float width, float height, float opad) {
        Color h = Misc.getHighlightColor();
        Color n = Misc.getNegativeHighlightColor();

        info.addPara("The Alliance Intelligence Office views your existence as a direct threat to "
                + "Fafnir's military alliance. A massive XLII Battlegroup detachment has been dispatched to eliminate "
                + "your production capabilities.", opad, h, "massive");

        info.addPara("Unlike raiders, this is a professional military operation with clear strategic objectives. "
                + "The strike force will attempt to systematically destroy all heavy armaments production facilities "
                + "in the target system through %s and %s.", opad, h, "ground raids", "orbital bombardment");

        info.addPara("Intelligence suggests a special forces element will attempt to %s from any colonies they attack.",
                opad, n, "steal AI cores");
    }

    /**
     * Configure fleet quality - all expedition fleets get SMOD_3 (elite quality)
     */
    @Override
    protected void configureFleet(int size, FleetCreatorMission m) {
        super.configureFleet(size, m);

        // All expedition fleets are elite quality with SMOD_3
        m.triggerSetFleetQuality(HubMissionWithTriggers.FleetQuality.SMOD_3);

        log.info("Draconis: Set SMOD_3 quality for expedition fleet (difficulty: " + size + ")");
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
            int marineCount = Math.max(estimatedFP * 20, 200);  // ~20 marines per estimated FP
            fleet.getCargo().addMarines(marineCount);
            log.info("Draconis: Added " + marineCount + " marines to expedition fleet (difficulty: " + size + ", est. FP: ~" + estimatedFP + ")");
        }
    }
}