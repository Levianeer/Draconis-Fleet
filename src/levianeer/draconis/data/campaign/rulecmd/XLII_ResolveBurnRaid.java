package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.characters.XLII_PersonEmilAugust;
import levianeer.draconis.data.campaign.events.XLII_KoriInfiltration;
import levianeer.draconis.data.campaign.events.XLII_KoriStrike;
import levianeer.draconis.data.campaign.ids.Factions;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Action command: fired by rules.csv's XLII_burn_raid_continue rule, which is the
 * $raidContinueTrigger MarketCMD calls once the base-game marine raid on Kori (set up by
 * XLII_burn_raid_setup) has fully resolved.
 * <p>
 * Derives the archive-infiltration outcome tier (clean/caught/revealed) from the raid's own
 * auto-computed marine losses instead of the old hand-rolled risk score, applies the existing
 * "caught" reputation penalty, then reopens XLII_KoriStrike directly in its aftermath state.
 * <p>
 * Usage in rules.csv script column:
 *   XLII_ResolveBurnRaid
 */
@SuppressWarnings("unused")
public class XLII_ResolveBurnRaid extends BaseCommandPlugin {

    private static final Logger log = Global.getLogger(XLII_ResolveBurnRaid.class);

    // Marine losses as a fraction of the force that went in. Tunable - see the merged raid's
    // $raidDifficulty (rules.csv, XLII_burn_raid_setup) for the other half of this balance pass.
    private static final float CLEAN_MAX_RATIO  = 0.10f;
    private static final float CAUGHT_MAX_RATIO = 0.35f;

    // Elias having prepped the archive access (the old standalone infiltration's setup beat, now
    // just his own offer scene - see XLII_korrin_opt_archive_hint in rules.csv) makes the archive
    // half of this operation go more quietly, independent of how the assault itself went.
    private static final float ELIAS_PREPPED_RATIO_MULT = 0.7f;

    private static final float DRACONIS_REP_HIT = -0.10f;
    private static final float AUGUST_REP_HIT   = DRACONIS_REP_HIT / 2f;

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        int marinesLost = (int) dialog.getInteractionTarget().getMemoryWithoutUpdate()
                .getFloat("$raidMarinesLost");
        int startingMarines = cargo.getMarines() + marinesLost;

        float lossRatio = startingMarines > 0 ? (float) marinesLost / startingMarines : 0f;
        if (Global.getSector().getMemoryWithoutUpdate().getBoolean("$XLII_koriInfiltrationOffered")) {
            lossRatio *= ELIAS_PREPPED_RATIO_MULT;
        }

        String outcome;
        if (lossRatio <= CLEAN_MAX_RATIO) {
            outcome = XLII_KoriInfiltration.OUTCOME_CLEAN;
        } else if (lossRatio <= CAUGHT_MAX_RATIO) {
            outcome = XLII_KoriInfiltration.OUTCOME_CAUGHT;
            applyCaughtRepPenalty(dialog);
        } else {
            outcome = XLII_KoriInfiltration.OUTCOME_REVEALED;
        }

        log.info("Draconis: XLII_ResolveBurnRaid - marines lost: " + marinesLost
                + " of " + startingMarines + ", outcome: " + outcome);

        // Restores whatever the player's transponder was set to before XLII_KoriStrike.launchRaid()
        // forced it on - see that method's own notes for why (blocks MarketCMD's Story-Point
        // "keep it secret" rep-penalty bypass, which requires the transponder to be off).
        XLII_KoriStrike.restoreTransponderIfNeeded();

        Global.getSector().getMemoryWithoutUpdate().set(XLII_KoriStrike.MEM_RAID_COMPLETE, true);

        XLII_KoriStrike strike = new XLII_KoriStrike(marinesLost, outcome);
        dialog.setPlugin(strike);
        strike.init(dialog);

        return true;
    }

    private void applyCaughtRepPenalty(InteractionDialogAPI dialog) {
        CustomRepImpact draconisImpact = new CustomRepImpact();
        draconisImpact.delta = DRACONIS_REP_HIT;
        Global.getSector().adjustPlayerReputation(
            new RepActionEnvelope(RepActions.CUSTOM, draconisImpact, null, dialog.getTextPanel(), true),
            Factions.DRACONIS
        );

        PersonAPI august = Global.getSector().getImportantPeople().getPerson(XLII_PersonEmilAugust.PERSON_ID);
        if (august != null) {
            CustomRepImpact augustImpact = new CustomRepImpact();
            augustImpact.delta = AUGUST_REP_HIT;
            Global.getSector().adjustPlayerReputation(
                new RepActionEnvelope(RepActions.CUSTOM, augustImpact, null, dialog.getTextPanel(), true),
                august
            );
        }
    }
}
