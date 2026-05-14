package levianeer.draconis.data.campaign.characters;

import org.apache.log4j.Logger;
import com.fs.starfarer.api.Global;

/**
 * Facade for Draconis character management.
 * Delegates to per-character classes; keeps the public API stable for external callers.
 */
public class XLII_Characters {

    private static final Logger log = Global.getLogger(XLII_Characters.class);

    // Re-exported for external callers
    public static final String ADMIRAL_ID = XLII_PersonEmilAugust.PERSON_ID;
    public static final String ELIAS_ID = XLII_PersonEliasKorrin.PERSON_ID;
    public static final String ANCKER_ID = XLII_PersonDanielAncker.PERSON_ID;

    public static void initializeAllCharacters() {
        log.info("Draconis: Initializing core characters");
        XLII_PersonEmilAugust.createOrEnsureRegistered();
        XLII_PersonEliasKorrin.createOrEnsureRegistered();
        XLII_PersonDanielAncker.createOrEnsureRegistered();
        updateCharacterPlacements();
    }

    public static void updateCharacterPlacements() {
        XLII_PersonEmilAugust.updatePlacement();
        XLII_PersonEliasKorrin.updatePlacement();
        XLII_PersonDanielAncker.updatePlacement();
    }

    public static void revealEliasKorrin() {
        XLII_PersonEliasKorrin.reveal();
    }

    public static void revealDanielAncker() {
        XLII_PersonDanielAncker.reveal();
    }
}