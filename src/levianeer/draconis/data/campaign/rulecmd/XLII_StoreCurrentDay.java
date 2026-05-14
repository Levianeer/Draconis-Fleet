package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Script command: stores the current game day into a global memory key.
 * Used to timestamp when a significant flag is first set, so elapsed-time
 * checks can be performed later via XLII_CheckDaysSince.
 * <p>
 * Usage in rules.csv script column:
 *   XLII_StoreCurrentDay XLII_blindEyeLogDay
 * <p>
 * Writes to "$global.&lt;param&gt;" - the "$global." prefix is added automatically.
 * If the key is already set this command is a no-op (does not overwrite).
 */
@SuppressWarnings("unused")
public class XLII_StoreCurrentDay extends BaseCommandPlugin {

    private static final Logger log = Global.getLogger(XLII_StoreCurrentDay.class);

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (params.isEmpty()) {
            log.warn("Draconis: XLII_StoreCurrentDay - no key parameter provided");
            return false;
        }

        String key = "$global." + params.get(0).getString(memoryMap);
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        if (mem.contains(key)) {
            log.debug("Draconis: XLII_StoreCurrentDay - key already set, skipping: " + key);
            return true;
        }

        long timestamp = Global.getSector().getClock().getTimestamp();
        mem.set(key, timestamp, 0f);
        log.debug("Draconis: XLII_StoreCurrentDay - stored timestamp " + timestamp + " to " + key);
        return true;
    }
}