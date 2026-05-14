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
 * Delivers a fixed credit reward for the external Ring-Port capture
 * resolution dialog with August.
 * <p>
 * Usage in rules.csv script column:
 *   XLII_DeliverExternalReward 500000
 */
@SuppressWarnings("unused")
public class XLII_DeliverExternalReward extends BaseCommandPlugin {

    private static final Logger log = Global.getLogger(XLII_DeliverExternalReward.class);

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (params.isEmpty()) {
            log.warn("Draconis: XLII_DeliverExternalReward - no credit amount param");
            return false;
        }

        int credits = (int) params.get(0).getFloat(memoryMap);

        Global.getSector().getPlayerFleet().getCargo().getCredits().add(credits);

        if (dialog != null) {
            AddRemoveCommodity.addCreditsGainText(credits, dialog.getTextPanel());
        }

        log.info("Draconis: XLII_DeliverExternalReward - " + credits + " credits");
        return true;
    }
}
