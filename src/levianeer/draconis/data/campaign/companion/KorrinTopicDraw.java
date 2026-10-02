package levianeer.draconis.data.campaign.companion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Selection pass for Korrin's Talk menu: decides which of the eligible topics are shown when there
 * are more of them than the menu budget.
 * <p>
 * Deliberately free of engine dependencies - no Global, no dialog, no memory. Eligibility is
 * rules.csv's job and happens before this runs; option text, sort order and dialogue never reach
 * here. That makes the one part of the draw whose behaviour cannot be eyeballed in game
 * ("does this starve anything?") drivable from a plain main().
 * <p>
 * Design and rationale: {@code work/outline/korrin-topic-draw.md}.
 */
public class KorrinTopicDraw {

    /** How a class picks from its eligible candidates when it has more than its cap. */
    public enum Policy {
        /** Everything eligible, up to the cap. For content that must not be luck-gated. */
        PINNED,
        /** Highest priority first, oldest first on a tie. Mirrors the backlog's own ordering. */
        PRIORITY,
        /** Weighted random, with a pity floor so nothing starves. */
        LUCK
    }

    /** One content class - see the five-class table in the design note. */
    public static class ClassPolicy {
        public final String name;
        public final int cap;
        public final Policy select;
        /**
         * Days after which a candidate in this class is dropped rather than shown, or null for
         * classes that do not expire.
         * <p>
         * An expiring class is also exempt from pity. The two pull in opposite directions: pressure
         * pushes a topic harder the longer it goes unseen, which is exactly wrong for a remark
         * about a system the player left four months ago.
         */
        public final Float staleAfterDays;

        public ClassPolicy(String name, int cap, Policy select, Float staleAfterDays) {
            this.name = name;
            this.cap = cap;
            this.select = select;
            this.staleAfterDays = staleAfterDays;
        }

        public boolean expires() {
            return staleAfterDays != null;
        }
    }

    /** One eligible topic, as registered by {@code XLII_Korrin offer} during the options pass. */
    public static class Candidate {
        public final String id;
        public final String topicClass;
        /** Relative weight for LUCK classes - what this topic is worth showing often. */
        public final float weight;
        /** Ordering for PRIORITY classes. Ignored elsewhere. */
        public final int priority;
        /** Age in days, for expiring classes. Ignored elsewhere. */
        public final float ageDays;

        public Candidate(String id, String topicClass, float weight, int priority, float ageDays) {
            this.id = id;
            this.topicClass = topicClass;
            this.weight = weight;
            this.priority = priority;
            this.ageDays = ageDays;
        }
    }

    /** Global shape of the menu. Mirrors the {@code korrinTalk} block in settings.json. */
    public static class Config {
        /** Total topic lines. Standing entries (filler, flip, exit) sit outside this. */
        public final int topicBudget;
        /** Cascade order matters: earlier classes take their cap first. */
        public final List<ClassPolicy> classes;
        public final float pressureGain;
        public final int pityThreshold;

        public Config(int topicBudget, List<ClassPolicy> classes, float pressureGain, int pityThreshold) {
            this.topicBudget = topicBudget;
            this.classes = classes;
            this.pressureGain = pressureGain;
            this.pityThreshold = pityThreshold;
        }
    }

    public static class Result {
        /** Topic ids to keep on the menu, in cascade order. Everything else is pruned. */
        public final List<String> picked = new ArrayList<>();
        /**
         * Eligible LUCK candidates that were not picked and have not gone stale. Non-empty means
         * the flip has somewhere to go.
         */
        public final List<String> undrawn = new ArrayList<>();
    }

    /**
     * The pinned classes collectively take at most {@code topicBudget - PINNED_FLOOR} slots.
     * <p>
     * Without this a player carrying a live arc beat and an open offer never sees a casual topic
     * again, and the casual set is the bulk of the writing. The pinned classes are layered on top
     * of it, not in place of it.
     */
    private static final int PINNED_FLOOR = 2;

