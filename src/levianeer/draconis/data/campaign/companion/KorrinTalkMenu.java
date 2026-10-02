package levianeer.draconis.data.campaign.companion;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Engine-side half of the Talk menu draw: registration, rendering, and pressure persistence.
 * The selection itself is {@link KorrinTopicDraw}, which knows nothing about dialogs or memory.
 * <p>
 * Design: {@code work/outline/korrin-topic-draw.md}.
 */
public class KorrinTalkMenu {

    private static final Logger log = Global.getLogger(KorrinTalkMenu.class);

    /** rules.csv trigger carrying one rule per eligible Talk topic. */
    public static final String OPTIONS_TRIGGER = "XLII_KorrinTalkOptions";

    /** Option id of the flip entry, pruned when there is nothing left to turn over. */
    public static final String FLIP_OPTION = "korrin_talk_flip";

    private static final String PRESSURE_KEY = "$korrin_pressure";

    // --- per-conversation cache ---------------------------------------------------------------
    // Static, and deliberately not persisted: a dialog cannot be open across a save. The cache is
    // keyed on the dialog instance rather than cleared by an explicit rules.csv call, so a new
    // conversation cannot forget to start a new hand.

    private static InteractionDialogAPI cachedDialog;
    private static List<String> hand = new ArrayList<>();
    private static List<String> undrawn = new ArrayList<>();
    /** Every topic seen in any hand this conversation - flips included. Drives the pressure reset. */
    private static Set<String> shownThisConversation = new LinkedHashSet<>();
    /** Hands already turned over this conversation, held out of the next draw. */
    private static Set<String> excluded = new LinkedHashSet<>();
    /** Pressures as they stood when the conversation opened. Assignment is against this, not the running value. */
    private static Map<String, Integer> snapshot = new LinkedHashMap<>();

    private static final List<KorrinTopicDraw.Candidate> registered = new ArrayList<>();

    private static KorrinTopicDraw.Config config;
    private static final Random RANDOM = new Random();

    /**
     * Registers one eligible topic as drawable. Called from the topic's own rules.csv row during
     * the options pass, so gate, weight, class, label and handler all stay together on that row.
     * <p>
     * A topic that never calls this is never pruned and always shows. That is the right default:
     * forgetting the cell is loud the first time the menu is opened, rather than quietly letting a
     * topic escape the cap.
     */
    public static void offer(String optionId, String topicClass, float weight) {
        if (optionId == null || topicClass == null) return;
        for (KorrinTopicDraw.Candidate c : registered) {
            if (c.id.equals(optionId)) return;
        }
        // ageDays is 0 until something registers with a real timestamp. Only expiring classes read
        // it, and none have content yet - see "world" in the design note.
        registered.add(new KorrinTopicDraw.Candidate(optionId, topicClass, weight, priorityOf(optionId), 0f));
    }

    /**
     * Builds the Talk menu: fires the options trigger, then prunes the losers.
     * <p>
     * The two halves are one command because the draw has to run after the trigger every single
     * time the menu is rebuilt, and there are six call sites. A rule that fired the trigger and
     * forgot the draw would render the whole unpruned menu, which is a footgun aimed at future
     * content.
     */
    public static void render(String ruleId, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return;

        if (dialog != cachedDialog) beginConversation(dialog);

        registered.clear();
        // FireAll populates every eligible option first and runs the script column afterwards, so
        // by the time this returns the panel is fully built and every offer() has landed.
        FireAll.fire(ruleId, dialog, memoryMap, OPTIONS_TRIGGER);

        if (hand.isEmpty()) drawHand();

        for (KorrinTopicDraw.Candidate c : registered) {
            if (!hand.contains(c.id)) dialog.getOptionPanel().removeOption(c.id);
        }

        // The flip is an ordinary rules.csv option pruned like any other, rather than a condition
        // on its row: conditions are evaluated inside FireAll, before the draw has run, so no
        // condition can see this conversation's undrawn set.
        if (undrawn.isEmpty()) dialog.getOptionPanel().removeOption(FLIP_OPTION);
    }

    /**
     * Discards the current hand and holds it out of the next draw, so a flip visibly turns the page
     * rather than re-rolling into the same three. The caller re-renders.
     */
    public static void reroll() {
        excluded.addAll(hand);
        hand = new ArrayList<>();
        undrawn = new ArrayList<>();
    }

    private static void beginConversation(InteractionDialogAPI dialog) {
        cachedDialog = dialog;
        hand = new ArrayList<>();
        undrawn = new ArrayList<>();
        shownThisConversation = new LinkedHashSet<>();
        excluded = new LinkedHashSet<>();
        snapshot = readPressures();
    }

    private static void drawHand() {
        KorrinTopicDraw.Result result =
                KorrinTopicDraw.pick(config(), registered, snapshot, excluded, RANDOM);

        hand = result.picked;
        undrawn = result.undrawn;
        shownThisConversation.addAll(hand);

        // Assignment against the conversation-start snapshot, so this is identical whether it runs
        // once or five times in a conversation.
        writePressures(KorrinTopicDraw.updatePressures(config(), registered, snapshot, shownThisConversation));
    }

