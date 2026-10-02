package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;
import levianeer.draconis.data.campaign.characters.XLII_Characters;
import levianeer.draconis.data.campaign.intel.events.crisis.deal.DraconisAIOPaymentDealIntel;

import java.util.List;
import java.util.Map;

/**
 * Creates the AIO payment-deal intel, clears the "previously declined" flag, and reveals
 * Daniel Ancker in the Ring-Port comm directory. Run this before {@code XLII_ComputeAIOOfferVars}
 * on the confirm beat, so the recomputed $aio_monthly reflects the deal that was just created.
 * <p>
 * Usage in rules.csv script column: {@code XLII_CreateAIOPaymentDeal}
 */
@SuppressWarnings("unused")
public class XLII_CreateAIOPaymentDeal extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                            List<Token> params, Map<String, MemoryAPI> memoryMap) {
        new DraconisAIOPaymentDealIntel();
        Global.getSector().getMemoryWithoutUpdate().unset("$dda_aio_bar_declined");
        Global.getSector().getMemoryWithoutUpdate().set("$XLII_aio_operative_revealed", true);
        XLII_Characters.revealDanielAncker();
        return true;
    }
}
