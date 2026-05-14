package levianeer.draconis.data.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent;
import levianeer.draconis.data.campaign.characters.XLII_Characters;

import java.util.Map;

/**
 * One-time bar event: player meets AIO Operative Kael Vasner (IRON MOTH) at Ring-Port
 * after the assault. Completing the encounter reveals Vasner in the comm directory.
 */
public class XLII_AIOOperativeBarEvent extends BaseBarEvent {

    private static final String RING_PORT_MARKET_ID = "pirateStation_market";

    private enum OptionId { INIT, ACKNOWLEDGE }

    @Override
    public boolean isAlwaysShow() {
        return true;
    }

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        if (market == null || market.isHidden()) return false;
        if (!RING_PORT_MARKET_ID.equals(market.getId())) return false;
        return XLII_AIOOperativeBarEventCreator.isTriggerConditionMet();
    }

    @Override
    public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.addPromptAndOption(dialog, memoryMap);
        dialog.getTextPanel().addPara(
            "At the far end of the bar. A figure you didn't register when you came in, " +
            "which is itself worth noting. Civilian clothes, nothing that marks a " +
            "profession or a unit. They've been watching the room the way someone " +
            "watches a problem they've already solved."
        );
        dialog.getOptionPanel().addOption("Approach the plain clothed man.", this);
        dialog.setOptionColor(this, Global.getSettings().getColor("buttonShortcut"));
    }

    @Override
    public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.init(dialog, memoryMap);
        done = false;
        optionSelected(null, OptionId.INIT);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (!(optionData instanceof OptionId option)) return;

        TextPanelAPI text      = dialog.getTextPanel();
        OptionPanelAPI options = dialog.getOptionPanel();
        options.clearOptions();

        switch (option) {
            case INIT:
                text.addPara(
                    "They don't look up when you approach. When they do, the timing feels deliberate."
                );
                text.addPara(
                    "\"Commander.\" Less of a greeting - a category " +
                    "followed by a pause before the next sentence. \"You took " +
                    "Ring-Port. There were points in that operation where the outcome could have " +
                    "resolved very differently, but it didn't. Solid work.\""
                );
                text.addPara("A comm-slate appears on the table between you. He doesn't look at it.");
                text.addPara(
                    "\"Vasner.\" His eyes move across the room once, methodically. " +
                    "\"I've had an operational interest in this station for some time. You've made " +
                    "that considerably simpler to pursue. I'm in the directory now. When you require " +
                    "something that doesn't appear on standard supply manifests, contact me. And we can " +
                    "discuss what that costs.\""
                );
                options.addOption("Understood.", OptionId.ACKNOWLEDGE);
                break;

            case ACKNOWLEDGE:
                text.addPara("A nod. Minimal. He returns to his drink without ceremony.");
                Global.getSector().getMemoryWithoutUpdate()
                        .set(XLII_AIOOperativeBarEventCreator.MEM_OPERATIVE_REVEALED, true);
                XLII_Characters.revealAIOOperative();
                clearBarSnapshots();
                done = true;
                options.addOption("Leave.", "leave");
                break;
        }
    }

    private static void clearBarSnapshots() {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (Factions.PLAYER.equals(market.getFactionId())) {
                market.getMemoryWithoutUpdate().unset("$BarCMD_shownEvents");
            }
        }
    }
}