    /**
     * Picks the hand.
     *
     * @param exclude topic ids to hold out of the LUCK classes - the previous hand, on a flip.
     *                Ignored entirely if honouring it would leave fewer candidates than the budget,
     *                so a flip near the end of the pool turns the page rather than rendering short.
     *                PINNED and PRIORITY classes ignore it: the flip turns over the drawn band only,
     *                and shuffling away the entries the player is meant to be able to find would
     *                turn the affordance into a hazard.
     */
    public static Result pick(Config config, List<Candidate> candidates,
                              Map<String, Integer> pressures, Set<String> exclude, Random random) {
        Result result = new Result();
        if (config == null || candidates == null || candidates.isEmpty()) return result;

        Set<String> held = exclude == null ? Collections.<String>emptySet() : exclude;
        if (countDrawable(config, candidates) - held.size() < config.topicBudget) {
            held = Collections.emptySet();
        }

        int remaining = config.topicBudget;
        int pinnedAllowance = Math.max(0, config.topicBudget - PINNED_FLOOR);

        for (ClassPolicy policy : config.classes) {
            if (remaining <= 0) break;

            List<Candidate> pool = eligibleFor(policy, candidates);
            if (pool.isEmpty()) continue;

            // What this class actually gets: its cap, or whatever the budget has left, whichever
            // is smaller. Every policy below must honour this and not policy.cap.
            int slots = Math.min(policy.cap, remaining);
            if (policy.select == Policy.PINNED) {
                slots = Math.min(slots, pinnedAllowance);
            }
            if (slots <= 0) continue;

            List<Candidate> taken;
            switch (policy.select) {
                case PINNED:
                    taken = pool.subList(0, Math.min(slots, pool.size()));
                    pinnedAllowance -= taken.size();
                    break;
                case PRIORITY:
                    taken = byPriority(pool, slots);
                    break;
                case LUCK:
                default:
                    List<Candidate> drawable = new ArrayList<>();
                    for (Candidate c : pool) {
                        if (!held.contains(c.id)) drawable.add(c);
                    }
                    taken = byLuck(drawable, slots, policy, config, pressures, random);
                    for (Candidate c : drawable) {
                        if (!containsId(taken, c.id)) result.undrawn.add(c.id);
                    }
                    break;
            }

            for (Candidate c : taken) {
                result.picked.add(c.id);
            }
            remaining -= taken.size();
        }

        return result;
    }

    /**
     * The pressures to persist after a conversation.
     * <p>
     * Assignment, never increment - which makes it idempotent under both re-entry hazards at once:
     * the engine running an option-adding command twice, and the player flipping several times in
     * one conversation. {@code shown} is the union of every hand seen this conversation, because a
     * topic the player flipped past was still visible, and visibility is what pressure measures.
     * <p>
     * Only eligible topics move. A topic gated out by rules.csv is absent from {@code candidates}
     * and keeps whatever it had; expiring classes never accumulate pressure at all.
     */
    public static Map<String, Integer> updatePressures(Config config, List<Candidate> candidates,
                                                       Map<String, Integer> snapshot, Set<String> shown) {
        Map<String, Integer> next = new LinkedHashMap<>();
        if (snapshot != null) next.putAll(snapshot);
        if (config == null || candidates == null) return next;

        for (Candidate c : candidates) {
            ClassPolicy policy = classOf(config, c.topicClass);
            if (policy == null || policy.select != Policy.LUCK || policy.expires()) continue;

            if (shown != null && shown.contains(c.id)) {
                next.put(c.id, 0);
            } else {
                Integer had = snapshot == null ? null : snapshot.get(c.id);
                next.put(c.id, (had == null ? 0 : had) + 1);
            }
        }

        // Zero entries are dropped on write - the packed memory string only carries what is nonzero.
        next.values().removeIf(v -> v == null || v == 0);
        return next;
    }

    // --- selection policies ---

    /** Stable sort by priority descending, so equal priorities keep backlog order: oldest first. */
    private static List<Candidate> byPriority(List<Candidate> pool, int slots) {
        List<Candidate> sorted = new ArrayList<>(pool);
        sorted.sort((a, b) -> Integer.compare(b.priority, a.priority));
        return sorted.subList(0, Math.min(slots, sorted.size()));
    }

