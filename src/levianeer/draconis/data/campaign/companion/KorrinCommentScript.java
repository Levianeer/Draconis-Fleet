package levianeer.draconis.data.campaign.companion;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import com.fs.starfarer.api.util.Misc;

/**
 * Drives Korrin's unprompted comments, and keeps his state in sync with the officer roster.
 * <p>
 * Guard pattern follows Nexerelin's DelayedDialogScreenScript: never fire while the UI is busy,
 * and settle for a moment first so a popup does not land on the same frame as whatever provoked it.
 */
public class KorrinCommentScript implements EveryFrameScript {

    /** rules.csv trigger for an unprompted comment. */
    public static final String COMMENT_TRIGGER = "XLII_KorrinComment";

    private static final float SETTLE_DAYS = 0.1f;

    private float settle = 0f;
    private float cooldown = 0f;
    private String lastShownTopic = null;
    private boolean sawDialogOpen = false;

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui == null) return;

        boolean busy = Global.getCurrentState() != GameState.CAMPAIGN
                || Global.getSector().isInNewGameAdvance()
                || ui.isShowingDialog();

        if (busy) {
            if (lastShownTopic != null) sawDialogOpen = true;
        } else if (lastShownTopic != null && sawDialogOpen) {
            resolveClosedPopup();
            sawDialogOpen = false;
        }

        // GONE is checked alongside UNMET (this used to only check UNMET). Popups are already
        // safe - tryShowComment() gates on isAboard(), false for GONE - but without this,
        // reconcileOfficerRoster()/pollWatches()/pollNearbyPlanets() would keep running forever
        // for a Korrin who is never coming back. Placed after the resolveClosedPopup()
        // housekeeping above so that still runs unconditionally.
        if (KorrinCompanion.getState() == KorrinCompanion.State.UNMET
                || KorrinCompanion.isGoneForever()) {
            return;
        }

        reconcileOfficerRoster();

        // Vanilla quests have no listener, so their declared flags are polled here rather than
        // in a script of their own.
        KorrinObserver.pollWatches();
        // Same reason: there is no listener for "the player got close to this planet," which is
        // the only route in for Fafnir's own worlds, already surveyed and so unable to raise
        // SurveyPlanetListener.
        KorrinObserver.pollNearbyPlanets();

        float days = Global.getSector().getClock().convertToDays(amount);
        if (cooldown > 0f) cooldown -= days;

        if (busy) {
            settle = 0f;
            return;
        }

        settle += days;
        if (settle < SETTLE_DAYS || cooldown > 0f) return;

        tryShowComment();
    }

    /**
     * The player can drop Korrin from the officer roster through the fleet screen. There is no
     * listener for that - the UI calls removeOfficer() directly - so it has to be polled.
     * Under this system that is a legitimate transition, not a desync to repair.
     */
    private void reconcileOfficerRoster() {
        KorrinCompanion.State state = KorrinCompanion.getState();
        boolean onRoster = KorrinCompanion.isOnOfficerRoster();

        if (state == KorrinCompanion.State.ABOARD_OFFICER && !onRoster) {
            // Dismissal through the fleet screen is left alone deliberately - no popup, no
            // comment. The player gets a one-line notice that he is still aboard, nothing more.
            KorrinCompanion.standDown();
            Global.getSector().getCampaignUI()
                    .addMessage(KorrinStrings.MSG_STOOD_DOWN, Misc.getHighlightColor());
        } else if (state == KorrinCompanion.State.ABOARD_RESERVE && onRoster) {
            // Added back by something outside this system (console, another mod). Follow it.
            KorrinCompanion.takeCommand();
        }
    }

    /**
     * Safety net: if a popup was shown and its topic is still pending once the dialog has closed,
     * the player left without resolving it. Treat that as a decline so the content is not lost.
     */
    private void resolveClosedPopup() {
        if (lastShownTopic == null) return;
        if (KorrinTopicQueue.isPending(lastShownTopic)) {
            KorrinTopicQueue.defer(lastShownTopic);
        }
        lastShownTopic = null;
    }

    private void tryShowComment() {
        if (!KorrinCompanion.isAboard()) return;

        String topicId = KorrinTopicQueue.peekPending();
        if (topicId == null) return;

        PersonAPI korrin = KorrinCompanion.getKorrin();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (korrin == null || fleet == null) return;

        // Set before opening, because the rules fire during showInteractionDialog and
        // XLII_Korrin topicIs has to resolve while they do.
        KorrinTopicQueue.setCurrentTopic(topicId);

        boolean shown = Global.getSector().getCampaignUI().showInteractionDialog(
                new RuleBasedInteractionDialogPluginImpl(COMMENT_TRIGGER), fleet);

        if (shown) {
            lastShownTopic = topicId;
            sawDialogOpen = false;
            cooldown = KorrinRateLimit.popupCooldownDays();
            settle = 0f;
        } else {
            // The UI refused - it is busy with something this tick's guards did not catch. Leaving
            // the topic set would make XLII_Korrin topicIs true inside unrelated dialogs, so the
            // comment could fire off the back of the Talk menu. Retry on a later tick instead.
            KorrinTopicQueue.clearCurrentTopic();
        }
    }
}
