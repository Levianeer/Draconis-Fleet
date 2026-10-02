package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.events.XLII_KoriStrike;

import java.util.List;
import java.util.Map;

/**
 * Action command: fired by rules.csv's XLII_burn_raid_goback rule (alongside
 * XLII_RestoreBurnRaidTransponder), replacing the plain DismissDialog that used to sit on that row.
 * <p>
 * Reopens XLII_KoriStrike at its BRIEFING state instead of dismissing the dialog outright - see
 * XLII_KoriStrike.forBriefing() for why that's the right landing point rather than the very start
 * of the pre-raid confrontation.
 * <p>
 * Usage in rules.csv script column:
 *   XLII_ReturnToBriefing
 */
@SuppressWarnings("unused")
public class XLII_ReturnToBriefing extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        XLII_KoriStrike strike = XLII_KoriStrike.forBriefing();
        dialog.setPlugin(strike);
        strike.init(dialog);
        return true;
    }
}
