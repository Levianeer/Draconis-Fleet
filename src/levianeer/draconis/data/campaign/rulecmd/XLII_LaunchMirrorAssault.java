package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.events.XLII_MirrorAssault;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Script command: hands off from the confirm dialog into the actual Fafnir Mirror fleet fight.
 * <p>
 * Usage in rules.csv script column:
 *   XLII_LaunchMirrorAssault alpha
 * <p>
 * Parameters:
 *   [0] mirror key - one of "alpha", "bravo", "charlie"
 * <p>
 * See {@link XLII_MirrorAssault#launch} for what this actually does (fails every active
 * Draconis quest, flips Draconis hostile, spawns/reuses the guard fleet, swaps this dialog into
 * the fleet-fight FID).
 */
@SuppressWarnings("unused")
public class XLII_LaunchMirrorAssault extends BaseCommandPlugin {

    private static final Logger log = Global.getLogger(XLII_LaunchMirrorAssault.class);

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null || params.isEmpty()) {
            log.warn("Draconis: XLII_LaunchMirrorAssault - missing dialog or mirror key param");
            return false;
        }

        String key = params.get(0).getString(memoryMap);
        XLII_MirrorAssault.launch(dialog, key);
        return true;
    }
}
