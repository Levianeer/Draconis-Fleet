package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.characters.PersonAPI;
import levianeer.draconis.data.campaign.companion.KorrinCompanion;
import levianeer.draconis.data.campaign.companion.KorrinGiftRegistry;
import levianeer.draconis.data.campaign.companion.KorrinTalkMenu;
import levianeer.draconis.data.campaign.companion.KorrinTopicQueue;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Single rules.csv surface for the Korrin companion system. Mode-dispatched rather than split
 * across eight one-line commands, so adding content means editing rules.csv, not writing Java.
 * <p>
 * Modes that add dialog options live in {@link XLII_KorrinOptions} instead, and cannot be added
 * here: {@code doesCommandAddOptions()} takes no parameters, so declaring true for one mode
 * declares it for all of them, and the engine then runs every mode twice.
 *
 * <pre>
 * Conditions column:
 *   XLII_Korrin state STATIONED        true if Korrin is in the named state
 *   XLII_Korrin aboard                 true if he is with the fleet in either aboard state
 *   XLII_Korrin hasCommand             true if he has a ship under him, not just a roster slot
 *   XLII_Korrin hasBacklog             true if he has undelivered comments
 *   XLII_Korrin hasPendingBark         true if a bark is waiting to open the next Talk dialog
 *   XLII_Korrin isTopicOption          true if $option is a known topic id
 *   XLII_Korrin topicIs &lt;topicId&gt;      true if the named comment is the one being played
 *   XLII_Korrin repAtLeast &lt;level&gt;     true if his rep with the player is at least that RepLevel
 *   XLII_Korrin hasGiftable            true if any registered gift is in cargo and not yet given
 *
 * Script column:
 *   XLII_Korrin meet                   UNMET -> STATIONED
 *   XLII_Korrin comeAboard             STATIONED -> ABOARD_RESERVE
 *   XLII_Korrin takeCommand            ABOARD_RESERVE -> ABOARD_OFFICER
 *   XLII_Korrin standDown              ABOARD_OFFICER -> ABOARD_RESERVE
 *   XLII_Korrin returnToStation        ABOARD_* -> STATIONED
 *   XLII_Korrin queue &lt;topicId&gt;        queue a comment for delivery
 *   XLII_Korrin openTopic              set $korrinTopic from the selected $option
 *   XLII_Korrin topicDone              mark $korrinTopic delivered
 *   XLII_Korrin topicDefer             move the current comment to the backlog
 *   XLII_Korrin visual                 show Korrin's portrait in the dialog
 *   XLII_Korrin barkIntro              write the pending bark's text into the dialog, mark it delivered
 *   XLII_Korrin rep &lt;delta&gt;            adjust his personal reputation with the player (also used by
 *                                      the dev-only +/- menu, rules.csv `# Dev Testing`)
 *   XLII_Korrin giveGift &lt;giftId&gt;      remove the gift's item from cargo and mark it given
 *   XLII_Korrin offer &lt;id&gt; &lt;class&gt; &lt;weight&gt;   register this topic's option as drawable
 *   XLII_Korrin menu                   build the Talk menu: fire the options trigger, then prune
 *   XLII_Korrin reroll                 discard the current hand and turn the page
 * </pre>
 */
@SuppressWarnings("unused")
public class XLII_Korrin extends BaseCommandPlugin {

    private static final Logger log = Global.getLogger(XLII_Korrin.class);

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (params.isEmpty()) {
            log.warn("Draconis: XLII_Korrin called with no mode");
            return false;
        }

        String mode = params.get(0).getString(memoryMap);
        String arg = params.size() > 1 ? params.get(1).getString(memoryMap) : null;

