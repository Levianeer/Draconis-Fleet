package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.events.XLII_RingPortAssault;

import java.util.List;
import java.util.Map;

/**
 * Dev-only testing shortcut: skips XLII_RingPortAssault's FID combat and opens
 * directly on the post-assault decision beats. See .claude/systems/blind-eye.md.
 * Usage in rules.csv script column: XLII_DevSkipRingPortFight
 */
@SuppressWarnings("unused")
public class XLII_DevSkipRingPortFight extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        dialog.dismiss();
        XLII_RingPortAssault.devSkipToPostAssault();
        return true;
    }
}
