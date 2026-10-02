package levianeer.draconis.data.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import levianeer.draconis.data.campaign.intel.fafnir.FafnirAccessStrings;

import java.util.Map;

/**
 * Bar event: a Tri-Tachyon operations liaison offers the player a courier job to Kori.
 * Completing the job grants access to the Fafnir system via TT transit credentials.
 * <p>
 * Fires once at any TT market when the player has Welcoming rep or a TT commission.
 * One-shot: sets {@code $fafnirTTQuestActive} on accept and does not recur.
 * <p>
 * {@code isAlwaysShow}/{@code shouldShowAtMarket} stay Java (guaranteed-visibility bar-event
 * gating has no rules.csv equivalent - see {@code XLII_MissionBarEventWatchdog}'s own reasoning
 * for bypassing {@code BarEventManager} the same way). The prompt/approach step also stays Java,
 * since the hook option's data must be {@code this} for the bar to wrap into this event at all.
 * Everything past that point - the branching Q&amp;A, accept and decline flow - lives in rules.csv
 * on {@code XLII_ttBar*} triggers, painted directly into this same dialog window via
 * {@link FireBest#fire}, mirroring vanilla's own {@code HubMissionBarEventWrapper}. No dismiss,
 * no reopen, no jarring transition; {@code done = true} on "Leave" hands control back to the bar
 * exactly as it already did before this class touched rules.csv at all.
 */
public class XLII_FafnirTTBarEvent extends BaseBarEvent {

    @Override
    public boolean isAlwaysShow() {
        return true;
    }

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        if (market == null || market.isHidden()) return false;
        if ("XLII_draconis".equals(market.getFactionId())) return false;
        if (market.getStarSystem() != null && "Fafnir".equals(market.getStarSystem().getBaseName())) return false;
        if (!Factions.TRITACHYON.equals(market.getFactionId())) return false;
        return XLII_FafnirTTBarEventCreator.isTriggerConditionMet();
    }

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
        FireBest.fire(null, dialog, memoryMap, "XLII_ttBarOpen");
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        String key = (String) optionData;
        if ("barLeave".equals(key)) {
            done = true;
            return;
        }
        FireBest.fire(null, dialog, memoryMap, "XLII_ttBar_" + key);
    }
}
