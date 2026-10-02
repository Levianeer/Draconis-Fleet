package levianeer.draconis.data.campaign.intel.longsight;

/**
 * Flag-name constants holder only - the actual scene (Longsight's warning, the two optional
 * questions, the verdict, and the first branch point) has been ported to rules.csv in full
 * ({@code # [UPLINK TO GOD] Longsight Contact}, entry trigger {@code XLII_longsight_contact_open}),
 * the same way {@code XLII_KoriInfiltration} survives only as constants after the raid it used to
 * drive was merged elsewhere. {@code LongsightQuestMission} still reads {@link #CONTACT_DONE_FLAG}
 * by name, so the class stays rather than inlining the literal at each call site.
 * <p>
 * See {@code XLII_BeginLongsightContact} for the in-place {@code dialog.setPlugin()} hand-off, and
 * {@code .claude/systems/longsight.md} for the full scene's flow and history.
 */
public final class XLII_LongsightContactDialog {

    /** Set on close, either branch. Marks the direct-contact scene as done. */
    public static final String CONTACT_DONE_FLAG = "$XLII_longsightContactDone";

    private XLII_LongsightContactDialog() {}
}
