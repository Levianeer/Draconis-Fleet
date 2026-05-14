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
 * AIO Operative trade: deducts credits and grants 1 story point.
 * Cost doubles each purchase within a cycle (1M, 2M, 4M, 8M...), resetting each new cycle.
 * <p>
 * Called with "display" param to show the current cost in the pre-confirmation dialog.
 * Called with no params to execute the purchase.
 * <p>
 * Sets $XLII_aio_sp_purchased = true on success so rules.csv can branch to
 * the success confirmation text. On failure (insufficient credits), prints an
 * error and leaves the flag unset so the failure path fires instead.
 * <p>
 * Persistent memory keys (global):
 *   $XLII_aio_sp_cycle  - int, cycle in which sp_count was accumulated
 *   $XLII_aio_sp_count  - int, number of SPs purchased in that cycle
 */
@SuppressWarnings("unused")
public class XLII_BuySP extends BaseCommandPlugin {

    private static final Logger log = Global.getLogger(XLII_BuySP.class);
    private static final String KEY_CYCLE = "$XLII_aio_sp_cycle";
    private static final String KEY_COUNT = "$XLII_aio_sp_count";

    private long computeCost(MemoryAPI mem) {
        int currentCycle = Global.getSector().getClock().getCycle();
        int storedCycle = mem.contains(KEY_CYCLE) ? (Integer) mem.get(KEY_CYCLE) : -1;
        int count = (storedCycle == currentCycle && mem.contains(KEY_COUNT)) ? (Integer) mem.get(KEY_COUNT) : 0;
        return 1_000_000L << count;
    }

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        boolean displayMode = !params.isEmpty() && "display".equals(params.get(0).getString(memoryMap));

        if (displayMode) {
            long cost = computeCost(mem);
            dialog.getTextPanel().addPara("He doesn't look away from his terminal.");
            dialog.getTextPanel().addPara(
                    "\"" + Misc.getDGSCredits((float) cost) + "\" A pause that contains the question he won't ask aloud.");
            return true;
        }

        // Purchase mode
        long cost = computeCost(mem);
        float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        if (credits < cost) {
            dialog.getTextPanel().addPara(
                    "Insufficient credits. " + Misc.getDGSCredits((float) cost) + " required.");
            return false;
        }

        int currentCycle = Global.getSector().getClock().getCycle();
        int storedCycle = mem.contains(KEY_CYCLE) ? (Integer) mem.get(KEY_CYCLE) : -1;
        int count = (storedCycle == currentCycle && mem.contains(KEY_COUNT)) ? (Integer) mem.get(KEY_COUNT) : 0;

        AddRemoveCommodity.addCreditsLossText((int) cost, dialog.getTextPanel());
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract((float) cost);
        Global.getSector().getPlayerStats().addStoryPoints(1, dialog.getTextPanel(), false);
        Global.getSector().getMemoryWithoutUpdate().set("$XLII_aio_sp_purchased", true);

        mem.set(KEY_CYCLE, currentCycle, 0f);
        mem.set(KEY_COUNT, count + 1, 0f);

        log.info("Draconis: XLII_BuySP - purchase #" + (count + 1) + " this cycle, cost=" + cost);
        return true;
    }
}
