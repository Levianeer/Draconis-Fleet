package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Delivers the Ring-Port raid reward: credits (scaled by marine losses) and
 * story points (half of those spent during the assault, rounded down).
 * <p>
 * Reward amounts are pre-calculated and stored as global memkeys by
 * XLII_RingPortAssault.finalizeRaid(). This command reads them and applies
 * the reward with visual feedback in the current dialog panel.
 * <p>
 * Called from rules.csv in XLII_august_debrief_ack and XLII_august_debrief_elias_close.
 */

@SuppressWarnings("unused")
public class XLII_DeliverRaidReward extends BaseCommandPlugin {

    private static final Logger log = Global.getLogger(XLII_DeliverRaidReward.class);

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        Object rawCredits = mem.get("$XLII_raidCreditReward");
        Object rawSP = mem.get("$XLII_raidSPReward");

        if (rawCredits == null) {
            log.warn("Draconis: XLII_DeliverRaidReward - $XLII_raidCreditReward not set, skipping");
            return false;
        }

        float credits = ((Number) rawCredits).floatValue();
        int sp = rawSP != null ? ((Number) rawSP).intValue() : 0;

        Global.getSector().getPlayerFleet().getCargo().getCredits().add(credits);

        if (dialog != null) {
            TextPanelAPI text = dialog.getTextPanel();
            AddRemoveCommodity.addCreditsGainText((int) credits, text);
            if (sp > 0) {
                Global.getSector().getPlayerStats().addStoryPoints(sp, text, false);
            }
        } else if (sp > 0) {
            Global.getSector().getPlayerStats().addStoryPoints(sp, null, false);
        }

        log.info("Draconis: XLII_DeliverRaidReward - " + credits + " credits, " + sp + " SP");
        return true;
    }
}