package levianeer.draconis.data.campaign.intel.events.aicore;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseHostileActivityCause2;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;

import java.util.List;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Hostile activity cause for Draconis AI Core acquisition raids
 * Shows as a sub-item under "Draconis Alliance" in the colony crisis monthly report
 */
public class DraconisAICoreActivityCause extends BaseHostileActivityCause2 {

    public static int IGNORE_COLONY_THRESHOLD = 3;  // Same as Hegemony

    public DraconisAICoreActivityCause(HostileActivityEventIntel intel) {
        super(intel);
    }

    @Override
    public TooltipCreator getTooltip() {
        return new BaseFactorTooltip() {
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                float opad = 10f;
                tooltip.addPara("The Draconis Alliance is known to conduct covert operations to acquire %s " +
                        "from rival colonies. They are unlikely to take notice of colonies of size %s or smaller.", 0f,
                        Misc.getHighlightColor(), "AI cores", "" + IGNORE_COLONY_THRESHOLD);

                tooltip.addPara("These raids are conducted by Shadow Fleet operatives and aim to steal " +
                        "AI cores for integration into Draconis military and industrial infrastructure.", opad);
            }
        };
    }

    @Override
    public boolean shouldShow() {
        return getProgress() > 0;
    }

    @Override
    public int getProgress() {
        int progress = (int) Math.round(getTotalAICorePoints());

        // Apply diminishing returns (similar to Hegemony's system)
        float unit = 50f;  // Base unit
        float mult = 0.85f;  // Reduction multiplier per unit

        int rem = progress;
        float adjusted = 0;
        while (rem > unit) {
            adjusted += unit;
            rem -= unit;
            rem *= mult;
        }
        adjusted += rem;

        int reduced = Math.round(adjusted);
        if (progress > 0 && reduced < 1) reduced = 1;

        return reduced;
    }

    @Override
    public String getDesc() {
        return "AI core acquisition operations";
    }

    public float getTotalAICorePoints() {
        float total = 0f;
        for (StarSystemAPI system : Misc.getPlayerSystems(false)) {
            total += getAICorePoints(system);
        }
        return total;
    }

    public static float getAICorePoints(StarSystemAPI system) {
        float total = 0f;
        List<MarketAPI> markets = Misc.getMarketsInLocation(system, com.fs.starfarer.api.impl.campaign.ids.Factions.PLAYER);
        for (MarketAPI market : markets) {
            if (market.getSize() <= IGNORE_COLONY_THRESHOLD) continue;
            float interest = getAICorePoints(market);
            total += interest;
        }
        return total;
    }

    public static float getAICorePoints(MarketAPI market) {
        float total = 0f;

        // Points per AI core type - balanced to Hegemony levels
        // Hegemony: Admin 10, Alpha 4, Beta 2, Gamma 1
        // Draconis uses slightly lower values (they're more focused on theft than inspection)
        float admin = 6f;
        float alpha = 3f;
        float beta = 1.5f;
        float gamma = 0.75f;

        // Check admin AI core
        if (market.getAdmin() != null && market.getAdmin().getAICoreId() != null) {
            total += admin;
        }

        // Check industry AI cores
        for (Industry ind : market.getIndustries()) {
            String core = ind.getAICoreId();
            if (Commodities.ALPHA_CORE.equals(core)) {
                total += alpha;
            } else if (Commodities.BETA_CORE.equals(core)) {
                total += beta;
            } else if (Commodities.GAMMA_CORE.equals(core)) {
                total += gamma;
            }
        }

        return total;
    }

    @Override
    public float getMagnitudeContribution(StarSystemAPI system) {
        if (getProgress() <= 0) return 0f;

        List<MarketAPI> markets = Misc.getMarketsInLocation(system, com.fs.starfarer.api.impl.campaign.ids.Factions.PLAYER);

        float total = 0f;
        for (MarketAPI market : markets) {
            float points = getAICorePoints(market);
            total += points;
        }

        // Scale down the contribution slightly (raids are less frequent than regular hostile activity)
        total = Math.round(total * 0.3f * 100f) / 100f;

        return total;
    }
}