package levianeer.draconis.data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class XLII_BuyShip extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (params.isEmpty()) {
            return false;
        }

        String variantId = params.get(0).getString(memoryMap);
        String factionId = "XLII_draconis";
        float repCost = -0.05f; // -5 reputation = -0.05f
        float minRep = 0.5f; // Minimum reputation floor

        // Get the ship's base value dynamically by creating a temporary fleet member
        int cost;
        FleetMemberAPI tempMember;
        try {
            tempMember = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
            cost = (int) tempMember.getBaseValue();
        } catch (Exception e) {
            // If we can't create the fleet member, use default cost
            Global.getLogger(this.getClass()).warn("Could not retrieve cost for variant: " + variantId, e);
            dialog.getTextPanel().addPara("Error: Could not find ship variant '" + variantId + "'.");
            return false;
        }

        // Check if player has enough credits
        if (Global.getSector().getPlayerFleet().getCargo().getCredits().get() < cost) {
            dialog.getTextPanel().addPara("Insufficient credits. This ship costs " + Misc.getDGSCredits(cost) + ".");
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

        // Add the ship
        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(tempMember);

        return true;
    }
}