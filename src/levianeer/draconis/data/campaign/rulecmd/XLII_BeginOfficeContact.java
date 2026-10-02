package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.intel.blind_eye.XLII_OfficeContactMonitor;

import java.util.List;
import java.util.Map;

/**
 * Action command: registers {@link XLII_OfficeContactMonitor} immediately, mid-session, rather
 * than waiting for the next game load to pick it up via {@code XLII_ModPlugin.onGameLoad()}.
 * Idempotent - {@link XLII_OfficeContactMonitor#shouldRegister()} guards against a duplicate.
 * <p>
 * Called from {@code XLII_nanoforge_discuss_eligible} right after
 * {@code $global.XLII_officeReferralPending} is set.
 * <p>
 * Usage in rules.csv script column:
 *   XLII_BeginOfficeContact
 */
@SuppressWarnings("unused")
public class XLII_BeginOfficeContact extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (XLII_OfficeContactMonitor.shouldRegister()) {
            Global.getSector().addScript(new XLII_OfficeContactMonitor());
        }
        return true;
    }
}
