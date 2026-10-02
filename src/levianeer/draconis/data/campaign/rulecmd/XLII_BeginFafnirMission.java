package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;
import levianeer.draconis.data.campaign.intel.fafnir.FafnirAccessMissionIntel;

import java.util.List;
import java.util.Map;

/**
 * Creates the Fafnir-access mission intel for the given path (mirrors
 * {@code FafnirAccessStrings.PATH_TT_COURIER} / {@code PATH_RING_PORT}), or completes the
 * currently active one.
 * <p>
 * Usage in rules.csv script column:
 *   {@code XLII_BeginFafnirMission tt_courier} - creates the intel for that path
 *   {@code XLII_BeginFafnirMission complete} - pays the reward and completes the active intel
 */
@SuppressWarnings("unused")
public class XLII_BeginFafnirMission extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                            List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (params.isEmpty()) return false;
        String arg = params.get(0).getString(memoryMap);
        if ("complete".equals(arg)) {
            FafnirAccessMissionIntel intel = FafnirAccessMissionIntel.get();
            if (intel != null) intel.complete();
            return true;
        }
        new FafnirAccessMissionIntel(arg);
        return true;
    }
}
