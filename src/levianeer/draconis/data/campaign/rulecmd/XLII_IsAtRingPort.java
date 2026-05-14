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
 * Ring-Port Station (pirateStation_market).
 * <p>
 * Usage in rules.csv conditions column:
 *   XLII_IsAtRingPort
 */
@SuppressWarnings("unused")
public class XLII_IsAtRingPort extends BaseCommandPlugin {

    private static final Logger log = Global.getLogger(XLII_IsAtRingPort.class);
    private static final String RING_PORT_MARKET_ID = "pirateStation_market";

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
        SectorEntityToken entity = dialog.getInteractionTarget();
        if (entity == null) return false;

        MarketAPI market = entity.getMarket();
        if (market == null) return false;

        boolean result = RING_PORT_MARKET_ID.equals(market.getId());
        log.debug("Draconis: XLII_IsAtRingPort - marketId=" + market.getId() + " result=" + result);
        return result;
    }
}
