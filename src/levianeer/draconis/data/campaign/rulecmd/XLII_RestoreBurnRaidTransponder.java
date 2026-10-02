package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.events.XLII_KoriStrike;

import java.util.List;
import java.util.Map;

/**
 * Action command: fired by rules.csv's XLII_burn_raid_goback rule, alongside DismissDialog, when the
 * player backs out of the Burn the Machine raid picker before committing to a raid attempt.
 * <p>
 * XLII_KoriStrike.launchRaid() forces the player's transponder on for the duration of the raid
 * (see that method's own notes - blocks MarketCMD's Story-Point "keep it secret" rep-penalty
 * bypass, which requires the transponder to be off) and normally restores it once the raid
 * concludes (XLII_ResolveBurnRaid). But backing out via "Go back" never reaches that step, so
 * without this the transponder would stay stuck on until the player noticed and toggled it back
 * manually. Calls the same restoreTransponderIfNeeded() helper XLII_ResolveBurnRaid uses.
 * <p>
 * Usage in rules.csv script column:
 *   XLII_RestoreBurnRaidTransponder
 */
@SuppressWarnings("unused")
public class XLII_RestoreBurnRaidTransponder extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        XLII_KoriStrike.restoreTransponderIfNeeded();
        return true;
    }
}
