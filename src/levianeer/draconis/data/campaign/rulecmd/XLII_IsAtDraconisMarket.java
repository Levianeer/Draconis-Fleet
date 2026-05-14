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

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Condition command: returns true if the current interaction target is a
 * Draconis Defence Alliance market.
 * <p>
 * Usage in rules.csv conditions column:
 *   XLII_IsAtDraconisMarket
 * <p>
 * Uses direct Java faction ID check rather than $faction.id - the $faction.id
 * memory variable is only reliably populated in MarketPostDock contexts, not
 * during OpenInteractionDialog.
 */
@SuppressWarnings("unused")
public class XLII_IsAtDraconisMarket extends BaseCommandPlugin {

    private static final Logger log = Global.getLogger(XLII_IsAtDraconisMarket.class);

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
        SectorEntityToken entity = dialog.getInteractionTarget();
        if (entity == null) return false;

        MarketAPI market = entity.getMarket();
        if (market == null) {
            log.debug("Draconis: XLII_IsAtDraconisMarket - no market on entity");
            return false;
        }

        boolean result = DRACONIS.equals(market.getFactionId());
        log.debug("Draconis: XLII_IsAtDraconisMarket - marketFaction=" + market.getFactionId() + " result=" + result);
        return result;
    }
}