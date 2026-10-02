package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.companion.KorrinGiftRegistry;
import levianeer.draconis.data.campaign.companion.KorrinTalkMenu;
import levianeer.draconis.data.campaign.companion.KorrinTopicQueue;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Option-adding half of the Korrin companion's rules.csv surface.
 * <p>
 * Split out from {@link XLII_Korrin} because {@code doesCommandAddOptions()} takes no parameters
 * and so cannot answer per-mode. Both FireAll and FireBest execute every command that declares
 * true once inside the sorted option-add loop and again in {@code rule.runScript()}, so a
 * mode-dispatched command that declares true runs *every* mode twice - which silently doubled
 * every {@code XLII_Korrin rep} delta in the mod. Only the modes here may add options.
 *
 * <pre>
 * Script column:
 *   XLII_KorrinOptions backlog         add one dialog option per backlogged topic
 *   XLII_KorrinOptions gifts           add one dialog option per eligible, ungiven gift
 *   XLII_KorrinOptions archiveHint     add the "Ask about the tooling marks" option, highlighted
 * </pre>
 *
 * Every mode must be idempotent regardless: the double execution is the engine's behaviour for
 * any option-adding command, and the split does not change that - it only contains it.
 */
@SuppressWarnings("unused")
public class XLII_KorrinOptions extends BaseCommandPlugin {

    private static final Logger log = Global.getLogger(XLII_KorrinOptions.class);

    /** Sort order for backlog options; keep below the standing menu entries in rules.csv. */
    private static final int BACKLOG_OPTION_ORDER = 1;

    /**
     * Sort order for the gift picker's options. Only meaningful relative to the "Never mind." row
     * in the same submenu (XLII_KorrinGiftOptions) - this trigger never shares a menu with backlog
     * options, so the two orders never have to be compared against each other.
     */
    private static final int GIFT_OPTION_ORDER = 10;

    /** Matches this option's former order value in its XLII_KorrinTalkOptions options-column row. */
    private static final String ARCHIVE_HINT_OPTION_ID = "korrin_archive_hint_open";
    private static final String ARCHIVE_HINT_LABEL = "Ask about the tooling marks";
    private static final int ARCHIVE_HINT_OPTION_ORDER = 87;

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (params.isEmpty()) {
            log.warn("Draconis: XLII_KorrinOptions called with no mode");
            return false;
        }

        String mode = params.get(0).getString(memoryMap);

        if ("backlog".equals(mode)) {
            return addBacklogOptions(dialog);
        }
        if ("gifts".equals(mode)) {
            return addGiftOptions(dialog);
        }
        if ("archiveHint".equals(mode)) {
            return addHighlightedOption(dialog, ARCHIVE_HINT_OPTION_ID, ARCHIVE_HINT_LABEL);
        }