    /**
     * Pity first, then weighted luck.
     * <p>
     * Pity is what turns an unbounded geometric tail into a real guarantee: a candidate held out
     * {@code pityThreshold} conversations is seated before the draw runs at all. It is only a hard
     * guarantee while fewer candidates are at the threshold than there are slots; past that the
     * surplus keeps accumulating and wins the next draw, which is correct and bounded.
     */
    private static List<Candidate> byLuck(List<Candidate> pool, int slots, ClassPolicy policy,
                                          Config config, Map<String, Integer> pressures, Random random) {
        List<Candidate> taken = new ArrayList<>();
        if (pool.isEmpty() || slots <= 0) return taken;

        List<Candidate> remaining = new ArrayList<>(pool);
        // Shuffle up front so equal pressures and equal weights both break ties randomly.
        Collections.shuffle(remaining, random);

        if (!policy.expires()) {
            int threshold = pityThresholdFor(config, pool.size(), slots);
            List<Candidate> pity = new ArrayList<>();
            for (Candidate c : remaining) {
                if (pressureOf(pressures, c.id) >= threshold) pity.add(c);
            }
            pity.sort((a, b) -> Integer.compare(pressureOf(pressures, b.id), pressureOf(pressures, a.id)));

            for (Candidate c : pity) {
                if (taken.size() >= slots) break;
                taken.add(c);
                remaining.remove(c);
            }
        }

        while (taken.size() < slots && !remaining.isEmpty()) {
            Candidate drawn = weightedDraw(remaining, policy, config, pressures, random);
            if (drawn == null) break;
            taken.add(drawn);
            remaining.remove(drawn);
        }
        return taken;
    }

    /**
     * The configured pity threshold, floored to just past one natural rotation of the pool.
     * <p>
     * A pool of N topics over S slots takes ceil(N/S) conversations to show everything once even
     * under a perfect draw. If the threshold sits below that, every candidate reaches it before
     * luck has had a chance to come round, pity seats the whole menu, and the draw degenerates into
     * strict round-robin - at which point {@code base} weight stops meaning anything at all. The
     * config number is therefore a floor on how long a topic may be missed, not a fixed trigger:
     * it binds on small pools, and the rotation term takes over on large ones.
     */
    private static int pityThresholdFor(Config config, int poolSize, int slots) {
        if (slots <= 0) return config.pityThreshold;
        int rotation = (poolSize + slots - 1) / slots;
        return Math.max(config.pityThreshold, rotation + 1);
    }

    /** weight = base * (1 + pressure * pressureGain). Expiring classes carry no pressure term. */
    private static Candidate weightedDraw(List<Candidate> pool, ClassPolicy policy, Config config,
                                          Map<String, Integer> pressures, Random random) {
        float total = 0f;
        for (Candidate c : pool) {
            total += weightOf(c, policy, config, pressures);
        }
        if (total <= 0f) return pool.get(random.nextInt(pool.size()));

        float roll = random.nextFloat() * total;
        for (Candidate c : pool) {
            roll -= weightOf(c, policy, config, pressures);
            if (roll <= 0f) return c;
        }
        return pool.get(pool.size() - 1);
    }

    private static float weightOf(Candidate c, ClassPolicy policy, Config config,
                                  Map<String, Integer> pressures) {
        float base = Math.max(0f, c.weight);
        if (policy.expires()) return base;
        return base * (1f + pressureOf(pressures, c.id) * config.pressureGain);
    }

    // --- helpers ---

    private static List<Candidate> eligibleFor(ClassPolicy policy, List<Candidate> candidates) {
        List<Candidate> pool = new ArrayList<>();
        for (Candidate c : candidates) {
            if (!policy.name.equals(c.topicClass)) continue;
            if (policy.expires() && c.ageDays > policy.staleAfterDays) continue;
            pool.add(c);
        }
        return pool;
    }

    /** Candidates the flip can actually turn over - LUCK classes only, staleness applied. */
    private static int countDrawable(Config config, List<Candidate> candidates) {
        int n = 0;
        for (ClassPolicy policy : config.classes) {
            if (policy.select != Policy.LUCK) continue;
            n += eligibleFor(policy, candidates).size();
        }
        return n;
    }

    private static ClassPolicy classOf(Config config, String name) {
        for (ClassPolicy policy : config.classes) {
            if (policy.name.equals(name)) return policy;
        }
        return null;
    }

    private static int pressureOf(Map<String, Integer> pressures, String id) {
        if (pressures == null) return 0;
        Integer p = pressures.get(id);
        return p == null ? 0 : p;
    }

    private static boolean containsId(List<Candidate> list, String id) {
        for (Candidate c : list) {
            if (c.id.equals(id)) return true;
        }
        return false;
    }

    private KorrinTopicDraw() {
    }
}
