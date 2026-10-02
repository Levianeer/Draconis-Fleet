package levianeer.draconis.data.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import levianeer.draconis.data.campaign.characters.XLII_Characters;
import levianeer.draconis.data.campaign.intel.events.crisis.core.DraconisAIOTracker;
import levianeer.draconis.data.campaign.intel.events.crisis.deal.DraconisAIOPaymentDealIntel;
import levianeer.draconis.data.campaign.rulecmd.XLII_ComputeAIOOfferVars;
import org.apache.log4j.Logger;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Bar event: a DDA Intelligence Office contact manages an ongoing credit arrangement
 * that suppresses AIO tracker advancement while active.
 * <p>
 * Appears at player-owned markets when the AIO Tracker is active, the player is not
 * commissioned with DDA, and the player is not hostile to DDA.
 * <p>
 * Same split as {@code XLII_FafnirTTBarEvent}: gating and the initial hook option stay Java;
 * the whole branching offer/Q&amp;A/threaten/check-in/cancel flow lives in rules.csv on
 * {@code XLII_aioBar*} triggers, painted in-place via {@link FireBest#fire} - no dismiss, no
 * reopen. {@code optionSelected} is a generic dispatcher: every option id maps directly to a
 * same-named trigger (e.g. clicking "qWhoAreYou" fires {@code XLII_aioBar_qWhoAreYou}); the
 * branching itself (which questions remain, many-cores vs few-cores phrasing, etc.) lives
 * entirely in rules.csv conditions, not in this dispatcher. The one shared id used from many
 * different screens is {@code leave} - the universal one-line farewell + close, matching how
 * the original's {@code OptionId.LEAVE} was reached from half a dozen different buttons.
 * <p>
 * Dynamic values ($aio_monthly, $aio_coreDesc, $aio_totalCores, $aio_months) are computed by
 * {@link XLII_ComputeAIOOfferVars} - called directly (not via rules.csv) immediately before
 * firing any trigger whose text depends on them, since a trigger's own conditions are scored
 * before its script runs (see SKILL.md's "Execution Order" section) - the "many cores" text
 * variant's condition on $aio_totalCores would otherwise see a stale/unset value.
 * <p>
 * This event instance is created once and reused for every future bar visit (see
 * {@code XLII_MissionBarEventWatchdog}), unlike the one-shot dialogs (Monroe, Longsight)
 * that get a fresh instance per conversation. Any state meant to persist across visits -
 * which question(s) have been asked, whether the player has threatened him - must therefore
 * live in {@code $global.}-scoped rules.csv memory, not session-scoped (0-day-expiry) memory;
 * the latter would silently reset every time the conversation reopens, letting the same
 * question be asked again on a later visit.
 */
public class DraconisAIOPaymentBarEvent extends BaseBarEvent {

    private static final Logger log = Global.getLogger(DraconisAIOPaymentBarEvent.class);

    private static final String MEM_KEY_PREVIOUSLY_DECLINED = "$dda_aio_bar_declined";

    /**
     * Keys whose destination trigger's text depends on freshly-computed offer vars.
     * Deliberately excludes "offerConfirm" - that beat creates the deal first and only then
     * needs $aio_monthly to reflect it, so its own row's script recomputes internally instead
     * of relying on this pre-fire hook (which would run before the deal exists).
     */
    private static final Set<String> NEEDS_COMPUTE = Set.of("offer", "checkIn", "qWhatHappens");

    private boolean initialOfferPresented = false;

    public DraconisAIOPaymentBarEvent() {
        log.info("DDA: AIO bar event - instance created");
    }

    /**
     * Always-show ensures the event is sorted first in the bar option list and does not
     * count toward the bar's random event cap - so it reliably makes it into the market's
     * $BarCMD_shownEvents snapshot on the first fresh visit after the event is created.
     */
    @Override
    public boolean isAlwaysShow() {
        return true;
    }

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        log.debug("DDA: AIO bar event - shouldShowAtMarket called for "
                + (market != null ? market.getName() : "null")
                + " faction=" + (market != null ? market.getFactionId() : "null"));
        if (market == null || market.isHidden()) return false;
        if (!Factions.PLAYER.equals(market.getFactionId())) return false;

        DraconisAIOTracker tracker = DraconisAIOTracker.get();
        if (tracker == null) {
            log.info("DDA: AIO bar event - skipping, tracker not active");
            return false;
        }
        if (tracker.isCommissioned()) {
            log.info("DDA: AIO bar event - skipping, player is commissioned");
            return false;
        }
        if (Global.getSector().getPlayerFaction().isHostileTo(DRACONIS)) {
            log.info("DDA: AIO bar event - skipping, player is hostile to DDA");
            return false;
        }

        log.info("DDA: AIO bar event - shouldShowAtMarket=true for " + market.getName()
                + " (deal active=" + (DraconisAIOPaymentDealIntel.get() != null) + ")");
        return true;
    }

    @Override
    public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.addPromptAndOption(dialog, memoryMap);

        if (wasDeclined() && DraconisAIOPaymentDealIntel.get() == null) {
            dialog.getTextPanel().addPara(
                    "He's still at the corner table. He sees you before you've cleared the entrance, "
                    + "and his expression doesn't change.");
            dialog.getOptionPanel().addOption("Approach the man in the corner", this);
        } else {
            dialog.getTextPanel().addPara(
                    "A man at the corner table has been there since before you arrived. "
                    + "His chair faces the entrance and the back wall both - a small geometrical fact that takes a moment to read. "
                    + "You almost don't notice him, and then you understand that was the point. He gives you a nod.");
            dialog.getOptionPanel().addOption("Approach the man in the corner", this);
        }
        dialog.setOptionColor(this, Global.getSettings().getColor("buttonShortcut"));
    }

    private boolean wasDeclined() {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(MEM_KEY_PREVIOUSLY_DECLINED);
    }

    @Override
    public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.init(dialog, memoryMap);
        done = false;

        PersonAPI ancker = Global.getSector().getImportantPeople().getPerson(XLII_Characters.ANCKER_ID);
        if (ancker != null) {
            dialog.getVisualPanel().showPersonInfo(ancker, false);
        }

        String trigger;
        if (DraconisAIOPaymentDealIntel.get() != null) {
            trigger = "checkIn";
        } else if (wasDeclined()) {
            trigger = "returnOffer";
            initialOfferPresented = true;
        } else {
            trigger = "offer";
            initialOfferPresented = true;
        }
        fire(trigger);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        String key = (String) optionData;

        if ("leave".equals(key) && initialOfferPresented && DraconisAIOPaymentDealIntel.get() == null) {
            Global.getSector().getMemoryWithoutUpdate().set(MEM_KEY_PREVIOUSLY_DECLINED, true);
        }

        fire(key);

        if ("leave".equals(key)) {
            done = true;
        }
    }

    private void fire(String key) {
        if (NEEDS_COMPUTE.contains(key)) {
            new XLII_ComputeAIOOfferVars().execute(null, dialog, Collections.emptyList(), memoryMap);
        }
        FireBest.fire(null, dialog, memoryMap, "XLII_aioBar_" + key);
    }
}
