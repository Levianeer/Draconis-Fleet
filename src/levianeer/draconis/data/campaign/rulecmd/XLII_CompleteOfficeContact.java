package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;
import levianeer.draconis.data.campaign.intel.blind_eye.XLII_OfficeContactMonitor;

import java.util.List;
import java.util.Map;

/**
 * Clears assignments/tag and despawns the Office contact fleet (the interaction target).
 * Mirrors what {@code XLII_OfficeContactDialog.optionSelected()} used to do directly.
 * <p>
 * Usage in rules.csv script column: {@code XLII_CompleteOfficeContact}
 */
@SuppressWarnings("unused")
public class XLII_CompleteOfficeContact extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                            List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (!(dialog.getInteractionTarget() instanceof CampaignFleetAPI fleet)) return false;
        XLII_OfficeContactMonitor.onContactFired(fleet);
        return true;
    }
}
