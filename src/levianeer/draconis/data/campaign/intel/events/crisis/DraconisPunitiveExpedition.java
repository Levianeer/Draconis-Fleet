package levianeer.draconis.data.campaign.intel.events.crisis;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.intel.aicore.theft.DraconisAICoreTheftListener;

import java.awt.*;
import java.util.List;

public class DraconisPunitiveExpedition extends GenericRaidFGI {

    private boolean theftProcessed = false;

    public DraconisPunitiveExpedition(GenericRaidParams params) {
        super(params);

        if (params != null && params.raidParams != null && params.raidParams.allowedTargets != null) {
            Global.getLogger(this.getClass()).info(
                    "Draconis expedition created targeting " + params.raidParams.allowedTargets.size() + " markets"
            );

            // Log each target for debugging
            for (MarketAPI target : params.raidParams.allowedTargets) {
                Global.getLogger(this.getClass()).info(
                        "  Target: " + target.getName() + " (" + target.getFactionId() + ")"
                );
            }
        }
    }

    @Override
    public String getBaseName() {
        return "Strike Group";
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
            Global.getLogger(this.getClass()).info(
                    "Expedition succeeded - initiating AI core theft"
            );

            handleRaidSuccess();
            theftProcessed = true;
        }

        // Grant bonus if raid failed (player defeated it)
        if (isFailed() && !theftProcessed) {
            Global.getLogger(this.getClass()).info(
                    "Expedition defeated by player - granting bonus"
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
            Global.getLogger(this.getClass()).error("Params is null!");
            return;
        }

        if (params.raidParams == null) {
            Global.getLogger(this.getClass()).error("RaidParams is null!");
            return;
        }

        List<MarketAPI> targets = params.raidParams.allowedTargets;

        if (targets == null || targets.isEmpty()) {
            Global.getLogger(this.getClass()).error("No raid targets found!");
            return;
        }

        Global.getLogger(this.getClass()).info(
                "Processing " + targets.size() + " raid targets for AI core theft"
        );

        for (MarketAPI target : targets) {
            if (target == null) {
                Global.getLogger(this.getClass()).warn("Null target in list, skipping");
                continue;
            }

            boolean isPlayerTarget = target.isPlayerOwned();

            Global.getLogger(this.getClass()).info(
                    "Stealing AI cores from " + target.getName() +
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

        info.addPara("The Draconis Alliance views your growing heavy armaments production as a direct threat to their "
                + "monopoly on advanced military equipment. This strike force has been dispatched to eliminate "
                + "your production capabilities.", opad);

        info.addPara("Unlike raiders, this is a professional military operation with clear strategic objectives. "
                + "The strike force will attempt to systematically destroy all heavy armaments production facilities "
                + "in the target system through %s and %s.", opad, h, "ground raids", "orbital bombardment");

        info.addPara("Intelligence suggests they may also attempt to %s from any colonies they attack.",
                opad, n, "steal AI cores");
    }
}