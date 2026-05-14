package levianeer.draconis.data.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.characters.XLII_Characters;
import levianeer.draconis.data.campaign.intel.events.crisis.core.DraconisAIOTracker;
import levianeer.draconis.data.campaign.intel.events.crisis.deal.DraconisAIOPaymentDealIntel;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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
 * Two dialog states depending on deal status:
 *   - No deal active: offer to set up the arrangement (monthly expense)
 *   - Deal active: check-in dialog with option to terminate
 */
public class DraconisAIOPaymentBarEvent extends BaseBarEvent {

    private static final Logger log = Global.getLogger(DraconisAIOPaymentBarEvent.class);

    private enum OptionId {
        INIT, OFFER, RETURN_OFFER, OFFER_CONFIRM, CHECK_IN, CANCEL_CONFIRM, LEAVE,
        QUESTIONS_MENU, Q_WHO_ARE_YOU, Q_WHAT_HAPPENS, Q_WHAT_DOES_OFFICE_WANT, Q_WHY_CORES,
        THREATEN, THREATEN_BACK_DOWN, THREATEN_ESCALATE, THREATEN_ESCALATE_FINAL
    }

    // ==================== Strings ====================

    private static final String MEM_KEY_PREVIOUSLY_DECLINED = "$dda_aio_bar_declined";

    private static final String BAR_SCENE_ENTRANCE =
            "A man at the corner table has been there since before you arrived. "
            + "His chair faces the entrance and the back wall both - a small geometrical fact that takes a moment to read. "
            + "You almost don't notice him, and then you understand that was the point. He gives you a nod.";

    private static final String BAR_OPTION_APPROACH = "Approach the man in the corner";

    private static final String BAR_RETURN_SCENE =
            "He's still at the corner table. He sees you before you've cleared the entrance, "
            + "and his expression doesn't change.";

    private static final String BAR_OPTION_APPROACH_RETURN = "Approach the man in the corner";

    private static final String BAR_RETURN_PARA1 =
            "He doesn't stand. He just gives a slight nod.";

    private static final String BAR_RETURN_PARA2 = "\"Changed your mind?\"";

    private static final String BAR_OFFER_PARA1 =
            "He doesn't speak immediately. You get the impression he's finishing an inventory - "
            + "exits, occupied tables, the distance between you and the nearest occupied chair. "
            + "When he's satisfied, he looks at you. He doesn't offer a hand.";

    /** %s = natural-language duration (e.g. "Fourteen months"). Use String.format at call site. */
    private static final String BAR_OFFER_PARA2_FMT =
            "\"%s.\" He says it without preamble. "
            + "\"That's how long the Office has been building your file. You're careful - for what you are. We noticed the care.\"";

    /** %s = natural-language core description (e.g. "two alpha-class, one beta-class"). Use String.format at call site. */
    private static final String BAR_OFFER_PARA3_FMT =
            "\"You're running %s cores. Output projections that don't reconcile without them.\" "
            + "A brief pause. "
            + "Your operation is very readable, from the right angle.\"";

    /** Used in place of BAR_OFFER_PARA3_FMT when total core count >= 10. %s = core description. */
    private static final String BAR_OFFER_PARA3_MANY_FMT =
            "\"You're running %s cores.\" He sets his drink down. \"More than what's even mentioned in my brief.\"";

    private static final String BAR_OFFER_PARA3_MANY_CONT =
            "\"Output projections that don't reconcile without them. "
            + "He looks at you with the attention of a man revising an estimate. "
            + "\"Your operation is considerably more interesting than my briefing suggested.\"";

    private static final String BAR_OFFER_PARA4 = "He waits a moment.";

    private static final String BAR_OFFER_PARA5 =
            "\"The Office has been patient. "
            + "We watch until we understand the shape of a problem. We understand yours.\"";

    /** %s = formatted credit amount (Misc.getWithDGS). Use String.format at call site. */
    private static final String BAR_OFFER_PARA6_FMT =
            "\"What you're using draws attention the Alliance cannot afford. "
            + "We maintain relationships that depend on this corner of the Sector staying unremarkable. "
            + "Your operations compromise those relationships.\" A pause. "
            + "\"That creates a problem - one that, specifically, %s credits monthly, resolves.\"";

