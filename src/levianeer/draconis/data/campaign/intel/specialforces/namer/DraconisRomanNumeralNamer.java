package levianeer.draconis.data.campaign.intel.specialforces.namer;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import exerelin.campaign.intel.specialforces.namer.SpecialForcesNamer;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

/**
 * Names Draconis special forces fleets using Roman numerals.
 * Generates names like "VI Battlegroup", "XIX Battlegroup", etc.
 * Excludes XLII (42).
 */

@SuppressWarnings("unused")
public class DraconisRomanNumeralNamer implements SpecialForcesNamer {

    private static final Logger log = Global.getLogger(DraconisRomanNumeralNamer.class);
    private static final int MIN_NUMBER = 1;
    private static final int MAX_NUMBER = 50;
    private static final int EXCLUDED_NUMBER = 42; // XLII already exists

    @Override
    public String getFleetName(CampaignFleetAPI fleet, MarketAPI origin, PersonAPI commander) {
        int number = generateUniqueNumber();
        String romanNumeral = toRomanNumeral(number);
        String fleetName = romanNumeral + " Battlegroup";

        if (log.isDebugEnabled()) {
            log.debug("DraconisFleet: Generated special forces fleet name: " + fleetName);
        }

        return fleetName;
    }

    /**
     * Generates a random number in the valid range, excluding XLII (42).
     */
    private int generateUniqueNumber() {
        int number;
        do {
            number = MathUtils.getRandomNumberInRange(MIN_NUMBER, MAX_NUMBER);
        } while (number == EXCLUDED_NUMBER);
        return number;
    }

    /**
     * Converts an integer to its Roman numeral representation.
     * Supports values from 1 to 50 (I to L).
     */
    private String toRomanNumeral(int number) {
        if (number < 1 || number > 50) {
            log.warn("DraconisFleet: Number out of Roman numeral range: " + number);
            return "I"; // Fallback to I
        }

        // Roman numeral mapping in descending order
        int[] values = {50, 40, 10, 9, 5, 4, 1};
        String[] numerals = {"L", "XL", "X", "IX", "V", "IV", "I"};

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < values.length; i++) {
            while (number >= values[i]) {
                result.append(numerals[i]);
                number -= values[i];
            }
        }

        return result.toString();
    }
}