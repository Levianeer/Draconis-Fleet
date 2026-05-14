package levianeer.draconis.data.campaign.intel.fafnir;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;

import java.util.Map;

/**
 * Fleet hail shown on first unauthorized entry into the Fafnir system.
 * Covers two scenarios distinguished by {@link EntryType}:
 * <ul>
 *   <li>{@link EntryType#BRUTE_FORCE} - player forced Rift transit at the jump point.
 *       Warns that the DDA will turn hostile in 72 hours. Rep consequence is deferred
 *       to the {@link XLII_FafnirSystemMonitor} 3-day timer.</li>
 *   <li>{@link EntryType#TRANSVERSE_JUMP} - player bypassed all jump points via
 *       Transverse Jump. DDA has no legal basis to deny entry; they log it and
 *       promise to watch. Small rep penalty is applied by the monitor before this
 *       dialog fires.</li>
 * </ul>
 * <p>
 * Pass the intercepting fleet's commander as {@code commander} to show their portrait
 * in the visual panel. If null, no portrait is shown (anonymous comms header only).
 * <p>
 * Triggered by {@link XLII_FafnirSystemMonitor}.
 */
public class XLII_FafnirUnauthorizedEntryDialog implements InteractionDialogPlugin {

    public enum EntryType { BRUTE_FORCE, TRANSVERSE_JUMP }

    private static final String OPT_OK = "ok";

    private final EntryType  type;
    private final PersonAPI  commander;
    private InteractionDialogAPI dialog;

    public XLII_FafnirUnauthorizedEntryDialog(EntryType type, PersonAPI commander) {
        this.type      = type;
        this.commander = commander;
    }

    // -------------------------------------------------------------------------
    // InteractionDialogPlugin
    // -------------------------------------------------------------------------

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        if (commander != null) {
            dialog.getVisualPanel().showPersonInfo(commander, false);
        }
        showText();
    }

    @Override
    public void optionSelected(String text, Object optionData) {
        dialog.dismiss();
    }

    @Override
    public void optionMousedOver(String text, Object optionData) {}

    @Override
    public void advance(float amount) {}

    @Override
    public void backFromEngagement(EngagementResultAPI result) {}

    @Override
    public Object getContext() { return null; }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() { return null; }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void showText() {
        var text    = dialog.getTextPanel();
        var options = dialog.getOptionPanel();

        if (type == EntryType.BRUTE_FORCE) {
            text.addPara(FafnirAccessStrings.BF_INTERCEPT_PARA1);
            text.addPara(FafnirAccessStrings.BF_INTERCEPT_PARA2);
            text.addPara(FafnirAccessStrings.BF_INTERCEPT_PARA3);
            text.addPara(FafnirAccessStrings.BF_INTERCEPT_PARA4);
            options.addOption(FafnirAccessStrings.OPT_BF_ACKNOWLEDGE, OPT_OK);
        } else {
            text.addPara(FafnirAccessStrings.TJ_INTERCEPT_PARA1);
            text.addPara(FafnirAccessStrings.TJ_INTERCEPT_PARA2);
            text.addPara(FafnirAccessStrings.TJ_INTERCEPT_PARA3);
            text.addPara(FafnirAccessStrings.TJ_INTERCEPT_PARA4);
            options.addOption(FafnirAccessStrings.OPT_TJ_ACKNOWLEDGE, OPT_OK);
        }
    }
}