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
 * Condition command: returns true if at least N game days have elapsed
 * since a day was stored via XLII_StoreCurrentDay.
 * <p>
 * Usage in rules.csv conditions column:
 *   XLII_CheckDaysSince XLII_blindEyeLogDay 7
 * <p>
 * Parameters:
 *   [0] memory key written by XLII_StoreCurrentDay (without "$global." prefix)
 *   [1] required elapsed days (integer or float)
 * <p>
 * If the key hasn't been set yet, stores the current timestamp and returns false
 * (starts counting from now). This handles cases where the prerequisite flag was
 * set via dev console rather than through the normal rules.csv flow.
 */
@SuppressWarnings("unused")
public class XLII_CheckDaysSince extends BaseCommandPlugin {

    private static final Logger log = Global.getLogger(XLII_CheckDaysSince.class);

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (params.size() < 2) {
            log.warn("Draconis: XLII_CheckDaysSince - requires 2 params (key, days), got " + params.size());
            return false;
        }

        String key = "$global." + params.get(0).getString(memoryMap);
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        if (!mem.contains(key)) {
            long now = Global.getSector().getClock().getTimestamp();
            mem.set(key, now, 0f);
            log.debug("Draconis: XLII_CheckDaysSince - key not set, storing timestamp now and returning false: " + key);
            return false;
        }

        float requiredDays;
        try {
            requiredDays = Float.parseFloat(params.get(1).getString(memoryMap));
        } catch (NumberFormatException e) {
            log.warn("Draconis: XLII_CheckDaysSince - invalid days param: " + params.get(1).getString(memoryMap));
            return false;
        }

        long storedTimestamp = ((Number) mem.get(key)).longValue();
        float elapsed = Global.getSector().getClock().getElapsedDaysSince(storedTimestamp);
        boolean result = elapsed >= requiredDays;
        log.debug("Draconis: XLII_CheckDaysSince - key=" + key + " storedTimestamp=" + storedTimestamp
                + " elapsed=" + elapsed + " required=" + requiredDays + " result=" + result);
        return result;
    }
}