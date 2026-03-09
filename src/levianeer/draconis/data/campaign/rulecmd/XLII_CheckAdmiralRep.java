package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Condition command: returns true if Fleet Admiral Emil August's personal
 * reputation with the player is maxed.
 * <p>
 * Usage in rules.csv conditions column:
 *   XLII_CheckAdmiralRep
 */
@SuppressWarnings("unused")
public class XLII_CheckAdmiralRep extends BaseCommandPlugin {

    private static final Logger log = Global.getLogger(XLII_CheckAdmiralRep.class);

    private static final float COOPERATIVE_THRESHOLD = 1f;

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        PersonAPI admiral = Global.getSector().getImportantPeople().getPerson("XLII_fleet_admiral_emil");
        if (admiral == null) {
            log.debug("Draconis: XLII_CheckAdmiralRep - Admiral August not found in ImportantPeople");
            return false;
        }

        float rel = admiral.getRelToPlayer().getRel();
        boolean eligible = rel >= COOPERATIVE_THRESHOLD;
        log.debug("Draconis: XLII_CheckAdmiralRep - rel=" + rel + " eligible=" + eligible);
        return eligible;
    }
}