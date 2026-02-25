package levianeer.draconis.data.campaign.intel.events.crisis;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import org.apache.log4j.Logger;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseHostileActivityCause2;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.TriTachyonStandardActivityCause.CompetitorData;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.MapParams;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;

public class DraconisFleetStandardActivityCause extends BaseHostileActivityCause2 {

    private static final Logger log = Global.getLogger(DraconisFleetStandardActivityCause.class);

    public static List<CompetitorData> computePlayerCompetitionData() {
        List<CompetitorData> result = new ArrayList<>();

        int minCompetitorProduction = Global.getSettings().getInt("draconisMinCompetitorProduction");
        int minCompetitorMarketSize = Global.getSettings().getInt("draconisMinCompetitorMarketSize");

        // Just check player markets for heavy armaments production
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!Factions.PLAYER.equals(market.getFactionId())) continue;
            if (market.getSize() < minCompetitorMarketSize) continue;

            for (CommodityOnMarketAPI com : market.getCommoditiesCopy()) {
                // Check for heavy armaments (produced by heavy industry/orbital works)
                if (!Commodities.HAND_WEAPONS.equals(com.getId())) continue;

                int prod = com.getMaxSupply();
                if (prod < minCompetitorProduction) continue;

                // Found qualifying production - create a CompetitorData for it
                CompetitorData data = new CompetitorData(com.getId());
                data.competitorMaxProd = prod;
                data.competitorMaxMarketSize = market.getSize();
                data.competitorProducers.add(market);
                data.competitorProdTotal = prod;

                result.add(data);
            }
        }

        return result;
    }

    public DraconisFleetStandardActivityCause(HostileActivityEventIntel intel) {
        super(intel);
    }

    @Override
    public TooltipCreator getTooltip() {
        return new BaseFactorTooltip() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                float opad = 10f;

                Color h = Misc.getHighlightColor();
                Color n = Misc.getNegativeHighlightColor();

                tooltip.addPara("Your production of heavy armaments threatens the Draconis Alliance's "
                        + "market share on military equipment, drawing their hostile attention.", 0f);

                int minProd = Global.getSettings().getInt("draconisMinCompetitorProduction");

                tooltip.addPara("Event progress increases with heavy armaments production. "
                                + "%s below %s per colony will avoid "
                                + "antagonizing the Draconis Alliance.", opad,
                        h, "Keeping production", "" + minProd);

                tooltip.addPara("The Draconis Alliance's main production facilities rely on advanced "
                                + "nanoforge technology. Disrupting these could shift the balance of power.", opad,
                        h, "nanoforge technology");
            }
        };
    }

    @Override
    public boolean shouldShow() {
        return getProgress() != 0;
    }

    public int getProgress() {
        if (DraconisFleetHostileActivityFactor.meetsResetConditions() ||
                DraconisFleetHostileActivityFactor.isPlayerDefeatedDraconisAttack()) {
            return 0;
        }

        int total = 0;
        float prodProgressMult = Global.getSettings().getFloat("draconisCompetitionProgressRate");
        List<CompetitorData> comp = computePlayerCompetitionData();

        if (log.isDebugEnabled()) {
            log.debug("Draconis: === Computing Progress ===");
            log.debug("Draconis: Competitor data entries: " + comp.size());
        }

        for (CompetitorData data : comp) {
            int progress = data.getProgress(prodProgressMult);
            total += progress;
            if (log.isDebugEnabled()) log.debug("Draconis:   Market progress: " + progress);
        }

        if (log.isDebugEnabled()) log.debug("Draconis: Total progress: " + total);
        return total;
    }

    public String getDesc() {
        return "Competing exports";
    }

    public float getMagnitudeContribution(StarSystemAPI system) {
        // Compute once and reuse for both the progress check and magnitude calculation
        if (DraconisFleetHostileActivityFactor.meetsResetConditions() ||
                DraconisFleetHostileActivityFactor.isPlayerDefeatedDraconisAttack()) {
            return 0f;
        }

        List<CompetitorData> comp = computePlayerCompetitionData();
        if (comp.isEmpty()) return 0f;

        float mag = 0f;
        float perUnitMagnitude = Global.getSettings().getFloat("draconisCompetitionMagnitude");
        float maxMagnitude = Global.getSettings().getFloat("draconisCompetitionMaxMagnitude");

        for (CompetitorData data : comp) {
            for (MarketAPI market : data.competitorProducers) {
                if (market.getContainingLocation() == system) {
                    CommodityOnMarketAPI com = market.getCommodityData(data.commodityId);
                    float prod = com.getMaxSupply();
                    float contribution = prod * perUnitMagnitude;
                    mag += contribution;
                    if (log.isDebugEnabled()) {
                        log.debug("Draconis:     " + market.getName() + " production: " + prod + " -> magnitude: " + contribution);
                    }
                }
            }
        }

        boolean capped = mag > maxMagnitude;
        if (capped) mag = maxMagnitude;

        return Math.round(mag * 100f) / 100f;
    }
}