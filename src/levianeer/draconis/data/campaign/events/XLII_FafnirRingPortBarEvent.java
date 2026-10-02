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
 * Bar event: a Ring-Port broker (ex-First Fleet veteran) offers the player a weapons
 * delivery job. Accepting the job grants Ring-Port contractor status and transit
 * credentials for the pirate jump point into Fafnir.
 * <p>
 * Fires at pirate or independent markets. One-shot: sets
 * {@code $fafnirRingPortQuestActive} on accept, granting pirate jump point credentials.
 * Delivery at Ring-Port Station (inside Fafnir) is a post-entry acknowledgement handled
 * by rules.csv's "# Ring-Port Delivery" section.
 * <p>
 * Same split as {@code XLII_FafnirTTBarEvent}: gating and the initial hook option stay
 * Java (guaranteed-visibility and the {@code this}-as-option-data requirement respectively);
 * the branching Q&amp;A, accept and decline flow lives in rules.csv on {@code XLII_rpBar*}
 * triggers, painted in-place via {@link FireBest#fire} - no dismiss, no reopen.
 */
public class XLII_FafnirRingPortBarEvent extends BaseBarEvent {

    @Override
    public boolean isAlwaysShow() {
        return true;
    }

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        if (market == null || market.isHidden()) return false;
        if ("XLII_draconis".equals(market.getFactionId())) return false;
        if (market.getStarSystem() != null && "Fafnir".equals(market.getStarSystem().getBaseName())) return false;
        String factionId = market.getFactionId();
        if (!Factions.PIRATES.equals(factionId) && !Factions.INDEPENDENT.equals(factionId)) {
            return false;
        }
        return XLII_FafnirRingPortBarEventCreator.isTriggerConditionMet();
    }

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
        FireBest.fire(null, dialog, memoryMap, "XLII_rpBarOpen");
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        String key = (String) optionData;
        if ("barLeave".equals(key)) {
            done = true;
            return;
        }
        FireBest.fire(null, dialog, memoryMap, "XLII_rpBar_" + key);
    }
}
