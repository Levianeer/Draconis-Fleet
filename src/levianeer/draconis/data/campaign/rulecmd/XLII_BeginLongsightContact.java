package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

/**
 * Action command: swaps the current dialog over to the Longsight Contact scene in place (rules.csv,
 * {@code # [UPLINK TO GOD] Longsight Contact}, entry trigger {@code XLII_longsight_contact_open}).
 * Fired from a MarketPostDock row at Kori (rules.csv, gated on XLII_IsAtKori +
 * $global.XLII_koriInfiltrationOffered) - the player is already inside the dock dialog when this
 * fires, so an in-place dialog.setPlugin() swap is used rather than the dismiss-then-reopen dance
 * this used to do when it fired off the end of a comms call with August instead.
 * <p>
 * Used to swap in the hand-rolled XLII_LongsightContactDialog; that scene has since been ported to
 * rules.csv in full (same shape as Monroe Vetting/the interception), so this now hands off to a
 * bare RuleBasedInteractionDialogPluginImpl like XLII_KoriStrike's own interception hand-off does.
 * <p>
 * Usage in rules.csv actions column:
 *   XLII_BeginLongsightContact
 */
@SuppressWarnings("unused")
public class XLII_BeginLongsightContact extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        RuleBasedInteractionDialogPluginImpl contact =
                new RuleBasedInteractionDialogPluginImpl("XLII_longsight_contact_open");
        dialog.setPlugin(contact);
        contact.init(dialog);
        return true;
    }
}
