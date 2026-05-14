package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.events.XLII_RingPortAssault;

import java.util.List;
import java.util.Map;

/**
 * Condition command: returns true if Ring-Port Station has changed hands
 * outside the scripted Blind Eye assault (player raid, Nexerelin invasion,
 * third-party capture).
 * <p>
 * True when: Ring-Port market exists, is no longer pirate-controlled, and
 * {@code $XLII_ringPortTaken} has not been set by the scripted assault.
 * <p>
 * Usage in rules.csv conditions column:
 *   XLII_IsRingPortTakenExternally
 *   !XLII_IsRingPortTakenExternally
 */
@SuppressWarnings("unused")
public class XLII_IsRingPortTakenExternally extends BaseCommandPlugin {

    private static final String RING_PORT_MARKET_ID = "pirateStation_market";

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MarketAPI market = Global.getSector().getEconomy().getMarket(RING_PORT_MARKET_ID);
        if (market == null) return false;
        if (Factions.PIRATES.equals(market.getFactionId())) return false;
        return !Global.getSector().getMemoryWithoutUpdate().getBoolean(XLII_RingPortAssault.MEM_TAKEN);
    }
}
