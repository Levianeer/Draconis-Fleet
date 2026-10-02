package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.characters.XLII_Characters;

import java.util.List;
import java.util.Map;

/**
 * Script command: immediately unhides Daniel Ancker in the comm directory.
 * Called from rules.csv when the player meets him for the first time somewhere other than the
 * XLII_AIOOperativeBarEvent bar event (e.g. the Threshold-stage Ladon escort scene, for a player who
 * never got that bar event) - mirrors XLII_RevealEliasKorrin's shape exactly.
 * <p>
 * Does not set $global.XLII_aio_operative_revealed itself - the caller is expected to set that
 * memory flag directly in the same script block, matching how XLII_AIOOperativeBarEvent does both
 * steps itself rather than folding the flag-set into this command.
 * <p>
 * Usage in rules.csv script column:
 *   XLII_RevealDanielAncker
 */
@SuppressWarnings("unused")
public class XLII_RevealDanielAncker extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        XLII_Characters.revealDanielAncker();
        return true;
    }
}
