package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.events.XLII_RingPortAssault;

import java.util.List;
import java.util.Map;

/**
 * Condition command: returns true if the player fleet has enough marines
 * to launch the Ring-Port assault (>= XLII_RingPortAssault.MINIMUM_MARINES).
 * <p>
 * Usage in rules.csv conditions column:
 *   XLII_HasMarinesForAssault
 */
@SuppressWarnings("unused")
public class XLII_HasMarinesForAssault extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        int marines = Global.getSector().getPlayerFleet().getCargo().getMarines();
        return marines >= XLII_RingPortAssault.MINIMUM_MARINES;
    }
}