        switch (mode) {
            // --- conditions ---
            case "state":
                return arg != null && KorrinCompanion.getState().name().equals(arg);
            case "aboard":
                return KorrinCompanion.isAboard();
            case "hasCommand":
                return KorrinCompanion.hasCommand();
            case "hasBacklog":
                return KorrinTopicQueue.hasBacklog();
            case "hasPendingBark":
                return KorrinTopicQueue.peekBarkBacklog() != null;
            case "isTopicOption":
                return KorrinTopicQueue.isKnownTopic(selectedOption(memoryMap));
            case "topicIs":
                return arg != null && arg.equals(KorrinTopicQueue.getCurrentTopic());
            case "repAtLeast":
                return repAtLeast(arg);
            case "hasGiftable":
                return KorrinGiftRegistry.hasAnyEligible();

            // --- transitions ---
            case "meet":
                KorrinCompanion.meet();
                return true;
            case "comeAboard":
                KorrinCompanion.comeAboard();
                return true;
            case "takeCommand":
                KorrinCompanion.takeCommand();
                return true;
            case "standDown":
                KorrinCompanion.standDown();
                return true;
            case "returnToStation":
                KorrinCompanion.returnToStation();
                return true;

            // --- topics ---
            case "queue":
                if (arg != null) KorrinTopicQueue.queue(arg);
                return true;
            case "visual":
                return showVisual(dialog);
            case "barkIntro":
                return barkIntro(dialog);
            case "openTopic":
                return openTopic(memoryMap);
            case "topicDone":
                return resolveTopic(true);
            case "topicDefer":
                return resolveTopic(false);
            case "rep":
                return adjustRep(arg);
            case "giveGift":
                if (arg != null) KorrinGiftRegistry.give(arg);
                return true;

            // --- talk menu ---
            case "offer":
                return offer(params, memoryMap);
            case "menu":
                KorrinTalkMenu.render(ruleId, dialog, memoryMap);
                return true;
            case "reroll":
                KorrinTalkMenu.reroll();
                return true;

            default:
                log.warn("Draconis: XLII_Korrin - unknown mode '" + mode + "'");
                return false;
        }
    }

    /**
     * Registers a Talk-menu topic as drawable: {@code offer <optionId> <class> <weight>}.
     * <p>
     * Note this does not add the option - the topic's own rules.csv row does that in the options
     * column, as it always has. This only tells the draw the option exists and what it is worth,
     * which is why it belongs on this command rather than on XLII_KorrinOptions.
     */
    private boolean offer(List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (params.size() < 4) {
            log.warn("Draconis: XLII_Korrin offer needs <optionId> <class> <weight>");
            return false;
        }

        String optionId = params.get(1).getString(memoryMap);
        String topicClass = params.get(2).getString(memoryMap);
        float weight = params.get(3).getFloat(memoryMap);

        KorrinTalkMenu.offer(optionId, topicClass, weight);
        return true;
    }

    /**
     * Adjusts Korrin's personal reputation with the player. Delta is on the -1..1 scale used by
     * {@link com.fs.starfarer.api.characters.RelationshipAPI#getRel()}, so conversational moves are
     * small - roughly 0.02 to 0.06.
     * <p>
     * Losses bottom out at NEUTRAL on purpose. Korrin is written so that a player who handles him
     * badly gets the professional version of him, never a hostile one - see
     * {@code .claude/characters/character_profile-korrin.md}.
     */
    private boolean adjustRep(String arg) {
        PersonAPI korrin = KorrinCompanion.getKorrin();
        if (korrin == null || arg == null) return false;

        float delta;
        try {
            delta = Float.parseFloat(arg);
        } catch (NumberFormatException e) {
            log.warn("Draconis: XLII_Korrin rep - invalid delta '" + arg + "'");
            return false;
        }

        RepLevel limit = delta >= 0f ? RepLevel.COOPERATIVE : RepLevel.NEUTRAL;
        korrin.getRelToPlayer().adjustRelationship(delta, limit);
        return true;
    }

    private boolean repAtLeast(String arg) {
        PersonAPI korrin = KorrinCompanion.getKorrin();
        if (korrin == null || arg == null) return false;

        try {
            return korrin.getRelToPlayer().isAtWorst(RepLevel.valueOf(arg.toUpperCase()));
        } catch (IllegalArgumentException e) {
            log.warn("Draconis: XLII_Korrin repAtLeast - unknown RepLevel '" + arg + "'");
            return false;
        }
    }

    /**
     * Shows Korrin's portrait.
     * <p>
     * Vanilla ShowPersonVisual resolves the portrait from the interaction target's active person
     * and falls back to the target fleet's commander - which, for a dialog opened on the player's
     * own fleet, is the player. Naming him outright avoids the whole question.
     */
    private boolean showVisual(InteractionDialogAPI dialog) {
        if (dialog == null) return false;

        PersonAPI korrin = KorrinCompanion.getKorrin();
        if (korrin == null) return false;

        // Minimal mode, so no faction crest is drawn. His faction follows him onto the player's
        // roster, so the panel would otherwise fly the player's flag beside his portrait - and the
        // intel entry already shows Draconis standing anyway.
        dialog.getVisualPanel().showPersonInfo(korrin, true);
        return true;
    }

    /**
     * Writes the highest-priority pending bark's text straight into the dialog's text panel -
     * the same way {@link #showVisual} writes the portrait directly rather than going through a
     * rules.csv text cell. Keeps a bark a pure data row: no rules.csv block is written per bark.
     * <p>
     * A {@code $variable} token in a text cell only substitutes engine-known tokens
     * ({@code $playerName} and its kind), not arbitrary memory keys - an earlier version of this
     * relied on that and the token was never replaced.
     * <p>
     * Marks the topic delivered immediately. A bark is a remark, not a question with a decline
     * path, so there is nothing to wait on.
     */
    private boolean barkIntro(InteractionDialogAPI dialog) {
        if (dialog == null) return false;

        String id = KorrinTopicQueue.peekBarkBacklog();
        if (id == null) return false;

        KorrinTopicQueue.Topic topic = KorrinTopicQueue.getTopic(id);
        if (topic == null) return false;

        dialog.getTextPanel().addParagraph(topic.text);
        KorrinTopicQueue.markDelivered(id);
        return true;
    }

    private boolean openTopic(Map<String, MemoryAPI> memoryMap) {
        String id = selectedOption(memoryMap);
        if (!KorrinTopicQueue.isKnownTopic(id)) return false;

        KorrinTopicQueue.setCurrentTopic(id);
        return true;
    }

    private boolean resolveTopic(boolean delivered) {
        String id = KorrinTopicQueue.getCurrentTopic();
        if (id == null) return false;

        if (delivered) {
            KorrinTopicQueue.markDelivered(id);
        } else {
            KorrinTopicQueue.defer(id);
        }
        KorrinTopicQueue.clearCurrentTopic();
        return true;
    }

    private String selectedOption(Map<String, MemoryAPI> memoryMap) {
        MemoryAPI local = memoryMap.get(MemKeys.LOCAL);
        return local == null ? null : local.getString("$option");
    }
}
