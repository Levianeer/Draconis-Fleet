package levianeer.draconis.data.campaign.intel.aicore.diplomacy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.campaign.DiplomacyManager;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Applies diplomatic strain through Nexerelin's event system
 * This affects both immediate disposition and long-term relationship trajectory
 */
public class DraconisDiplomacyStrain {

    // Base strain values (these get multiplied by EVENT_MULT = 80 in DiplomacyBrain)
    private static final float ALPHA_CORE_STRAIN = -0.06f;  // -4.8 disposition
    private static final float BETA_CORE_STRAIN = -0.04f;   // -3.2 disposition
    private static final float GAMMA_CORE_STRAIN = -0.025f; // -2.0 disposition

    /**
     * Applies diplomatic strain when AI cores are discovered
     * This uses Nexerelin's reportDiplomacyEvent system which:
     * 1. Immediately affects disposition towards the faction
     * 2. Decays slowly over time (EVENT_DECREMENT_PER_DAY = 0.2)
     * 3. Influences future diplomatic events and war declarations
     *
     * @param targetFactionId Faction whose AI cores were discovered
     * @param alphaCores Number of alpha cores
     * @param betaCores Number of beta cores
     * @param gammaCores Number of gamma cores
     */
    public static void applyAICoreStrain(String targetFactionId, int alphaCores, int betaCores, int gammaCores) {
        if (!isNexerelinEnabled()) return;
        if (targetFactionId == null || targetFactionId.equals(DRACONIS)) return;

        try {
            // Get Draconis's brain (how Draconis views other factions)
            DiplomacyBrain draconiBrain = DiplomacyManager.getManager().getDiplomacyBrain(DRACONIS);
            if (draconiBrain == null) {
                Global.getLogger(DraconisDiplomacyStrain.class).warn(
                        "No diplomacy brain found for " + DRACONIS
                );
                return;
            }

            // Calculate total strain effect
            // Note: reportDiplomacyEvent multiplies by EVENT_MULT (80), so we use small values
            float totalStrain = (alphaCores * ALPHA_CORE_STRAIN) +
                    (betaCores * BETA_CORE_STRAIN) +
                    (gammaCores * GAMMA_CORE_STRAIN);

            if (totalStrain == 0) return;

            // Apply the diplomatic event (Draconis becomes more hostile to target)
            float newDisposition = draconiBrain.reportDiplomacyEvent(targetFactionId, totalStrain);

            FactionAPI targetFaction = Global.getSector().getFaction(targetFactionId);
            Global.getLogger(DraconisDiplomacyStrain.class).info(
                    String.format("Applied AI core diplomatic strain: %s vs %s | Cores: %da %db %dg | Effect: %.2f | New disposition from events: %.2f",
                            DRACONIS,
                            targetFaction.getDisplayName(),
                            alphaCores, betaCores, gammaCores,
                            totalStrain,
                            newDisposition)
            );

            // Also apply reciprocal strain (they don't like being investigated)
            DiplomacyBrain targetBrain = DiplomacyManager.getManager().getDiplomacyBrain(targetFactionId);
            if (targetBrain != null) {
                // They're slightly less upset than we are suspicious
                float reciprocalStrain = totalStrain * 0.7f;
                targetBrain.reportDiplomacyEvent(DRACONIS, reciprocalStrain);

                Global.getLogger(DraconisDiplomacyStrain.class).info(
                        String.format("  Applied reciprocal strain: %s dislikes %s by %.2f",
                                targetFaction.getDisplayName(),
                                "Draconis",
                                reciprocalStrain)
                );
            }

        } catch (Exception e) {
            Global.getLogger(DraconisDiplomacyStrain.class).error(
                    "Error applying AI core diplomatic strain: " + e.getMessage(), e
            );
        }
    }

    /**
     * Applies positive diplomatic event when relations improve
     * Use when cores are removed, alliances formed, etc.
     */
    public static void applyDiplomaticImprovement(String targetFactionId, float amount) {
        if (!isNexerelinEnabled()) return;

        try {
            DiplomacyBrain brain = DiplomacyManager.getManager().getDiplomacyBrain(DRACONIS);
            if (brain != null) {
                brain.reportDiplomacyEvent(targetFactionId, amount);

                Global.getLogger(DraconisDiplomacyStrain.class).info(
                        String.format("Applied diplomatic improvement: %s <-> %s by %.2f",
                                DRACONIS, targetFactionId, amount)
                );
            }
        } catch (Exception e) {
            Global.getLogger(DraconisDiplomacyStrain.class).error(
                    "Error applying diplomatic improvement: " + e.getMessage(), e
            );
        }
    }

    /**
     * Gets current diplomatic strain from events
     * Returns the "events" modifier value from disposition
     */
    public static float getCurrentStrain(String targetFactionId) {
        if (!isNexerelinEnabled()) return 0f;

        try {
            DiplomacyBrain brain = DiplomacyManager.getManager().getDiplomacyBrain(DRACONIS);
            if (brain != null) {
                DiplomacyBrain.DispositionEntry disposition = brain.getDisposition(targetFactionId);
                if (disposition != null && disposition.disposition.getFlatStatMod("events") != null) {
                    return disposition.disposition.getFlatStatMod("events").getValue();
                }
            }
        } catch (Exception e) {
            // Silent fail
        }

        return 0f;
    }

    /**
     * Checks if Nexerelin is present and loaded
     */
    private static boolean isNexerelinEnabled() {
        try {
            Class.forName("exerelin.campaign.DiplomacyManager");
            return Global.getSettings().getModManager().isModEnabled("nexerelin");
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}