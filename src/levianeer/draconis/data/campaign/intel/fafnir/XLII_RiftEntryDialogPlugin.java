package levianeer.draconis.data.campaign.intel.fafnir;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;

import java.util.Map;

/**
 * Fires once, the first time the player fleet crosses into the Rift without already holding
 * Fafnir access. The navigation officer reports what the Rift is doing to the fleet and points
 * at the two channels that sell the approach - Tri-Tachyon couriers and the Ring-Port gun runs -
 * so a new player knows the bar events exist before they burn CR looking for a way through.
 * <p>
 * Opened by {@code XLII_RiftTerrainPlugin.advance()}, which owns the one-shot gate
 * ({@link FafnirAccessStrings#MEM_RIFT_FIRST_ENTRY_DONE}). Display only - no flags, rep or
 * rewards are applied here.
 */
public class XLII_RiftEntryDialogPlugin implements InteractionDialogPlugin {

    private static final String OPT_NOTED = "noted";

    private InteractionDialogAPI dialog;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;

        TextPanelAPI text = dialog.getTextPanel();
        text.addPara(FafnirAccessStrings.RIFT_ENTRY_PARA1);
        text.addPara(FafnirAccessStrings.RIFT_ENTRY_PARA2);
        text.addPara(FafnirAccessStrings.RIFT_ENTRY_PARA3);
        text.addPara(FafnirAccessStrings.RIFT_ENTRY_PARA4);
        text.addPara(FafnirAccessStrings.RIFT_ENTRY_PARA5);

        OptionPanelAPI options = dialog.getOptionPanel();
        options.addOption(FafnirAccessStrings.OPT_RIFT_ENTRY_NOTED, OPT_NOTED);
        dialog.setOptionOnEscape(FafnirAccessStrings.OPT_RIFT_ENTRY_NOTED, OPT_NOTED);
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
}
