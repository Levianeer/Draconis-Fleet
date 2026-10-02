package levianeer.draconis.data.campaign.companion;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Content spine for the Korrin companion system: what he wants to say, and where it gets said.
 * <p>
 * Topics move through two lists held in Korrin's own memory:
 * <ul>
 *   <li>{@code pending} - queued for an unprompted popup</li>
 *   <li>{@code backlog} - a popup was declined, a word/bark topic is waiting, or he was not
 *       aboard to give it; surfaces as "You wanted to talk?" in his intel dialogue. Word and
 *       popup topics sit here as pickable options; a bark sits here too but is never one - see
 *       {@link #peekBarkBacklog()}</li>
 * </ul>
 * Nothing is ever lost - declining a popup only changes where the content is read.
 */
public class KorrinTopicQueue {

    private static final Logger log = Global.getLogger(KorrinTopicQueue.class);

    private static final String PENDING_KEY = "$korrin_pending";
    private static final String BACKLOG_KEY = "$korrin_backlog";

    /** Which comment is currently being played. Held on Korrin, read via XLII_Korrin topicIs. */
    private static final String CURRENT_TOPIC_KEY = "$korrinTopic";

    private static final String TOPIC_CSV = "data/config/korrin_topics.csv";
    private static final String MOD_ID = "levianeer_draconis";

    private static Map<String, Topic> topics;

    private KorrinTopicQueue() {}

    /**
     * How much of the player's attention a topic is allowed to take. See
     * {@code work/outline/korrin-reactions.md} § The delivery ladder.
     */
    public enum Tier {
        /**
         * One-way remark. Never a pickable option - it plays automatically as the opening line the
         * next time Talk is opened ({@code XLII_Korrin barkIntro}), then "Continue." drops straight
         * into the ordinary menu.
         */
        BARK,
        /** He wants to ask you something. Goes to the backlog and waits for the player. */
        WORD,
        /** Blocking dialog. Reserve it for arc beats - it is the loudest thing he can do. */
        POPUP
    }

    /** One row of korrin_topics.csv. */
    public static class Topic {
        public final String id;
        /** Shown in the backlog menu, before the conversation opens. */
        public final String label;
        /** Higher fires first. */
        public final int priority;
        public final Tier tier;
        /** Bark text. Empty for WORD and POPUP, whose lines live in rules.csv. */
        public final String text;
        /**
         * Sector memory flag whose becoming set should provoke this topic, or empty. This is how
         * vanilla quests reach him: they have no listener, so the declared flags are polled.
         */
        public final String watch;

        Topic(String id, String label, int priority, Tier tier, String text, String watch) {
            this.id = id;
            this.label = label;
            this.priority = priority;
            this.tier = tier;
            this.text = text;
            this.watch = watch;
        }
    }

    // --- Registry ----------------------------------------------------------------------------

    public static Topic getTopic(String id) {
        return getTopics().get(id);
    }

    public static boolean isKnownTopic(String id) {
        return id != null && getTopics().containsKey(id);
    }

    /** Every topic declaring a memory flag to watch. */
    public static List<Topic> getWatchers() {
        List<Topic> out = new ArrayList<>();
        for (Topic topic : getTopics().values()) {
            if (!topic.watch.isEmpty()) out.add(topic);
        }
        return out;
    }

    private static Map<String, Topic> getTopics() {
        if (topics != null) return topics;

        topics = new LinkedHashMap<>();
        try {
            JSONArray rows = Global.getSettings().getMergedSpreadsheetDataForMod("id", TOPIC_CSV, MOD_ID);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String id = row.optString("id", "").trim();
                if (id.isEmpty()) continue;

                Tier tier = tierOf(row.optString("tier", null), id);
                String text = row.optString("text", "").trim();
                if (tier == Tier.BARK && text.isEmpty()) {
                    log.warn("Draconis: Korrin topic '" + id + "' is tier bark but has no text");
                }

                topics.put(id, new Topic(
                        id,
                        row.optString("label", id),
                        row.optInt("priority", 0),
                        tier,
                        text,
                        row.optString("watch", "").trim()));
            }
            log.info("Draconis: loaded " + topics.size() + " Korrin topic(s)");
        } catch (Exception e) {
            log.error("Draconis: failed to load " + TOPIC_CSV, e);
        }
        return topics;
    }

    /** Unknown or missing tiers fall back to POPUP - the old behaviour, and the loud one. */
    private static Tier tierOf(String raw, String topicId) {
        if (raw == null || raw.trim().isEmpty()) return Tier.POPUP;
        try {
            return Tier.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Draconis: Korrin topic '" + topicId + "' has unknown tier '" + raw + "'");
            return Tier.POPUP;
        }
    }

    // --- Queueing ----------------------------------------------------------------------------

    /**
     * Queues a topic for delivery, on the channel its tier declares.
     * <p>
     * Where he is still matters on top of that: aboard he raises a thing himself, posted to
     * Ring-Port he leaves word instead. Barks are the exception - they are said in the moment or
     * not at all.
     *
     * @return true if the topic is now on a channel, or already was. False means it never will be
     *         from this call - unknown id, Korrin not yet met (or gone for good), or a bark with
     *         nobody aboard to say it. Callers that record what they have fired should record only
     *         on true.
     */
    public static boolean queue(String topicId) {
        if (!isKnownTopic(topicId)) {
            log.warn("Draconis: queue() called with unknown Korrin topic '" + topicId + "'");
            return false;
        }
        // Bug fix: this used to check only UNMET, from before State.GONE existed - so a GONE Korrin
        // could still silently file backlog content and fire a "he wants a word" toast, implying
        // he's still around when he's permanently gone. GONE is excluded explicitly, same as UNMET.
        if (KorrinCompanion.getState() == KorrinCompanion.State.UNMET
                || KorrinCompanion.isGoneForever()) {
            return false;
        }
        if (contains(PENDING_KEY, topicId) || contains(BACKLOG_KEY, topicId)) return true;

        Topic topic = getTopic(topicId);
        boolean aboard = KorrinCompanion.isAboard();

        if (topic.tier == Tier.BARK) {
            // Deliberately dropped when he is not aboard, and deliberately not deduplicated here.
            // Whether an observation is worth making at all is the caller's decision - see
            // work/outline/korrin-reactions.md § The observer layer.
            if (!aboard) return false;
            // Suppressed barks are dropped, not deferred - and reporting false here is what keeps
            // them from being recorded as fired, so the same observation is offered again later.
            if (!KorrinRateLimit.barkAllowed()) return false;

            // Filed like a word topic rather than said immediately: it plays as the opening line
            // the next time Talk is opened (XLII_Korrin barkIntro), never as a pickable option -
            // see peekBarkBacklog(). The nudge is the same one word/popup filings use.
            add(BACKLOG_KEY, topicId);
            if (KorrinRateLimit.nudgeAllowed()) {
                Global.getSector().getCampaignUI().addMessage(
                        KorrinStrings.MSG_WANTS_A_WORD, Misc.getHighlightColor());
                KorrinRateLimit.recordNudge();
            }
            KorrinRateLimit.recordBark();
            return true;
        }

        if (aboard && topic.tier == Tier.POPUP) {
            add(PENDING_KEY, topicId);
            return true;
        }

        // Filing is never rate-limited - only the nudge about it is. The topic is on the backlog
        // and the contact panel shows the count either way.
        add(BACKLOG_KEY, topicId);
        if (KorrinRateLimit.nudgeAllowed()) {
            Global.getSector().getCampaignUI().addMessage(
                    aboard ? KorrinStrings.MSG_WANTS_A_WORD : KorrinStrings.MSG_LEFT_WORD,
                    Misc.getHighlightColor());
            KorrinRateLimit.recordNudge();
        }
        return true;
    }

    /**
     * Highest-priority BARK-tier topic sitting in the backlog, or null.
     * <p>
     * Barks never become backlog options - see {@code XLII_KorrinOptions.addBacklogOptions} - so
     * this is the only way one is ever surfaced: {@code XLII_Korrin barkIntro} consumes it as the
     * Talk dialogue's opening line.
     */
    public static String peekBarkBacklog() {
        String best = null;
        int bestPriority = Integer.MIN_VALUE;
        for (String id : get(BACKLOG_KEY)) {
            Topic topic = getTopic(id);
            if (topic == null || topic.tier != Tier.BARK) continue;
            if (topic.priority > bestPriority) {
                bestPriority = topic.priority;
                best = id;
            }
        }
        return best;
    }

    /** Highest-priority pending topic, or null. */
    public static String peekPending() {
        String best = null;
        int bestPriority = Integer.MIN_VALUE;
        for (String id : get(PENDING_KEY)) {
            Topic topic = getTopic(id);
            int priority = topic == null ? 0 : topic.priority;
            if (priority > bestPriority) {
                bestPriority = priority;
                best = id;
            }
        }
        return best;
    }

    /** Pending -> backlog. The player declined, or the dialog closed without resolving. */
    public static void defer(String topicId) {
        if (!contains(PENDING_KEY, topicId)) return;
        remove(PENDING_KEY, topicId);
        add(BACKLOG_KEY, topicId);
    }

    /** Topic has been delivered - drop it from both lists. */
    public static void markDelivered(String topicId) {
        remove(PENDING_KEY, topicId);
        remove(BACKLOG_KEY, topicId);
    }

    public static List<String> getBacklog() {
        return get(BACKLOG_KEY);
    }

    public static boolean hasBacklog() {
        return !get(BACKLOG_KEY).isEmpty();
    }

    /** The comment currently being played, or null. */
    public static String getCurrentTopic() {
        PersonAPI korrin = KorrinCompanion.getKorrin();
        return korrin == null ? null : korrin.getMemoryWithoutUpdate().getString(CURRENT_TOPIC_KEY);
    }

    public static void setCurrentTopic(String topicId) {
        PersonAPI korrin = KorrinCompanion.getKorrin();
        if (korrin != null) korrin.getMemoryWithoutUpdate().set(CURRENT_TOPIC_KEY, topicId);
    }

    public static void clearCurrentTopic() {
        PersonAPI korrin = KorrinCompanion.getKorrin();
        if (korrin != null) korrin.getMemoryWithoutUpdate().unset(CURRENT_TOPIC_KEY);
    }

    public static boolean isPending(String topicId) {
        return contains(PENDING_KEY, topicId);
    }

    // --- Memory-backed string lists ----------------------------------------------------------
    // Stored comma-delimited rather than as a serialised List, so the save format stays readable
    // and no collection type is baked into old saves.

    static List<String> get(String key) {
        List<String> out = new ArrayList<>();
        PersonAPI korrin = KorrinCompanion.getKorrin();
        if (korrin == null) return out;

        String raw = korrin.getMemoryWithoutUpdate().getString(key);
        if (raw == null || raw.isEmpty()) return out;

        for (String part : raw.split(",")) {
            String id = part.trim();
            if (!id.isEmpty()) out.add(id);
        }
        return out;
    }

    private static void set(String key, List<String> ids) {
        PersonAPI korrin = KorrinCompanion.getKorrin();
        if (korrin == null) return;

        if (ids.isEmpty()) {
            korrin.getMemoryWithoutUpdate().unset(key);
        } else {
            korrin.getMemoryWithoutUpdate().set(key, String.join(",", ids));
        }
    }

    static boolean contains(String key, String id) {
        return get(key).contains(id);
    }

    static void add(String key, String id) {
        List<String> ids = get(key);
        if (ids.contains(id)) return;
        ids.add(id);
        set(key, ids);
    }

    private static void remove(String key, String id) {
        List<String> ids = get(key);
        if (ids.remove(id)) set(key, ids);
    }
}
