package levianeer.draconis.data.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator;
import levianeer.draconis.data.campaign.intel.fafnir.FafnirAccessStrings;

/**
 * Creator for the Fafnir Ring-Port contractor bar event.
 * Fires at pirate or independent markets when the player has not yet started or
 * completed the Ring-Port path.
 */
public class XLII_FafnirRingPortBarEventCreator extends BaseBarEventCreator {

    @Override
    public float getBarEventFrequencyWeight() {
        if (!isTriggerConditionMet()) return 0f;
        return super.getBarEventFrequencyWeight();
    }

    @Override
    public PortsideBarEvent createBarEvent() {
        // Clear bar snapshots so the event can appear on the next market visit
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (Factions.PLAYER.equals(market.getFactionId())) {
                market.getMemoryWithoutUpdate().unset("$BarCMD_shownEvents");
            }
        }
        return new XLII_FafnirRingPortBarEvent();
    }

    @Override
    public String getBarEventId() {
        return "XLII_fafnir_ring_port";
    }

    @Override
    public boolean isPriority() {
        return false;
    }

    @Override
    public float getBarEventTimeoutDuration() {
        return 10000000000f;
    }

    @Override
    public float getBarEventAcceptedTimeoutDuration() {
        return 10000000000f;
    }

    // -------------------------------------------------------------------------
    // Shared condition check
    // -------------------------------------------------------------------------

    /**
     * Conditions: player has not already accepted or completed the Ring-Port quest,
     * Fafnir access is not yet granted, and player has Neutral or better rep with
     * Pirates (>= -0.09).
     */
    static boolean isTriggerConditionMet() {
        if ("XLII_draconis".equals(Misc.getCommissionFactionId())) return false;
        var mem = Global.getSector().getMemoryWithoutUpdate();
        if (mem.getBoolean(FafnirAccessStrings.MEM_RP_QUEST_ACTIVE))  return false;
        if (mem.getBoolean(FafnirAccessStrings.MEM_ACCESS_GRANTED))   return false;

        float rep = Global.getSector().getFaction(Factions.PIRATES)
                .getRelationship(Factions.PLAYER);
        return rep >= -0.09f;
    }
}