        log.warn("Draconis: XLII_KorrinOptions - unknown mode '" + mode + "'");
        return false;
    }

    /**
     * Declares that this command may add dialog options, so the engine folds them into the same
     * sort-order merge as options-column entries instead of appending them after the fact.
     */
    @Override
    public boolean doesCommandAddOptions() {
        return true;
    }

    /** Backlogged topics sort ahead of the standing menu entries; gifts sort ahead of "Never mind." */
    @Override
    public int getOptionOrder(List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (!params.isEmpty()) {
            String mode = params.get(0).getString(memoryMap);
            if ("gifts".equals(mode)) return GIFT_OPTION_ORDER;
            if ("archiveHint".equals(mode)) return ARCHIVE_HINT_OPTION_ORDER;
        }
        return BACKLOG_OPTION_ORDER;
    }

    /**
     * Adds one option per backlogged topic, using the topic id as the option data.
     * <p>
     * Drawn in the highlight colour, the same yellow the game uses to mark a bar entry the player
     * should not walk past. Standing casual topics stay in the ordinary option colour, so a thing
     * he actually raised himself is visually distinct from small talk that is always on the menu.
     * <p>
     * A bark sits in the backlog too, but never as an option here - it is consumed automatically
     * by {@code XLII_Korrin barkIntro} as the Talk dialogue's opening line instead, so it is
     * filtered out by tier.
     * <p>
     * Also registers each option with {@link KorrinTalkMenu} as a {@code reaction} candidate, so
     * the topic draw's priority-ordered cap applies to the backlog the same way it applies to
     * casual topics - see {@code work/outline/korrin-topic-draw.md} build step 5. The weight
     * argument is ignored: {@code reaction} selects by priority, which {@link KorrinTalkMenu}
     * reads straight from the topic's own row.
     * <p>
     * Guarded by hasOption because the engine runs this twice per fire; whether the panel
     * de-duplicates by data key is not visible from the API source, so do not rely on it. The
     * {@code offer} call is idempotent on its own (see {@link KorrinTalkMenu#offer}), so it is not
     * guarded by the same check.
     */
    private boolean addBacklogOptions(InteractionDialogAPI dialog) {
        if (dialog == null) return false;

        for (String id : KorrinTopicQueue.getBacklog()) {
            KorrinTopicQueue.Topic topic = KorrinTopicQueue.getTopic(id);
            if (topic == null || topic.tier == KorrinTopicQueue.Tier.BARK) continue;
            KorrinTalkMenu.offer(id, "reaction", 0f);
            if (dialog.getOptionPanel().hasOption(id)) continue;
            dialog.getOptionPanel().addOption(topic.label, id, Misc.getHighlightColor(), null);
        }
        return true;
    }

    /**
     * Adds one option per gift that is both in cargo and not yet given, using the gift id as the
     * option data - the picker opened by "Give him something." Not part of the topic draw: gift
     * availability is driven by cargo state, not narrative pacing, so it does not call
     * {@link KorrinTalkMenu#offer}. Ordinary option colour, since this is the player's own
     * initiative rather than something Korrin raised - uses the 2-arg {@code addOption} overload
     * deliberately, not the 4-arg one with an explicit {@code null} Color: unlike the panel's other
     * addOption overloads, that one does not null-check its color argument and throws an NPE
     * ({@code Color.getAlpha()} on a null parameter).
     * <p>
     * Guarded by hasOption for the same reason addBacklogOptions is - the engine runs this twice
     * per fire.
     */
    private boolean addGiftOptions(InteractionDialogAPI dialog) {
        if (dialog == null) return false;

        for (KorrinGiftRegistry.Gift gift : KorrinGiftRegistry.getGiftsSorted()) {
            if (!KorrinGiftRegistry.isEligible(gift)) continue;
            if (dialog.getOptionPanel().hasOption(gift.id)) continue;
            dialog.getOptionPanel().addOption(gift.label, gift.id);
        }
        return true;
    }

    /**
     * Colored at add-time via the 4-arg {@code addOption(text, id, color, tooltip)} overload, the
     * same way {@link #addBacklogOptions} does, rather than a declarative rules.csv {@code options}
     * column entry plus a later {@code SetOptionColor} script call - that combination does not
     * render highlighted on Korrin's own Talk dialog (a fleet-backed
     * RuleBasedInteractionDialogPluginImpl, per {@code .claude/systems/korrin-companion.md}'s
     * "Reaching Korrin from inside a dialog" note), even though the same pattern is standard,
     * working vanilla usage elsewhere (e.g. every {@code SetOptionColor <id> gray} dev option in
     * the base game's own rules.csv). Root cause not confirmed; this sidesteps it by using the one
     * mechanism already known to render correctly in this exact dialog.
     * <p>
     * The caller's own rules.csv row still owns all gating (conditions column) and both option ids
     * still have an ordinary {@code DialogOptionSelected} handler elsewhere in rules.csv keyed on
     * the same id - this only changes how the option gets added, not what picking it does.
     * <p>
     * Guarded by hasOption for the same reason addBacklogOptions is - the engine runs this twice
     * per fire.
     */
    private boolean addHighlightedOption(InteractionDialogAPI dialog, String optionId, String label) {
        if (dialog == null) return false;
        if (dialog.getOptionPanel().hasOption(optionId)) return true;
        dialog.getOptionPanel().addOption(label, optionId, Misc.getHighlightColor(), null);
        return true;
    }
}
