package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;

import java.util.List;
import java.util.Map;

/**
 * Script command: closes out the Stellar Mirror quest (Faller's payoff scene at Ring-Port) and
 * applies its one mechanical consequence - Draconis' standing with the Hegemony takes a lasting
 * hit, now that the Rift is down and the Alliance is exposed.
 * <p>
 * Usage in rules.csv script column:
 *   XLII_CompleteStellarMirrorQuest
 * <p>
 * Sets {@code $global.XLII_stellarMirrorComplete} and Draconis' relationship with the Hegemony
 * to -0.50 (absolute, not a delta - deliberately not using adjustRelationship, since this is a
 * one-time narrative consequence rather than a repeatable reputation action).
 */
@SuppressWarnings("unused")
public class XLII_CompleteStellarMirrorQuest extends BaseCommandPlugin {

    // Fully-qualified to avoid collision with com.fs.starfarer.api.impl.campaign.ids.Factions
    private static final String DRACONIS_FACTION = levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                            List<Token> params, Map<String, MemoryAPI> memoryMap) {
        Global.getSector().getMemoryWithoutUpdate().set("$XLII_stellarMirrorComplete", true);

        FactionAPI draconis = Global.getSector().getFaction(DRACONIS_FACTION);
        if (draconis != null) {
            draconis.setRelationship(Factions.HEGEMONY, -0.5f);
        }
        return true;
    }
}
