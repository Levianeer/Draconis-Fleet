package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Rule command that calculates ship or fighter wing cost and stores it in memory variables
 * for display in dialog options.
 * <p>
 * Usage: XLII_CalculateShipCost <variant_id> [suffix]
 * <p>
 * If suffix is provided, sets memory variables with that suffix:
 * - $XLII_cost_credits_<suffix>: Raw numeric cost
 * - $XLII_cost_credits_formatted_<suffix>: Formatted cost string (e.g., "8,000")
 * - $XLII_cost_rep_<suffix>: Reputation point cost as integer (e.g., "1" for -0.01 rep)
 * <p>
 * If suffix is omitted, uses default variable names without suffix.
 */
@SuppressWarnings("unused")
public class XLII_CalculateShipCost extends BaseCommandPlugin {
    private static final Logger log = Global.getLogger(XLII_CalculateShipCost.class);

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (params.isEmpty()) {
            log.warn("XLII_CalculateShipCost: No variant ID provided");
            return false;
        }

        String variantId = params.get(0).getString(memoryMap);

        // Optional suffix for unique variable names
        String suffix = "";
        if (params.size() > 1) {
            suffix = "_" + params.get(1).getString(memoryMap);
        }

        // Try to create fleet member - first as fighter wing, then as ship
        FleetMemberAPI tempMember = null;
        boolean isWing = false;

        try {
            tempMember = Global.getFactory().createFleetMember(FleetMemberType.FIGHTER_WING, variantId);
            isWing = true;
            log.info("XLII_CalculateShipCost: Successfully identified " + variantId + " as fighter wing");
        } catch (RuntimeException e) {
            // Not a fighter wing, try as ship
            try {
                tempMember = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
                log.info("XLII_CalculateShipCost: Successfully identified " + variantId + " as ship");
            } catch (Exception e2) {
                log.warn("XLII_CalculateShipCost: Could not create fleet member for variant: " + variantId);
                return false;
            }
        }

        // Calculate cost
        int cost;
        if (isWing) {
            cost = (int) Global.getSettings().getFighterWingSpec(variantId).getBaseValue();
        } else {
            cost = (int) tempMember.getBaseValue();
        }

        // Calculate reputation cost
        float repCost;
        if (isWing) {
            // Fighter wings cost 1 reputation point
            repCost = -0.01f;
        } else {
            // Ships use hull-size-based cost
            repCost = getReputationCostByHullSize(tempMember);
        }

        // Convert reputation to points for display (e.g., -0.01 = 1 point)
        int repPoints = Math.abs((int) (repCost * 100));

        // Get local memory to store variables
        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
        if (memory == null) {
            log.warn("XLII_CalculateShipCost: Could not access local memory");
            return false;
        }

        // Store values in memory (WITH $ prefix as required by Starsector)
        String creditsKey = "$XLII_cost_credits" + suffix;
        String formattedKey = "$XLII_cost_credits_formatted" + suffix;
        String repKey = "$XLII_cost_rep" + suffix;
        String formattedValue = Misc.getWithDGS(cost);

        memory.set(creditsKey, cost, 0);
        memory.set(formattedKey, formattedValue, 0);
        memory.set(repKey, repPoints, 0);

        log.info("XLII_CalculateShipCost: Calculated cost for " + variantId +
                 " - Credits: " + cost + ", Rep: " + repPoints + " (" + (isWing ? "wing" : "ship") + ")" +
                 " [suffix: " + suffix + "]");
        log.info("XLII_CalculateShipCost: Set variables -> " + creditsKey + "=" + cost +
                 ", " + formattedKey + "=" + formattedValue +
                 ", " + repKey + "=" + repPoints);

        return true;
    }

    /**
     * Calculate reputation cost based on ship hull size.
     * @param member The fleet member to calculate cost for
     * @return Negative reputation cost (e.g., -0.01f for frigates)
     */
    private float getReputationCostByHullSize(FleetMemberAPI member) {
        return switch (member.getHullSpec().getHullSize()) {
            case FIGHTER -> -0.01f;         // 1 reputation point
            case FRIGATE -> -0.02f;         // 2 reputation point
            case DESTROYER -> -0.03f;       // 3 reputation points
            case CRUISER -> -0.04f;         // 4 reputation points
            case CAPITAL_SHIP -> -0.05f;    // 5 reputation points
            default -> -0.01f;              // Default to frigate cost
        };
    }
}