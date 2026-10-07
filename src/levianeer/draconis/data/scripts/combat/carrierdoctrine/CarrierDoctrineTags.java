package levianeer.draconis.data.scripts.combat.carrierdoctrine;

/**
 * Hull tags that {@link CarrierDoctrineAI} uses to bucket deployed ships into doctrine roles.
 * Mirrors vanilla's Tags.THREAT_* idiom - role is read off the hull spec, never inferred from
 * class/variant identity.
 *
 * This doctrine is exclusive to the FortySecond Battlegroup skins (data/hulls/skins/XLII_*_fortysecond.skin),
 * not the base Draconis hulls - each skin's own "tags" array is where these live, since a skin
 * fully replaces the base hull's tags rather than inheriting them. Currently tagged:
 *   CARRIER:    XLII_shaobi_fortysecond, XLII_alrakis_fortysecond, XLII_alwaid_fortysecond
 *   SCREEN:     XLII_errakis_fortysecond, XLII_eltanin_fortysecond
 *   BATTLELINE: XLII_juza_fortysecond, XLII_tianlong_fortysecond
 *   PICKET:     XLII_alruba_fortysecond
 */
public class CarrierDoctrineTags {
    private CarrierDoctrineTags() {}

    public static final String CARRIER = "XLII_CARRIER";
    public static final String SCREEN = "XLII_SCREEN";
    public static final String BATTLELINE = "XLII_BATTLELINE";
    public static final String PICKET = "XLII_PICKET";
}
