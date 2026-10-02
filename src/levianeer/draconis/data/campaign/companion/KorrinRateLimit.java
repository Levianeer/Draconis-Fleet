package levianeer.draconis.data.campaign.companion;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import org.apache.log4j.Logger;
import org.json.JSONObject;

/**
 * How often Korrin is allowed to speak, per delivery tier.
 * <p>
 * The cooldown in {@link KorrinCommentScript} was sized for our own quest beats, which arrive a
 * handful of times a playthrough. Listener-driven reactions arrive on docking, on entering a
 * system, on finding a derelict - orders of magnitude more often - and a companion who remarks on
 * every one of them is the reason people mute their followers.
 * <p>
 * Design: {@code work/outline/korrin-reactions.md} § Restraint.
 */
public class KorrinRateLimit {

    private static final Logger log = Global.getLogger(KorrinRateLimit.class);

    private static final String BARK_KEY = "$korrin_last_bark";
    private static final String NUDGE_KEY = "$korrin_last_nudge";

    private static float barkCooldownDays = 5f;
    private static float nudgeCooldownDays = 0f;
    private static float popupCooldownDays = 2f;
    private static boolean loaded;

    /**
     * Whether a bark may be said now.
     * <p>
     * A suppressed bark is <b>dropped, not deferred</b> - which is the same rule barks already
     * follow when he is not aboard. The useful consequence is that nothing is lost: the observer
     * only records a topic as fired when {@link KorrinTopicQueue#queue} reports success, so a bark
     * held back by the cooldown stays unfired and is offered again the next time the player docks
     * somewhere it applies. The limiter spaces content out rather than deleting it, which is what
     * makes a generous cooldown safe.
     */
    public static boolean barkAllowed() {
        ensureLoaded();
        return elapsedSince(BARK_KEY) >= barkCooldownDays;
    }

    public static void recordBark() {
        stamp(BARK_KEY);
    }

    /**
     * Whether the "would like a word" nudge may be shown.
     * <p>
     * Limits the <b>message only</b> - filing is never rate-limited: the topic always reaches the
     * backlog, and the contact panel always shows the count regardless of this return value.
     * <p>
     * <b>Defaults to off</b> (shipped at 2 days). The cooldown guarded against message-box spam,
     * but a silently dropped message risks the player missing a filing - and a suppressed bark,
     * not a suppressed message, is the only content actually lost ({@link #barkAllowed} already
     * does that anti-spam work). Kept as a config knob.
     */
    public static boolean nudgeAllowed() {
        ensureLoaded();
        return elapsedSince(NUDGE_KEY) >= nudgeCooldownDays;
    }

    public static void recordNudge() {
        stamp(NUDGE_KEY);
    }

    /** Read by {@link KorrinCommentScript}, which owns the popup cooldown's actual mechanism. */
    public static float popupCooldownDays() {
        ensureLoaded();
        return popupCooldownDays;
    }

    // --- clock -----------------------------------------------------------------------------

    private static float elapsedSince(String key) {
        MemoryAPI memory = memory();
        if (memory == null) return Float.MAX_VALUE;

        // An unset key reads 0, which is the beginning of the campaign clock and therefore reads
        // as "long enough ago" - the correct answer for something that has never happened.
        return Global.getSector().getClock().getElapsedDaysSince(memory.getLong(key));
    }

    private static void stamp(String key) {
        MemoryAPI memory = memory();
        if (memory != null) memory.set(key, Global.getSector().getClock().getTimestamp());
    }

    private static MemoryAPI memory() {
        PersonAPI korrin = KorrinCompanion.getKorrin();
        return korrin == null ? null : korrin.getMemoryWithoutUpdate();
    }

    // --- config ----------------------------------------------------------------------------

    /** Reset so settings are re-read on the next game load. */
    public static void reset() {
        loaded = false;
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            JSONObject json = Global.getSettings().getJSONObject("korrinReactions");
            barkCooldownDays = (float) json.optDouble("barkCooldownDays", barkCooldownDays);
            nudgeCooldownDays = (float) json.optDouble("nudgeCooldownDays", nudgeCooldownDays);
            popupCooldownDays = (float) json.optDouble("popupCooldownDays", popupCooldownDays);
        } catch (Exception e) {
            log.warn("Draconis: could not read korrinReactions settings, using defaults", e);
        }
    }

    private KorrinRateLimit() {
    }
}
