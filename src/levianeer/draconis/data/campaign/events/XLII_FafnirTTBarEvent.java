package levianeer.draconis.data.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent;
import levianeer.draconis.data.campaign.intel.fafnir.FafnirAccessStrings;
import levianeer.draconis.data.campaign.intel.fafnir.FafnirAccessMissionIntel;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Bar event: a Tri-Tachyon operations liaison offers the player a courier job to Kori.
 * Completing the job grants access to the Fafnir system via TT transit credentials.
 * <p>
 * Fires once at any TT market when the player has Welcoming rep or a TT commission.
 * One-shot: sets {@code $fafnirTTQuestActive} on accept and does not recur.
 */
public class XLII_FafnirTTBarEvent extends BaseBarEvent {

    private enum OptionId {
        INIT,
        ASK_WHO,
        ASK_WHAT,
        ACCEPT,
        DECLINE
    }

    private final Set<OptionId> askedQuestions = EnumSet.noneOf(OptionId.class);

    @Override
    public boolean isAlwaysShow() {
        return true;
    }

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        if (market == null || market.isHidden()) return false;
        if (!Factions.TRITACHYON.equals(market.getFactionId())) return false;
        return XLII_FafnirTTBarEventCreator.isTriggerConditionMet();
    }

    // -------------------------------------------------------------------------
    // Dialog flow
    // -------------------------------------------------------------------------

    @Override
    public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.addPromptAndOption(dialog, memoryMap);
        dialog.getTextPanel().addPara(FafnirAccessStrings.TT_BAR_SCENE);
        dialog.getOptionPanel().addOption(FafnirAccessStrings.TT_BAR_OPTION_APPROACH, this);
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

        TextPanelAPI text    = dialog.getTextPanel();
        OptionPanelAPI options = dialog.getOptionPanel();
        options.clearOptions();

        switch (option) {
            case INIT:
                showOpening(text, options);
                break;

            case ASK_WHO:
                askedQuestions.add(OptionId.ASK_WHO);
                text.addPara(FafnirAccessStrings.TT_ASK_WHO_PARA1);
                text.addPara(FafnirAccessStrings.TT_ASK_WHO_PARA2);
                text.addPara(FafnirAccessStrings.TT_ASK_WHO_PARA3);
                addMainOptions(options);
                break;

            case ASK_WHAT:
                askedQuestions.add(OptionId.ASK_WHAT);
                text.addPara(FafnirAccessStrings.TT_ASK_WHAT_PARA1);
                text.addPara(FafnirAccessStrings.TT_ASK_WHAT_PARA2);
                text.addPara(FafnirAccessStrings.TT_ASK_WHAT_PARA3);
                addMainOptions(options);
                break;

            case ACCEPT:
                text.addPara(FafnirAccessStrings.TT_ACCEPT_PARA1);
                Global.getSector().getMemoryWithoutUpdate()
                        .set(FafnirAccessStrings.MEM_TT_QUEST_ACTIVE, true);
                new FafnirAccessMissionIntel(FafnirAccessStrings.PATH_TT_COURIER);
                clearBarSnapshots();
                done = true;
                options.addOption("Leave.", "leave");
                break;

            case DECLINE:
                text.addPara(FafnirAccessStrings.TT_DECLINE_PARA1);
                Global.getSector().getMemoryWithoutUpdate()
                        .set(FafnirAccessStrings.MEM_TT_DECLINED, true);
                clearBarSnapshots();
                done = true;
                options.addOption("Leave.", "leave");
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void showOpening(TextPanelAPI text, OptionPanelAPI options) {
        text.addPara(FafnirAccessStrings.TT_OPENING_PARA1);
        text.addPara(FafnirAccessStrings.TT_OPENING_PARA2);
        text.addPara(FafnirAccessStrings.TT_OPENING_PARA3);
        text.addPara(FafnirAccessStrings.TT_OPENING_PARA4);
        addMainOptions(options);
    }

    private void addMainOptions(OptionPanelAPI options) {
        if (!askedQuestions.contains(OptionId.ASK_WHO))
            options.addOption(FafnirAccessStrings.OPT_TT_ASK_WHO,  OptionId.ASK_WHO);
        if (!askedQuestions.contains(OptionId.ASK_WHAT))
            options.addOption(FafnirAccessStrings.OPT_TT_ASK_WHAT, OptionId.ASK_WHAT);
        options.addOption(FafnirAccessStrings.OPT_TT_ACCEPT,   OptionId.ACCEPT);
        options.addOption(FafnirAccessStrings.OPT_TT_DECLINE,  OptionId.DECLINE);
    }

    private static void clearBarSnapshots() {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (Factions.PLAYER.equals(market.getFactionId())) {
                market.getMemoryWithoutUpdate().unset("$BarCMD_shownEvents");
            }
        }
    }
}
