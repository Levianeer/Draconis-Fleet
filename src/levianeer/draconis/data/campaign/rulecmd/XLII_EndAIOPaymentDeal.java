package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;
import levianeer.draconis.data.campaign.intel.events.crisis.deal.DraconisAIOPaymentDealIntel;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Ends the active AIO payment deal, if one exists.
 * <p>
 * Usage in rules.csv script column: {@code XLII_EndAIOPaymentDeal}
 */
@SuppressWarnings("unused")
public class XLII_EndAIOPaymentDeal extends BaseCommandPlugin {

    private static final Logger log = Global.getLogger(XLII_EndAIOPaymentDeal.class);

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                            List<Token> params, Map<String, MemoryAPI> memoryMap) {
        DraconisAIOPaymentDealIntel deal = DraconisAIOPaymentDealIntel.get();
        if (deal != null) {
            deal.endImmediately();
            log.info("DDA: AIO payment deal terminated by player");
        }
        return true;
    }
}
