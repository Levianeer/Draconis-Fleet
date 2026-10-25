package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

/**
 * Script command: previews the Waste Not installation picket in the dialog text, using the same
 * inline ship list as the Kori Battlegroup preview, then puts a confirmation on the option that
 * launches the fight.
 * <p>
 * Usage in rules.csv script column:
 *   XLII_ShowWasteNotPicket &lt;engageOptionId&gt;
 * <p>
 * The engage option must be in the same row's options column - FireBest adds a row's options
 * before running its script, which is what lets this attach the confirmation.
 */
@SuppressWarnings("unused")
public class XLII_ShowWasteNotPicket extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        XLII_ShowBattlegroupAssessment.render(dialog,
                XLII_LaunchWasteNotInstallationFight.createPicketFleet(),
                "Your sensors officer has a clean read on the site's security picket from this range.",
                "picket");

        if (!params.isEmpty()) {
            dialog.getOptionPanel().addOptionConfirmation(params.get(0).getString(memoryMap),
                    "If the picket drives you off, the installation will purge everything before you " +
                    "can return. There is no second attempt. Engage the picket?",
                    "Engage", "Cancel");
        }
        return true;
    }
}