    private static final String BAR_OFFER_PARA7 =
            "He glances at his drink. \"A commission is available, if you'd prefer a structural solution to an accounting one. "
            + "The Office prefers operators inside the framework rather than adjacent to it. But in my experience, people find our arrangement preferable.\"";

    private static final String BAR_OPTION_QUESTIONS  = "I have some questions first.";
    private static final String BAR_OPTION_AGREE      = "Agreed. Set up the arrangement.";
    private static final String BAR_OPTION_THREATEN   = "I could make this very uncomfortable for you.";
    private static final String BAR_OPTION_LEAVE      = "Not interested.";

    private static final String BAR_CONFIRM_PARA1 =
            "He makes no acknowledgment. The conversation is already filed.";

    /** %s = formatted credit amount. Use String.format at call site. */
    private static final String BAR_CONFIRM_PARA2_FMT = "\"%s credits, monthly. The Office will not contact you directly.\"";

    private static final String BAR_CONFIRM_PARA3 = "He looks past you, already watching the door.";

    private static final String BAR_CONFIRM_PARA4 = "\"Return here when you want it to stop.\"";

    /** Reused in confirmOffer, showThreatEscalateResponse, and confirmCancellation. */
    private static final String BAR_OPTION_CONFIRM_LEAVE = "Leave";

    private static final String BAR_CHECKIN_PARA1 =
            "He gives you a brief look - not a greeting, an inventory.";

    private static final String BAR_CHECKIN_PARA2 = "\"Still running. The Office remains incurious.\"";

    /** %s = formatted credit amount. Use String.format at call site. */
    private static final String BAR_CHECKIN_PARA3_FMT = "\"%s credits monthly. Same terms.\"";

    private static final String BAR_OPTION_KEEP         = "Keep the arrangement running.";
    private static final String BAR_OPTION_TERMINATE    = "Terminate the arrangement.";

    private static final String BAR_QUESTIONS_PROMPT =
            "He waits. He has the particular stillness of someone who is never actually idle.";

    private static final String BAR_OPTION_Q_WHO           = "Who are you?";
    private static final String BAR_OPTION_Q_WHAT_HAPPENS  = "What happens if I don't pay?";
    private static final String BAR_OPTION_Q_OFFICE_WANT   = "What does the Office actually want?";
    private static final String BAR_OPTION_Q_WHY_CORES     = "Why does the Office care about AI cores?";
    private static final String BAR_OPTION_NEVERMIND       = "Never mind.";

    private static final String BAR_ANS_WHO_1 =
            "\"My name isn't the relevant variable.\"";

    private static final String BAR_ANS_WHO_2 =
            "\"The Office sends this conversation when it wants something resolved before it becomes the kind of problem "
            + "that generates a different kind of follow-up. That's the accurate description of the function here.\"";

    private static final String BAR_ANS_WHAT_HAPPENS_1 = "\"The assessment cycle resumes where it paused.\"";

    private static final String BAR_ANS_WHAT_HAPPENS_2 = "He turns his glass slowly.";

    /** %s = natural-language duration (e.g. "Fourteen months"). Use String.format at call site. */
    private static final String BAR_ANS_WHAT_HAPPENS_3_FMT =
            "\"You've been inside it already. %s. The inspection fleet that diverted, the quarterly assessments that never materialised.\" "
            + "He waits - not for your response, but to let you count the incidents. "
            + "\"Those weren't coincidence.\"";

    private static final String BAR_ANS_WHAT_HAPPENS_4 =
            "\"The cycle resumes. The outcomes it produces are not accidents. Most people don't notice the difference until it's academic.\"";

    private static final String BAR_ANS_OFFICE_WANT_1 =
            "\"To not be here.\"";

    private static final String BAR_ANS_OFFICE_WANT_2 =
            "\"Specifically - for your operation to stop generating signals that require a response. "
            + "The arrangement creates that condition. Your cores stay in their housings; the Office stops noticing them. "
            + "Both parties have less work.\"";

    private static final String BAR_ANS_OFFICE_WANT_3 =
            "\"The Office doesn't want your loyalty. It wants your noise to stop.\"";

    private static final String BAR_ANS_WHY_CORES_1 =
            "\"Cores draw eyes. Hegemony compliance patrols. Luddic Path cells. "
            + "Parties whose interest in this region would be inconvenient for everyone inside the Rift.\"";

