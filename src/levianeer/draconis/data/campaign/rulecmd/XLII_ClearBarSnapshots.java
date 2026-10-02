package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;

import java.util.List;
import java.util.Map;

/**
 * Clears the bar-event snapshot ({@code $BarCMD_shownEvents}) on every player-owned market, so a
 * bar event that just resolved (accepted/declined/deal-changed) can appear again on the next visit
 * instead of waiting out the shown-events cache.
 * <p>
 * Usage in rules.csv script column: {@code XLII_ClearBarSnapshots}
 */
@SuppressWarnings("unused")
public class XLII_ClearBarSnapshots extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                            List<Token> params, Map<String, MemoryAPI> memoryMap) {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (Factions.PLAYER.equals(market.getFactionId())) {
                market.getMemoryWithoutUpdate().unset("$BarCMD_shownEvents");
            }
        }
        return true;
    }
}
