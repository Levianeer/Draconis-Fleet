package levianeer.draconis.data.campaign.companion;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data-driven replacement for one-off "force Korrin to leave" calls.
 * <p>
 * Each row of korrin_leave_triggers.csv names a memory flag to watch and what to do to Korrin once
 * it goes true - send him back to his post ({@code station}, reversible) or lose him for good
 * ({@code forever}, see {@link KorrinCompanion#leaveForever()}). Polled the same way
 * {@link KorrinObserver#pollWatches()} polls korrin_topics.csv's own watch column, since vanilla
 * quest flags have no listener to hang off.
 * <p>
 * This does not replace a bespoke call like the Uplink to God failure sequence
 * (XLII_KoriStrike.finalizeFailure()) - that one is gated on a local dialog variable
 * ({@code eliasStayed}) inside a scripted dialog branch, not a standing memory flag, so there is
 * nothing here for it to watch. This is for the common case: "once flag X is true, he's gone or
 * sent home," with no dialog of its own.
 * <p>
 * An optional {@code topic} column names a korrin_topics.csv id to queue and wait on before acting
 * - see {@link #pollWatches()}'s two-phase handling of it. Leave it blank for a silent transition
 * ({@code korrin_august_death}'s shape: the reaction is already narrated elsewhere).
 */
public class KorrinLeaveTriggers {

    private static final Logger log = Global.getLogger(KorrinLeaveTriggers.class);

    /** Trigger ids that have fully fired (action performed), so each one only ever acts once. */
    private static final String FIRED_KEY = "$korrin_leave_fired";
    /** Trigger ids whose reaction topic has been queued, waiting on it to resolve before acting. */
    private static final String QUEUED_KEY = "$korrin_leave_queued";

    private static final String CSV = "data/config/korrin_leave_triggers.csv";
    private static final String MOD_ID = "levianeer_draconis";

    public enum Action { STATION, FOREVER }

    private static class Trigger {
        final String id;
        final String watch;
        final Action action;
        final String message;
        final String topic;

        Trigger(String id, String watch, Action action, String message, String topic) {
            this.id = id;
            this.watch = watch;
            this.action = action;
            this.message = message;
            this.topic = topic;
        }
    }

    private static Map<String, Trigger> triggers;

    private KorrinLeaveTriggers() {}

    /**
     * Polls every declared watch flag and acts on the first one whose condition is met. Driven
     * from {@link KorrinCommentScript}, right alongside {@link KorrinObserver#pollWatches()} -
     * same reasoning, same tick.
     * <p>
     * A trigger only fires while Korrin is actually aboard - {@link KorrinCompanion#leaveForever()}
     * and {@link KorrinCompanion#returnToStation()} are both no-ops otherwise, and a "he leaves in
     * grief" message makes no sense for a Korrin who was never there. Deliberately not marked fired
     * in that case either, so it is re-checked and still fires later if he comes aboard afterward
     * while the flag is still set.
     * <p>
     * When a {@code topic} is declared, the action does not happen on the same tick the flag goes
     * true: the topic is queued first, and the trigger waits - re-checking every tick - until it is
     * no longer pending or backlogged (i.e. the player has actually read it) before performing the
     * leave action. Without this, a {@code forever} action firing immediately would strip him from
     * the roster and lock GONE before the popup he just raised could ever be shown or read from the
     * backlog - the scene would be queued and then permanently unreachable. Declining the popup just
     * defers it to the backlog as usual, which also defers the departure - he doesn't truly leave
     * until the player actually hears him out.
     */
    public static void pollWatches() {
        if (!KorrinCompanion.isAboard()) return;

        for (Trigger trigger : getTriggers().values()) {
            if (hasFired(trigger.id)) continue;

            boolean flagSet = Global.getSector().getMemoryWithoutUpdate().getBoolean(trigger.watch)
                    || Global.getSector().getPlayerPerson().getMemoryWithoutUpdate().getBoolean(trigger.watch);
            if (!flagSet) continue;

            if (!trigger.topic.isEmpty()) {
                if (!hasQueued(trigger.id)) {
                    if (KorrinTopicQueue.queue(trigger.topic)) markQueued(trigger.id);
                    continue;
                }
                if (KorrinTopicQueue.isPending(trigger.topic)
                        || KorrinTopicQueue.getBacklog().contains(trigger.topic)) {
                    continue;
                }
                // Falls through once the topic is no longer pending/backlogged - it's been read.
            }

            if (!trigger.message.isEmpty()) {
                Global.getSector().getCampaignUI().addMessage(trigger.message, Misc.getHighlightColor());
            }
            if (trigger.action == Action.FOREVER) {
                KorrinCompanion.leaveForever();
            } else {
                KorrinCompanion.returnToStation();
            }
            markFired(trigger.id);
            log.info("Draconis: Korrin leave trigger '" + trigger.id + "' fired (" + trigger.action + ")");
        }
    }

    private static Map<String, Trigger> getTriggers() {
        if (triggers != null) return triggers;

        triggers = new LinkedHashMap<>();
        try {
            JSONArray rows = Global.getSettings().getMergedSpreadsheetDataForMod("id", CSV, MOD_ID);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String id = row.optString("id", "").trim();
                String watch = row.optString("watch", "").trim();
                if (id.isEmpty() || watch.isEmpty()) continue;

                triggers.put(id, new Trigger(
                        id,
                        watch,
                        actionOf(row.optString("action", null), id),
                        row.optString("message", "").trim(),
                        row.optString("topic", "").trim()));
            }
            log.info("Draconis: loaded " + triggers.size() + " Korrin leave trigger(s)");
        } catch (Exception e) {
            log.error("Draconis: failed to load " + CSV, e);
        }
        return triggers;
    }

    /**
     * Unknown or missing action falls back to STATION, not FOREVER - the reversible one, since a
     * typo should never be the thing that silently costs the player their companion for good.
     */
    private static Action actionOf(String raw, String triggerId) {
        if (raw == null || raw.trim().isEmpty()) return Action.STATION;
        try {
            return Action.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Draconis: Korrin leave trigger '" + triggerId + "' has unknown action '" + raw + "'");
            return Action.STATION;
        }
    }

    private static boolean hasFired(String triggerId) {
        return KorrinTopicQueue.contains(FIRED_KEY, triggerId);
    }

    private static void markFired(String triggerId) {
        KorrinTopicQueue.add(FIRED_KEY, triggerId);
    }

    private static boolean hasQueued(String triggerId) {
        return KorrinTopicQueue.contains(QUEUED_KEY, triggerId);
    }

    private static void markQueued(String triggerId) {
        KorrinTopicQueue.add(QUEUED_KEY, triggerId);
    }
}
