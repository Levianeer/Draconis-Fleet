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
 * Script command: if no market has yet been locked for Walt Hollis's "Waste Not" bar hook (see
 * {@link XLII_IsWasteNotHookMarket}), records the current interaction market as that lock. A no-op
 * once a market is already locked.
 * <p>
 * Run from the script column of both {@code XLII_waste_not_bar_add} (the hook) and
 * {@code XLII_waste_not_report_bar_add} (the report-back) - harmless to call from the latter too,
 * which only matters for a save that reached {@code $global.XLII_wasteNotStarted} before this lock
 * existed and so never ran it from the hook.
 * <p>
 * Usage in rules.csv script column:
 *   XLII_LockWasteNotHookMarket
 */
@SuppressWarnings("unused")
public class XLII_LockWasteNotHookMarket extends BaseCommandPlugin {

    private static final Logger log = Global.getLogger(XLII_LockWasteNotHookMarket.class);

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
        SectorEntityToken entity = dialog.getInteractionTarget();
        if (entity == null) return false;

        MarketAPI market = entity.getMarket();
        if (market == null) return false;

        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        String locked = mem.getString(XLII_IsWasteNotHookMarket.MEM_KEY);
        if (locked == null || locked.isEmpty()) {
            mem.set(XLII_IsWasteNotHookMarket.MEM_KEY, market.getId());
            log.info("Draconis: Waste Not hook market locked to " + market.getId());
        }
        return true;
    }
}
