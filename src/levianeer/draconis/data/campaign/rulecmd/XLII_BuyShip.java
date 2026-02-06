package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class XLII_BuyShip extends BaseCommandPlugin {
    private static final Logger log = Global.getLogger(XLII_BuyShip.class);

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (params.isEmpty()) {
            return false;
        }

        String variantId = params.get(0).getString(memoryMap);
        String factionId = "XLII_draconis";
        float minRep = 0.5f; // Minimum reputation floor

        // Get the ship/wing base value dynamically by creating a temporary fleet member
        // Try as fighter wing first, then as ship
        int cost;
        FleetMemberAPI tempMember = null;
        boolean isWing = false;

        try {
            tempMember = Global.getFactory().createFleetMember(FleetMemberType.FIGHTER_WING, variantId);
            cost = (int) Global.getSettings().getFighterWingSpec(variantId).getBaseValue();
            isWing = true;
            log.info("XLII_BuyShip: Successfully identified " + variantId + " as fighter wing");
        } catch (RuntimeException e) {
            // Not a fighter wing, try as ship
            try {
                tempMember = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
                cost = (int) tempMember.getBaseValue();
                log.info("XLII_BuyShip: Successfully identified " + variantId + " as ship");
            } catch (Exception e2) {
                // If we can't create the fleet member as either type, return error
                log.warn("XLII_BuyShip: Could not retrieve cost for variant: " + variantId, e2);
                dialog.getTextPanel().addPara("Error: Could not find ship or wing variant '" + variantId + "'.");
                return false;
            }
        }

        // Calculate reputation cost based on type and hull size
        float repCost;
        if (isWing) {
            // Fighter wings cost 1 reputation point
            repCost = -0.01f;
        } else {
            // Ships use hull-size-based reputation cost
            repCost = getReputationCostByHullSize(tempMember);
        }

        // Check if player has enough credits
        if (Global.getSector().getPlayerFleet().getCargo().getCredits().get() < cost) {
            String itemType = isWing ? "wing" : "ship";
            dialog.getTextPanel().addPara("Insufficient credits. This " + itemType + " costs " + Misc.getDGSCredits(cost) + ".");
            log.info("XLII_BuyShip: Purchase denied - insufficient credits for " + variantId);
            return false;
        }

        // Check if this purchase would drop reputation too low
        float currentRep = Global.getSector().getPlayerFaction().getRelationship(factionId);
        float newRep = currentRep + repCost; // repCost is negative, so this decreases rep
        if (newRep < minRep) {
            dialog.getTextPanel().addPara("This purchase would reduce your standing with the Draconis Defence Alliance too much. " +
                    "(Current: " + Misc.getRoundedValueMaxOneAfterDecimal(currentRep) +
                    ", After purchase: " + Misc.getRoundedValueMaxOneAfterDecimal(newRep) +
                    ", Minimum allowed: " + Misc.getRoundedValueMaxOneAfterDecimal(minRep) + ")");
            log.info("XLII_BuyShip: Purchase denied - reputation would drop too low for " + variantId);
            return false;
        }

        // Deduct credits
        AddRemoveCommodity.addCreditsLossText(cost, dialog.getTextPanel());
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cost);

        // Adjust reputation and show notification
        FactionAPI faction = Global.getSector().getFaction(factionId);
        Global.getSector().getPlayerFaction().adjustRelationship(factionId, repCost);

        // Add reputation loss text
        int repChange = (int) (repCost * 100); // Convert to reputation points
        dialog.getTextPanel().addPara("Lost " + Math.abs(repChange) + " reputation with " + faction.getDisplayName() + ".",
                Misc.getNegativeHighlightColor(),
                "" + Math.abs(repChange));

        // Add the ship or wing to fleet/cargo
        if (isWing) {
            // Wings are added to cargo, not fleet
            Global.getSector().getPlayerFleet().getCargo().addFighters(variantId, 1);
            log.info("XLII_BuyShip: Successfully purchased wing " + variantId +
                     " for " + cost + " credits and " + Math.abs(repChange) + " reputation (added to cargo)");
        } else {
            // Ships are added as fleet members
            Global.getSector().getPlayerFleet().getFleetData().addFleetMember(tempMember);
            log.info("XLII_BuyShip: Successfully purchased ship " + variantId +
                     " for " + cost + " credits and " + Math.abs(repChange) + " reputation (added to fleet)");
        }

        // Increase Fleet Admiral Emil August's personal reputation
        adjustAdmiralReputation(repCost, dialog);

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

    /**
     * Adjusts Fleet Admiral Emil August's personal reputation with the player.
     * Called after a successful ship purchase to increase the admiral's favor.
     *
     * @param repCost The reputation cost of the purchase (negative value)
     * @param dialog The interaction dialog for displaying feedback
     */
    private void adjustAdmiralReputation(float repCost, InteractionDialogAPI dialog) {
        // Get Kori market
        MarketAPI koriMarket = Global.getSector().getEconomy().getMarket("kori_market");
        if (koriMarket == null) {
            log.debug("XLII_BuyShip: Kori market not found - skipping admiral reputation adjustment");
            return;
        }

        // Find Fleet Admiral Emil August
        PersonAPI admiral = null;
        for (PersonAPI person : koriMarket.getPeopleCopy()) {
            if ("XLII_fleet_admiral_emil".equals(person.getId())) {
                admiral = person;
                break;
            }
        }

        if (admiral == null) {
            log.debug("XLII_BuyShip: Fleet Admiral Emil August not found on Kori - skipping reputation adjustment");
            return;
        }

        // Adjust personal reputation using official Starsector API
        // Convert negative faction cost to positive personal gain
        float repGain = (Math.abs(repCost)*2);

        CustomRepImpact impact = new CustomRepImpact();
        impact.delta = repGain;

        Global.getSector().adjustPlayerReputation(
            new RepActionEnvelope(RepActions.CUSTOM, impact, null, dialog != null ? dialog.getTextPanel() : null, true),
            admiral
        );

        int repPoints = (int)(repGain * 100);
        log.info("XLII_BuyShip: Increased Fleet Admiral Emil August's personal reputation by " +
                 repPoints + " points (new total: " +
                 Misc.getRoundedValueMaxOneAfterDecimal(admiral.getRelToPlayer().getRel()) + ")");
    }
}