    private static final String BAR_ANS_WHY_CORES_2 = "He meets your eyes for the first time.";

    private static final String BAR_ANS_WHY_CORES_3 = "\"I won't be elaborating on the Rift side of that equation.\"";

    private static final String BAR_OPTION_CONTINUE = "Continue.";

    private static final String BAR_THREAT_PARA1 = "He sets his glass down.";
    private static final String BAR_THREAT_PARA2 =
            "\"Your file has a status. Right now it reads 'managed.' "
            + "That's why we're speaking - why the 42nd is not bloackading this backwater system.\"";
    private static final String BAR_THREAT_PARA3 = "A pause. He turns his glass.";
    private static final String BAR_THREAT_PARA4 =
            "\"When I leave, it changes. Three words, maximum. Procedural.\"";
    private static final String BAR_THREAT_PARA5 = "He picks up his drink.";
    private static final String BAR_THREAT_PARA6 = "\"The offer expires when I stand up.\"";

    private static final String BAR_OPTION_BACK_DOWN = "...Fine. Let's talk terms.";
    private static final String BAR_OPTION_ESCALATE  = "Is that a threat?";

    /** Reused in showThreatResponse and showThreatEscalateResponse. */
    private static final String BAR_OPTION_CHANCES  = "I'll take my chances.";

    private static final String BAR_ESCALATE_PARA1 = "\"No.\"";
    private static final String BAR_ESCALATE_PARA2 = "A small pause.";
    private static final String BAR_ESCALATE_PARA3 = "\"Just a warning.\"";
    private static final String BAR_ESCALATE_PARA4 = "He finishes his drink. Sets the glass down precisely and waits.";

    private static final String BAR_LEAVE_PARA   = "He watches you go. With the particular attention of someone making a note.";

    private static final String BAR_CANCEL_PARA1 = "\"Understood.\"";
    private static final String BAR_CANCEL_PARA2 = "He doesn't stand immediately. Finishes his drink. Signals for the bill.";
    private static final String BAR_CANCEL_PARA3 =
            "\"Once I leave, the assessment cycle resumes. Whatever progress it had made doesn't reset.\"";
    private static final String BAR_CANCEL_PARA4 = "A pause.";
    private static final String BAR_CANCEL_PARA5 = "\"If you want this conversation again - come find me.\"";
    private static final String BAR_CANCEL_PARA6 = "He leaves.";

    // ==================== State ====================

    private final Set<OptionId> askedQuestions = EnumSet.noneOf(OptionId.class);
    private boolean threatMade = false;
    private boolean initialOfferPresented = false;

    public DraconisAIOPaymentBarEvent() {
        log.info("DDA: AIO bar event - instance created");
    }

    // ==================== Spawn conditions ====================

