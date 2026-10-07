package levianeer.draconis.data.scripts.combat.carrierdoctrine;

/**
 * What a carrier is doing with its wings this cycle. Computed per carrier from its actual
 * deployed wing loadout (see {@link CarrierDoctrineAI#updateCarrierCapability}), not from
 * variant name/id - the mod's own "_Strike"/"_Escort" variant naming does not reliably
 * predict wing composition (e.g. XLII_alwaid_Escort is bomber-heavy).
 */
public enum CarrierDuty {
    STRIKE,
    CAP
}