    /** Backlogged comments carry a priority; standing topics do not. */
    private static int priorityOf(String optionId) {
        KorrinTopicQueue.Topic topic = KorrinTopicQueue.getTopic(optionId);
        return topic == null ? 0 : topic.priority;
    }

    // --- pressure persistence ------------------------------------------------------------------
    // Packed "id:n,id:n" into Korrin's memory, matching the comma-delimited convention
    // KorrinTopicQueue already uses for pending and backlog. Zero entries are dropped on write.

    private static Map<String, Integer> readPressures() {
        Map<String, Integer> out = new LinkedHashMap<>();
        PersonAPI korrin = KorrinCompanion.getKorrin();
        if (korrin == null) return out;

        String raw = korrin.getMemoryWithoutUpdate().getString(PRESSURE_KEY);
        if (raw == null || raw.isEmpty()) return out;

        for (String part : raw.split(",")) {
            String[] kv = part.trim().split(":");
            if (kv.length != 2) continue;
            try {
                out.put(kv[0], Integer.parseInt(kv[1]));
            } catch (NumberFormatException e) {
                log.warn("Draconis: bad Korrin pressure entry '" + part + "'");
            }
        }
        return out;
    }

    private static void writePressures(Map<String, Integer> pressures) {
        PersonAPI korrin = KorrinCompanion.getKorrin();
        if (korrin == null) return;

        if (pressures.isEmpty()) {
            korrin.getMemoryWithoutUpdate().unset(PRESSURE_KEY);
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : pressures.entrySet()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(e.getKey()).append(":").append(e.getValue());
        }
        korrin.getMemoryWithoutUpdate().set(PRESSURE_KEY, sb.toString());
    }

    // --- config --------------------------------------------------------------------------------

    /** Reset so settings are re-read on the next game load. */
    public static void reset() {
        config = null;
        cachedDialog = null;
    }

    private static KorrinTopicDraw.Config config() {
        if (config == null) config = loadConfig();
        return config;
    }

    private static KorrinTopicDraw.Config loadConfig() {
        int budget = 3;
        float pressureGain = 1f;
        int pityThreshold = 4;
        List<KorrinTopicDraw.ClassPolicy> classes = defaultClasses();

        try {
            JSONObject json = Global.getSettings().getJSONObject("korrinTalk");
            budget = json.optInt("topicBudget", budget);
            pressureGain = (float) json.optDouble("pressureGain", pressureGain);
            pityThreshold = json.optInt("pityThreshold", pityThreshold);

            JSONObject declared = json.optJSONObject("classes");
            if (declared != null) {
                List<KorrinTopicDraw.ClassPolicy> loaded = new ArrayList<>();
                // Cascade order is the declaration order in defaultClasses(), not the JSON's -
                // allocation depends on it, and JSON object order is not guaranteed.
                for (KorrinTopicDraw.ClassPolicy fallback : classes) {
                    JSONObject row = declared.optJSONObject(fallback.name);
                    if (row == null) {
                        loaded.add(fallback);
                        continue;
                    }
                    Float stale = row.has("staleAfter") ? (float) row.optDouble("staleAfter") : null;
                    loaded.add(new KorrinTopicDraw.ClassPolicy(
                            fallback.name,
                            row.optInt("cap", fallback.cap),
                            policyOf(row.optString("select", null), fallback.select),
                            stale));
                }
                classes = loaded;
            }
        } catch (Exception e) {
            log.warn("Draconis: could not read korrinTalk settings, using defaults", e);
        }

        return new KorrinTopicDraw.Config(budget, classes, pressureGain, pityThreshold);
    }

    private static List<KorrinTopicDraw.ClassPolicy> defaultClasses() {
        return new ArrayList<>(Arrays.asList(
                new KorrinTopicDraw.ClassPolicy("arc", 1, KorrinTopicDraw.Policy.PINNED, null),
                new KorrinTopicDraw.ClassPolicy("reaction", 2, KorrinTopicDraw.Policy.PRIORITY, null),
                new KorrinTopicDraw.ClassPolicy("offer", 1, KorrinTopicDraw.Policy.PINNED, null),
                new KorrinTopicDraw.ClassPolicy("casual", 3, KorrinTopicDraw.Policy.LUCK, null),
                new KorrinTopicDraw.ClassPolicy("world", 1, KorrinTopicDraw.Policy.LUCK, 60f)));
    }

    private static KorrinTopicDraw.Policy policyOf(String name, KorrinTopicDraw.Policy fallback) {
        if (name == null || name.isEmpty()) return fallback;
        try {
            return KorrinTopicDraw.Policy.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Draconis: unknown Korrin topic selection policy '" + name + "'");
            return fallback;
        }
    }

    private KorrinTalkMenu() {
    }
}
