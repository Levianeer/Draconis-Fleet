package levianeer.draconis.data.campaign.intel.events.crisis;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.intel.aicore.theft.DraconisAICoreTheftListener;

import java.awt.*;

public class DraconisPunitiveExpedition extends GenericRaidFGI {

    public DraconisPunitiveExpedition(GenericRaidParams params) {
        super(params);

        if (params != null && params.raidParams != null && params.raidParams.allowedTargets != null) {
            Global.getLogger(this.getClass()).info(
                    "Draconis expedition created targeting " + params.raidParams.allowedTargets.size() + " markets"
            );
        }
    }

    @Override
    public String getBaseName() {
        return "Strike Group";
    }

    public static DraconisPunitiveExpedition get() {
        return (DraconisPunitiveExpedition) Global.getSector().getIntelManager().getFirstIntel(DraconisPunitiveExpedition.class);
    }

    // Track state changes
    private boolean wasSucceeded = false;
    private boolean wasFailed = false;
    private boolean completionHandled = false;

    @Override
    public void advance(float amount) {
        super.advance(amount);

        // Detect state changes since lifecycle methods aren't called
        boolean currentSucceeded = isSucceeded();
        boolean currentFailed = isFailed();

        // Raid just succeeded - Draconis forces won
        if (currentSucceeded && !wasSucceeded) {
            Global.getLogger(this.getClass()).info("Draconis expedition succeeded");
            wasSucceeded = true;
            handleRaidCompletion();
        }

        // Raid just failed - Player defeated it
        if (currentFailed && !wasFailed) {
            Global.getLogger(this.getClass()).info("Draconis expedition defeated by player");
            wasFailed = true;
            handleRaidCompletion();
        }

        // Handle reset conditions
        if (DraconisFleetHostileActivityFactor.meetsResetConditions() && !isEnding() && !isEnded()) {
            endAfterDelay();
        }
    }

    private void handleRaidCompletion() {
        // Prevent multiple calls
        if (completionHandled) return;
        completionHandled = true;

        // If raid FAILED, player defeated the expedition - grant bonus
        if (isFailed() && !DraconisFleetHostileActivityFactor.isPlayerDefeatedDraconisAttack()) {
            DraconisFleetHostileActivityFactor.setPlayerDefeatedDraconisAttack();
            DraconisArmamentsBonus.grantBonus(true);
        }

        // If raid SUCCEEDED, Draconis forces won - steal AI cores
        if (isSucceeded()) {
            stealAICores();
        }
    }

    private void stealAICores() {
        GenericRaidParams params = this.params;

        if (params == null || params.raidParams == null || params.raidParams.allowedTargets == null) {
            Global.getLogger(this.getClass()).warn("Cannot access raid targets for AI core theft");
            return;
        }

        for (MarketAPI target : params.raidParams.allowedTargets) {
            if (target == null) continue;

            boolean isPlayerTarget = target.isPlayerOwned();
            DraconisAICoreTheftListener.checkAndStealAICores(target, isPlayerTarget, "raid");
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