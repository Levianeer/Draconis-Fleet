package levianeer.draconis.data.campaign.events;

/**
 * The Kori archive infiltration used to be its own standalone operation (Elias-gated, three risk
 * beats resolving to a clean/caught/revealed outcome). It has since been folded into the single
 * merged Burn the Machine raid - see XLII_KoriStrike and XLII_ResolveBurnRaid, which now derive
 * this outcome from the base game's own marine-raid results instead of a bespoke risk score.
 * <p>
 * This class survives purely as a flag-name constants holder: ShapesOfOldMission and
 * XLII_LongsightContactDialog reference these by name and don't otherwise need to change.
 */
public final class XLII_KoriInfiltration {

    public static final String MEM_COMPLETE = "$XLII_koriInfiltrationComplete";
    public static final String MEM_OUTCOME  = "$XLII_koriInfiltrationOutcome";

    public static final String OUTCOME_CLEAN    = "clean";
    public static final String OUTCOME_CAUGHT   = "caught";
    public static final String OUTCOME_REVEALED = "revealed";

    private XLII_KoriInfiltration() {}
}
