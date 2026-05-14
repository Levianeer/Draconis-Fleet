package levianeer.draconis.data.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator;
import levianeer.draconis.data.campaign.intel.fafnir.FafnirAccessStrings;

/**
 * Creator for the Fafnir TT Courier bar event.
 * Fires at Tri-Tachyon markets when the player has Welcoming rep or a TT commission,
 * and has not yet started or completed the TT Courier path.
 */
public class XLII_FafnirTTBarEventCreator extends BaseBarEventCreator {

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
        return new XLII_FafnirTTBarEvent();
    }

    @Override
    public String getBarEventId() {
        return "XLII_fafnir_tt_courier";
    }

    @Override
    public boolean isPriority() {
        return true;
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
     * Conditions: player has not already accepted or completed the TT quest, Fafnir
     * access is not yet granted, and player has Neutral or better rep with TT (>= -0.09).
     */
    static boolean isTriggerConditionMet() {
        if ("XLII_draconis".equals(Misc.getCommissionFactionId())) return false;
        var mem = Global.getSector().getMemoryWithoutUpdate();
        if (mem.getBoolean(FafnirAccessStrings.MEM_TT_QUEST_ACTIVE)) return false;
        if (mem.getBoolean(FafnirAccessStrings.MEM_ACCESS_GRANTED))   return false;

        float rep = Global.getSector().getFaction(Factions.TRITACHYON)
                .getRelationship(Factions.PLAYER);
        return rep >= -0.09f;
    }
}
