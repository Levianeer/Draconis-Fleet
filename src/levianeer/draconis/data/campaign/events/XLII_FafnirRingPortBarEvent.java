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
 * Bar event: a Ring-Port broker (ex-First Fleet veteran) offers the player a weapons
 * delivery job. Accepting the job grants Ring-Port contractor status and transit
 * credentials for the pirate jump point into Fafnir.
 * <p>
 * Fires at pirate or independent markets. One-shot: sets
 * {@code $fafnirRingPortQuestActive} on accept, granting pirate jump point credentials.
 * Delivery at Ring-Port Station (inside Fafnir) is a post-entry acknowledgement handled
 * by {@code XLII_FafnirRingPortDeliveryDialogPlugin}.
 */
public class XLII_FafnirRingPortBarEvent extends BaseBarEvent {

    private enum OptionId {
        INIT,
        ASK_WHERE,
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
        String factionId = market.getFactionId();
        if (!Factions.PIRATES.equals(factionId) && !Factions.INDEPENDENT.equals(factionId)) {
            return false;
        }
        return XLII_FafnirRingPortBarEventCreator.isTriggerConditionMet();
    }

    // -------------------------------------------------------------------------
    // Dialog flow
    // -------------------------------------------------------------------------

    @Override
    public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.addPromptAndOption(dialog, memoryMap);
        dialog.getTextPanel().addPara(FafnirAccessStrings.RP_BAR_SCENE);
        dialog.getOptionPanel().addOption(FafnirAccessStrings.RP_BAR_OPTION_APPROACH, this);
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
                showOpening(text, options);
                break;

            case ASK_WHERE:
                askedQuestions.add(OptionId.ASK_WHERE);
                text.addPara(FafnirAccessStrings.RP_ASK_WHERE_PARA1);
                text.addPara(FafnirAccessStrings.RP_ASK_WHERE_PARA2);
                text.addPara(FafnirAccessStrings.RP_ASK_WHERE_PARA3);
                addMainOptions(options);
                break;

            case ASK_WHAT:
                askedQuestions.add(OptionId.ASK_WHAT);
                text.addPara(FafnirAccessStrings.RP_ASK_WHAT_PARA1);
                text.addPara(FafnirAccessStrings.RP_ASK_WHAT_PARA2);
                text.addPara(FafnirAccessStrings.RP_ASK_WHAT_PARA3);
                addMainOptions(options);
                break;

            case ACCEPT:
                text.addPara(FafnirAccessStrings.RP_ACCEPT_PARA1);
                Global.getSector().getMemoryWithoutUpdate()
                        .set(FafnirAccessStrings.MEM_RP_QUEST_ACTIVE, true);
                new FafnirAccessMissionIntel(FafnirAccessStrings.PATH_RING_PORT);
                clearBarSnapshots();
                done = true;
                options.addOption("Leave.", "leave");
                break;

            case DECLINE:
                text.addPara(FafnirAccessStrings.RP_DECLINE_PARA1);
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
        text.addPara(FafnirAccessStrings.RP_OPENING_PARA1);
        text.addPara(FafnirAccessStrings.RP_OPENING_PARA2);
        text.addPara(FafnirAccessStrings.RP_OPENING_PARA3);
        text.addPara(FafnirAccessStrings.RP_OPENING_PARA4);
        text.addPara(FafnirAccessStrings.RP_OPENING_PARA5);
        addMainOptions(options);
    }

    private void addMainOptions(OptionPanelAPI options) {
        if (!askedQuestions.contains(OptionId.ASK_WHERE))
            options.addOption(FafnirAccessStrings.OPT_RP_ASK_WHERE, OptionId.ASK_WHERE);
        if (!askedQuestions.contains(OptionId.ASK_WHAT))
            options.addOption(FafnirAccessStrings.OPT_RP_ASK_WHAT,  OptionId.ASK_WHAT);
        options.addOption(FafnirAccessStrings.OPT_RP_ACCEPT,    OptionId.ACCEPT);
        options.addOption(FafnirAccessStrings.OPT_RP_DECLINE,   OptionId.DECLINE);
    }

    private static void clearBarSnapshots() {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (Factions.PLAYER.equals(market.getFactionId())) {
                market.getMemoryWithoutUpdate().unset("$BarCMD_shownEvents");
            }
        }
    }
}
