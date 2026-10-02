package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.intel.longsight.crisis.XLII_LongsightCrisisManager;

import java.util.List;
import java.util.Map;

/**
 * Dev-only action command: forces the Office Takeover crisis on, the same way
 * {@code XLII_KoriStrike.finalizeFailure()} does for the real trigger - sets
 * {@link XLII_LongsightCrisisManager#DEBUG_FORCE_KEY} and calls
 * {@link XLII_LongsightCrisisManager#createIfNecessary()}. A no-op if the crisis is already
 * running or has already permanently resolved (both guarded inside createIfNecessary() itself).
 * <p>
 * Usage in rules.csv script column:
 *   XLII_DevForceLongsightCrisis
 */
@SuppressWarnings("unused")
public class XLII_DevForceLongsightCrisis extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        Global.getSector().getMemoryWithoutUpdate().set(XLII_LongsightCrisisManager.DEBUG_FORCE_KEY, true);
        XLII_LongsightCrisisManager.createIfNecessary();
        return true;
    }
}
