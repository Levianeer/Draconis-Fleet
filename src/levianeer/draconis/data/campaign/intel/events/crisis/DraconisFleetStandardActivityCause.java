package levianeer.draconis.data.campaign.intel.events.crisis;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
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
                Color tc = Misc.getTextColor();

                tooltip.addPara("Your production of heavy armaments threatens the Draconis Alliance's "
                        + "market share on advanced military equipment, drawing their hostile attention.", 0f);

                List<CompetitorData> comp = computePlayerCompetitionData();
                FactionAPI player = Global.getSector().getFaction(Factions.PLAYER);

                tooltip.beginTable(player, 20f, "Commodity", getTooltipWidth(tooltipParam) - 150f, "Production", 150f);
                for (final CompetitorData data : comp) {
                    tooltip.addRow(Alignment.LMID, tc, Misc.ucFirst(data.spec.getLowerCaseName()),
                            Alignment.MID, h, "" + data.competitorMaxProd);
                }
                tooltip.addTable("", 0, opad);
                tooltip.addSpacer(5f);

                int minProd = Global.getSettings().getInt("draconisMinCompetitorProduction");

                tooltip.addPara("Event progress increases with heavy armaments production. "
                                + "%s below %s per colony will avoid "
                                + "antagonizing the Draconis Alliance.", opad,
                        h, "Keeping production", "" + minProd);

                tooltip.addPara("Alternatively, obtaining a commission with the Draconis Alliance and achieving "
                                + "Cooperative reputation will grant you honorary membership status and end hostilities.", opad,
                        h, "commission", "Cooperative");

                tooltip.addPara("The Draconis Alliance's main production facilities rely on advanced "
                                + "forge technology. Disrupting these could shift the balance of power.", opad,
                        h, "forge technology");

                MarketAPI homeworld = DraconisFleetHostileActivityFactor.getDraconisHomeworld();
                if (homeworld != null && homeworld.getStarSystem() != null) {
                    MapParams params = new MapParams();
                    params.showSystem(homeworld.getStarSystem());
                    float w = tooltip.getWidthSoFar();
                    float ht = Math.round(w / 1.6f);
                    params.positionToShowAllMarkersAndSystems(true, Math.min(w, ht));
                    UIPanelAPI map = tooltip.createSectorMap(w, ht, params, homeworld.getName() + " (" + homeworld.getStarSystem().getNameWithLowercaseTypeShort() + ")");
                    tooltip.addCustom(map, opad);
                }
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
            Global.getLogger(this.getClass()).info("Progress blocked by reset conditions");
            return 0;
        }

        int total = 0;
        float prodProgressMult = Global.getSettings().getFloat("draconisCompetitionProgressRate");
        List<CompetitorData> comp = computePlayerCompetitionData();

        Global.getLogger(this.getClass()).info("=== Computing Progress ===");
        Global.getLogger(this.getClass()).info("Competitor data entries: " + comp.size());

        for (CompetitorData data : comp) {
            int progress = data.getProgress(prodProgressMult);
            total += progress;
            Global.getLogger(this.getClass()).info("  Market progress: " + progress);
        }

        Global.getLogger(this.getClass()).info("Total progress: " + total);
        return total;
    }

    public String getDesc() {
        return "Competing heavy armaments production";
    }

    public float getMagnitudeContribution(StarSystemAPI system) {
        if (getProgress() <= 0) return 0f;

        List<CompetitorData> comp = computePlayerCompetitionData();
        float mag = 0f;

        float perUnitMagnitude = Global.getSettings().getFloat("draconisCompetitionMagnitude");
        float maxMagnitude = Global.getSettings().getFloat("draconisCompetitionMaxMagnitude");

        for (CompetitorData data : comp) {
            for (MarketAPI market : data.competitorProducers) {
                if (market.getContainingLocation() == system) {
                    CommodityOnMarketAPI com = market.getCommodityData(data.commodityId);
                    float prod = com.getMaxSupply();
                    mag += prod * perUnitMagnitude;
                }
            }
        }
        if (mag > maxMagnitude) mag = maxMagnitude;

        mag = Math.round(mag * 100f) / 100f;

        return mag;
    }
}