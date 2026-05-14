package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.characters.XLII_Characters;

import java.util.List;
import java.util.Map;

/**
 * Script command: immediately unhides Elias Korrin in Ring-Port's comm directory.
 * Called from rules.csv when the player first arrives at Ring-Port with transponder off.
 * <p>
 * Usage in rules.csv script column:
 *   XLII_RevealEliasKorrin
 */
@SuppressWarnings("unused")
public class XLII_RevealEliasKorrin extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        XLII_Characters.revealEliasKorrin();
        return true;
    }
}
