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
 * Condition command: true if the current interaction market is the one Walt Hollis's "Waste Not"
 * bar hook has been locked to, or if no market has been locked yet.
 * <p>
 * Walt is meant to be a single chance encounter in one specific bar - found and reported back to
 * at the same market every time - not a bar event that re-adds itself at every market the player
 * docks at. Since which market that is isn't fixed in advance (he can turn up anywhere on first
 * encounter), the market has to be locked in dynamically rather than checked against a hardcoded
 * id the way {@link XLII_IsAtKori}/{@code XLII_IsAtRingPort} are. See
 * {@link XLII_LockWasteNotHookMarket}, which performs the actual locking.
 * <p>
 * Usage in rules.csv conditions column:
 *   XLII_IsWasteNotHookMarket
 */
@SuppressWarnings("unused")
public class XLII_IsWasteNotHookMarket extends BaseCommandPlugin {

    private static final Logger log = Global.getLogger(XLII_IsWasteNotHookMarket.class);

    /** Sector memory key holding the locked market's id, or unset if no market has been locked yet. */
    public static final String MEM_KEY = "$XLII_wasteNotHookMarketId";

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
        SectorEntityToken entity = dialog.getInteractionTarget();
        if (entity == null) return false;

        MarketAPI market = entity.getMarket();
        if (market == null) return false;

        String locked = Global.getSector().getMemoryWithoutUpdate().getString(MEM_KEY);
        boolean result = locked == null || locked.isEmpty() || locked.equals(market.getId());
        log.debug("Draconis: XLII_IsWasteNotHookMarket - marketId=" + market.getId()
                + " locked=" + locked + " result=" + result);
        return result;
    }
}
