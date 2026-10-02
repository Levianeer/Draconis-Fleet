package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Condition command: returns true if the current interaction target is
 * Kori Starport (kori_market).
 * <p>
 * Usage in rules.csv conditions column:
 *   XLII_IsAtKori
 */
@SuppressWarnings("unused")
public class XLII_IsAtKori extends BaseCommandPlugin {

    private static final Logger log = Global.getLogger(XLII_IsAtKori.class);
    private static final String KORI_MARKET_ID = "kori_market";

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
        SectorEntityToken entity = dialog.getInteractionTarget();
        if (entity == null) return false;

        MarketAPI market = entity.getMarket();
        if (market == null) return false;

        boolean result = KORI_MARKET_ID.equals(market.getId());
        log.debug("Draconis: XLII_IsAtKori - marketId=" + market.getId() + " result=" + result);
        return result;
    }
}
