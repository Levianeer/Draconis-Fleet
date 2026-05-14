package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * AIO Operative trade: deducts 1,000,000 credits and grants 1 story point.
 * Sets $XLII_aio_sp_purchased = true on success so rules.csv can branch to
 * the success confirmation text. On failure (insufficient credits), prints an
 * error and leaves the flag unset so the failure path fires instead.
 */
@SuppressWarnings("unused")
public class XLII_BuySP extends BaseCommandPlugin {

    private static final Logger log = Global.getLogger(XLII_BuySP.class);
    private static final int COST = 1_000_000;

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        if (credits < COST) {
            dialog.getTextPanel().addPara("Insufficient credits. One million required.");
            return false;
        }

        AddRemoveCommodity.addCreditsLossText(COST, dialog.getTextPanel());
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(COST);
        Global.getSector().getPlayerStats().addStoryPoints(1, dialog.getTextPanel(), false);
        Global.getSector().getMemoryWithoutUpdate().set("$XLII_aio_sp_purchased", true);

        log.info("Draconis: XLII_BuySP - " + COST + " credits deducted, 1 SP granted");
        return true;
    }
}