    /**
     * Always-show ensures the event is sorted first in the bar option list and does not
     * count toward the bar's random event cap - so it reliably makes it into the market's
     * $BarCMD_shownEvents snapshot on the first fresh visit after the event is created.
     */
    @Override
    public boolean isAlwaysShow() {
        return false;
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

    // ==================== Dialog flow ====================

    @Override
    public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.addPromptAndOption(dialog, memoryMap);

        if (wasDeclined() && DraconisAIOPaymentDealIntel.get() == null) {
            dialog.getTextPanel().addPara(BAR_RETURN_SCENE);
            dialog.getOptionPanel().addOption(BAR_OPTION_APPROACH_RETURN, this);
        } else {
            dialog.getTextPanel().addPara(BAR_SCENE_ENTRANCE);
            dialog.getOptionPanel().addOption(BAR_OPTION_APPROACH, this);
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

        // Show Vasner's portrait for the duration of the interaction
        var kael = Global.getSector().getImportantPeople().getPerson(XLII_Characters.AIO_OPERATIVE_ID);
        if (kael != null) {
            dialog.getVisualPanel().showPersonInfo(kael, false, false);
        }

        optionSelected(null, OptionId.INIT);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (!(optionData instanceof OptionId option)) return;

        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI options = dialog.getOptionPanel();
        options.clearOptions();

        switch (option) {
            case INIT:
                if (DraconisAIOPaymentDealIntel.get() != null) {
                    optionSelected(null, OptionId.CHECK_IN);
                } else if (wasDeclined()) {
                    optionSelected(null, OptionId.RETURN_OFFER);
                } else {
                    optionSelected(null, OptionId.OFFER);
                }
                break;

            case OFFER:
                showOfferDialog(text, options);
                break;

            case RETURN_OFFER:
                showReturnOfferDialog(text, options);
                break;

            case OFFER_CONFIRM:
                confirmOffer(text, options);
                break;

            case CHECK_IN:
                showCheckInDialog(text, options);
                break;

            case CANCEL_CONFIRM:
                confirmCancellation(text, options);
                break;

            case QUESTIONS_MENU:
                showQuestionsMenu(text, options);
                break;

            case Q_WHO_ARE_YOU:
            case Q_WHAT_HAPPENS:
            case Q_WHAT_DOES_OFFICE_WANT:
            case Q_WHY_CORES:
                showQuestionResponse(option, text, options);
                break;

            case THREATEN:
                showThreatResponse(text, options);
                break;

            case THREATEN_BACK_DOWN:
                optionSelected(null, OptionId.OFFER);
                break;

            case THREATEN_ESCALATE:
                showThreatEscalateResponse(text, options);
                break;

            case LEAVE:
                text.addPara(BAR_LEAVE_PARA);
                if (initialOfferPresented && DraconisAIOPaymentDealIntel.get() == null) {
                    Global.getSector().getMemoryWithoutUpdate().set(MEM_KEY_PREVIOUSLY_DECLINED, true);
                }
                noContinue = true;
                done = true;
                break;
        }
    }

    // ==================== Dialog states ====================

    private void showOfferDialog(TextPanelAPI text, OptionPanelAPI options) {
        initialOfferPresented = true;

        float monthly = DraconisAIOPaymentDealIntel.computeCurrentMonthlyPayment();
        int[] cores = countPlayerAICores();

        String coreDesc = formatCoreDescription(cores[0], cores[1], cores[2]);
        int totalCores = cores[0] + cores[1] + cores[2];

        DraconisAIOTracker tracker = DraconisAIOTracker.get();
        int months = (tracker != null) ? tracker.getMonthsWatched() : 14;
        text.addPara(BAR_OFFER_PARA1);
        text.addPara(String.format(BAR_OFFER_PARA2_FMT, formatWatchDuration(months)));
        if (totalCores >= 10) {
            text.addPara(String.format(BAR_OFFER_PARA3_MANY_FMT, coreDesc));
            text.addPara(BAR_OFFER_PARA3_MANY_CONT);
        } else {
            text.addPara(String.format(BAR_OFFER_PARA3_FMT, coreDesc));
        }
        text.addPara(BAR_OFFER_PARA4);
        text.addPara(BAR_OFFER_PARA5);
        text.addPara(String.format(BAR_OFFER_PARA6_FMT, Misc.getWithDGS((long) monthly)));
        text.addPara(BAR_OFFER_PARA7);

        addOfferOptions(options);
    }

    private void showReturnOfferDialog(TextPanelAPI text, OptionPanelAPI options) {
        initialOfferPresented = true;

        text.addPara(BAR_RETURN_PARA1);
        text.addPara(BAR_RETURN_PARA2);

        addOfferOptions(options);
    }

    private void addOfferOptions(OptionPanelAPI options) {
        if (askedQuestions.size() < 4) {
            options.addOption(BAR_OPTION_QUESTIONS, OptionId.QUESTIONS_MENU);
        }

        options.addOption(BAR_OPTION_AGREE, OptionId.OFFER_CONFIRM);

        if (!threatMade) {
            options.addOption(BAR_OPTION_THREATEN, OptionId.THREATEN);
        }

        options.addOption(BAR_OPTION_LEAVE, OptionId.LEAVE);
    }

    private void confirmOffer(TextPanelAPI text, OptionPanelAPI options) {
        new DraconisAIOPaymentDealIntel();
        Global.getSector().getMemoryWithoutUpdate().unset(MEM_KEY_PREVIOUSLY_DECLINED);
        float monthly = DraconisAIOPaymentDealIntel.computeCurrentMonthlyPayment();

        text.addPara(BAR_CONFIRM_PARA1);
        text.addPara(String.format(BAR_CONFIRM_PARA2_FMT, Misc.getWithDGS((long) monthly)));
        text.addPara(BAR_CONFIRM_PARA3);
        text.addPara(BAR_CONFIRM_PARA4);

        // Reveal Vasner in the Ring-Port comm directory so the player can reach him
        Global.getSector().getMemoryWithoutUpdate().set("$XLII_aio_operative_revealed", true);
        XLII_Characters.revealAIOOperative();

        log.info("DDA: AIO payment deal created - monthly rate=" + monthly);
        clearBarSnapshotsForPlayerMarkets();

        options.addOption(BAR_OPTION_CONFIRM_LEAVE, OptionId.LEAVE);
    }

    private void showCheckInDialog(TextPanelAPI text, OptionPanelAPI options) {
        DraconisAIOPaymentDealIntel deal = DraconisAIOPaymentDealIntel.get();
        float monthly = DraconisAIOPaymentDealIntel.computeCurrentMonthlyPayment();

        text.addPara(BAR_CHECKIN_PARA1);
        text.addPara(BAR_CHECKIN_PARA2);
        text.addPara(String.format(BAR_CHECKIN_PARA3_FMT, Misc.getWithDGS((long) monthly)));

        options.addOption(BAR_OPTION_KEEP, OptionId.LEAVE);
        options.addOption(BAR_OPTION_TERMINATE, OptionId.CANCEL_CONFIRM);
    }

    private void showQuestionsMenu(TextPanelAPI text, OptionPanelAPI options) {
        text.addPara(BAR_QUESTIONS_PROMPT);

        if (!askedQuestions.contains(OptionId.Q_WHO_ARE_YOU))
            options.addOption(BAR_OPTION_Q_WHO, OptionId.Q_WHO_ARE_YOU);
        if (!askedQuestions.contains(OptionId.Q_WHAT_HAPPENS))
            options.addOption(BAR_OPTION_Q_WHAT_HAPPENS, OptionId.Q_WHAT_HAPPENS);
        if (!askedQuestions.contains(OptionId.Q_WHAT_DOES_OFFICE_WANT))
            options.addOption(BAR_OPTION_Q_OFFICE_WANT, OptionId.Q_WHAT_DOES_OFFICE_WANT);
        if (!askedQuestions.contains(OptionId.Q_WHY_CORES))
            options.addOption(BAR_OPTION_Q_WHY_CORES, OptionId.Q_WHY_CORES);

        options.addOption(BAR_OPTION_NEVERMIND, OptionId.OFFER);
    }

    private void showQuestionResponse(OptionId question, TextPanelAPI text, OptionPanelAPI options) {
        askedQuestions.add(question);

        switch (question) {
            case Q_WHO_ARE_YOU:
                text.addPara(BAR_ANS_WHO_1);
                text.addPara(BAR_ANS_WHO_2);
                break;
            case Q_WHAT_HAPPENS:
                text.addPara(BAR_ANS_WHAT_HAPPENS_1);
                text.addPara(BAR_ANS_WHAT_HAPPENS_2);
                DraconisAIOTracker whatHappensTracker = DraconisAIOTracker.get();
                int whatHappensMonths = (whatHappensTracker != null) ? whatHappensTracker.getMonthsWatched() : 14;
                text.addPara(String.format(BAR_ANS_WHAT_HAPPENS_3_FMT, formatWatchDuration(whatHappensMonths)));
                text.addPara(BAR_ANS_WHAT_HAPPENS_4);
                break;
            case Q_WHAT_DOES_OFFICE_WANT:
                text.addPara(BAR_ANS_OFFICE_WANT_1);
                text.addPara(BAR_ANS_OFFICE_WANT_2);
                text.addPara(BAR_ANS_OFFICE_WANT_3);
                break;
            case Q_WHY_CORES:
                text.addPara(BAR_ANS_WHY_CORES_1);
                text.addPara(BAR_ANS_WHY_CORES_2);
                text.addPara(BAR_ANS_WHY_CORES_3);
                break;
        }

        if (askedQuestions.size() < 4) {
            options.addOption(BAR_OPTION_CONTINUE, OptionId.QUESTIONS_MENU);
        } else {
            options.addOption(BAR_OPTION_CONTINUE, OptionId.OFFER);
        }
    }

    private void showThreatResponse(TextPanelAPI text, OptionPanelAPI options) {
        threatMade = true;

        text.addPara(BAR_THREAT_PARA1);
        text.addPara(BAR_THREAT_PARA2);
        text.addPara(BAR_THREAT_PARA3);
        text.addPara(BAR_THREAT_PARA4);
        text.addPara(BAR_THREAT_PARA5);
        text.addPara(BAR_THREAT_PARA6);

        options.addOption(BAR_OPTION_BACK_DOWN, OptionId.THREATEN_BACK_DOWN);
        options.addOption(BAR_OPTION_ESCALATE, OptionId.THREATEN_ESCALATE);
        options.addOption(BAR_OPTION_CHANCES, OptionId.LEAVE);
    }

    private void showThreatEscalateResponse(TextPanelAPI text, OptionPanelAPI options) {
        text.addPara(BAR_ESCALATE_PARA1);
        text.addPara(BAR_ESCALATE_PARA2);
        text.addPara(BAR_ESCALATE_PARA3);
        text.addPara(BAR_ESCALATE_PARA4);

        options.addOption(BAR_OPTION_AGREE, OptionId.OFFER_CONFIRM);
        options.addOption(BAR_OPTION_CHANCES, OptionId.LEAVE);
    }

    // ==================== Helpers ====================

    /** Returns {alphaCount, betaCount, gammaCount} across all player-owned markets (industries + administrator). */
    private int[] countPlayerAICores() {
        int alpha = 0, beta = 0, gamma = 0;
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!Factions.PLAYER.equals(m.getFactionId())) continue;
            if (m.getAdmin() != null && m.getAdmin().getAICoreId() != null) alpha++;
            for (Industry industry : m.getIndustries()) {
                if (industry == null) continue;
                String coreId = industry.getAICoreId();
                if (coreId == null || coreId.isEmpty()) continue;
                if (Commodities.ALPHA_CORE.equals(coreId)) alpha++;
                else if (Commodities.BETA_CORE.equals(coreId)) beta++;
                else gamma++;
            }
        }
        return new int[]{alpha, beta, gamma};
    }

    /**
     * Builds a natural-language list of installed core types, e.g. "two alpha-class, one beta-class".
     * Returns "AI cores" as a fallback if all counts are zero.
     */
    private String formatCoreDescription(int alpha, int beta, int gamma) {
        List<String> parts = new ArrayList<>();
        if (alpha > 0) parts.add(toWord(alpha) + " alpha-class");
        if (beta  > 0) parts.add(toWord(beta)  + " beta-class");
        if (gamma > 0) parts.add(toWord(gamma) + " gamma-class");
        if (parts.isEmpty()) return "AI cores";
        if (parts.size() == 1) return parts.get(0);
        if (parts.size() == 2) return parts.get(0) + " and " + parts.get(1);
        return parts.get(0) + ", " + parts.get(1) + ", and " + parts.get(2);
    }

    private static String formatWatchDuration(int months) {
        if (months <= 1) return "One month";
        String word = months < 10 ? capitalize(toWord(months)) : String.valueOf(months);
        return word + " months";
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String toWord(int n) {
        return switch (n) {
            case 1 -> "one";
            case 2 -> "two";
            case 3 -> "three";
            case 4 -> "four";
            case 5 -> "five";
            case 6 -> "six";
            case 7 -> "seven";
            case 8 -> "eight";
            case 9 -> "nine";
            default -> String.valueOf(n);
        };
    }

    /** Clears the bar event snapshot on all player markets so the contact can appear on the next visit. */
    private static void clearBarSnapshotsForPlayerMarkets() {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (Factions.PLAYER.equals(market.getFactionId())) {
                market.getMemoryWithoutUpdate().unset("$BarCMD_shownEvents");
            }
        }
    }

    private void confirmCancellation(TextPanelAPI text, OptionPanelAPI options) {
        DraconisAIOPaymentDealIntel deal = DraconisAIOPaymentDealIntel.get();
        if (deal != null) {
            deal.endImmediately();
            log.info("DDA: AIO payment deal terminated by player");
        }

        text.addPara(BAR_CANCEL_PARA1);
        text.addPara(BAR_CANCEL_PARA2);
        text.addPara(BAR_CANCEL_PARA3);
        text.addPara(BAR_CANCEL_PARA4);
        text.addPara(BAR_CANCEL_PARA5);
        text.addPara(BAR_CANCEL_PARA6);

        clearBarSnapshotsForPlayerMarkets();
        options.addOption(BAR_OPTION_CONFIRM_LEAVE, OptionId.LEAVE);
    }
}
