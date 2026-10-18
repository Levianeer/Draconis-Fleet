package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;
import levianeer.draconis.data.campaign.events.XLII_MirrorAssault;

import java.util.List;
import java.util.Map;

/**
 * Script command: resolves the marine raid that finishes off a Stellar Array once its Echo Fleet
 * guard has already been destroyed in combat. Fired by the vanilla non-market ground raid menu's
 * success trigger (see {@code AddRaidObjective}, rules.csv "# [FAFNIR LORE] Stellar Arrays").
 * <p>
 * Usage in rules.csv script column:
 *   XLII_MirrorMarineRaid alpha
 * <p>
 * Parameters:
 *   [0] mirror key - one of "alpha", "bravo", "charlie"
 * <p>
 * See {@link XLII_MirrorAssault#arrayRaided} for what this actually does (marks the array
 * destroyed, and - once all three are down - reuses the Burn the Machine win path's own
 * Rift-collapse plumbing, same as the old single-step onMirrorDestroyed() used to).
 */
@SuppressWarnings("unused")
public class XLII_MirrorMarineRaid extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                            List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (params.isEmpty()) return false;
        XLII_MirrorAssault.arrayRaided(params.get(0).getString(memoryMap));
        return true;
    }
}
