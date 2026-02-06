package levianeer.draconis.data.campaign.intel.events.crisis;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseHostileActivityCause2;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Colony crisis cause based on player's usage of AI cores
 * Draconis views player's AI cores as strategic assets worth acquiring
 */
public class DraconisAICoreActivityCause extends BaseHostileActivityCause2 {
    private static final Logger log = Global.getLogger(DraconisAICoreActivityCause.class);

    public static class AICoreData {
        public MarketAPI market;
        public List<String> industries = new ArrayList<>();
        public int alphaCores = 0;
        public int betaCores = 0;
        public int gammaCores = 0;
        public int totalCores = 0;

        public int getProgress(float progressPerCore) {
            // Alpha cores worth more, gamma worth less
            float weightedCores = (alphaCores * 3f) + (betaCores * 2f) + (gammaCores * 1f);
            return Math.round(weightedCores * progressPerCore);
        }
    }

    public static List<AICoreData> computePlayerAICoreData() {
        List<AICoreData> result = new ArrayList<>();

        float minThreshold = Global.getSettings().getFloat("draconisMinAICoreThreshold");

        // Scan all player markets for AI cores
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!Factions.PLAYER.equals(market.getFactionId())) continue;

            AICoreData data = scanMarketCores(market);
            if (data.totalCores > 0) {
                // Calculate weighted cores: Alpha=3, Beta=2, Gamma=1
                float weightedCores = (data.alphaCores * 3f) + (data.betaCores * 2f) + (data.gammaCores * 1f);

                // Only add markets that meet the minimum threshold
                if (weightedCores >= minThreshold) {
                    result.add(data);
                }
            }
        }

        return result;
    }

    private static AICoreData scanMarketCores(MarketAPI market) {
        AICoreData data = new AICoreData();
        data.market = market;

        List<Industry> industries = market.getIndustries();
        if (industries == null) return data;

        for (Industry industry : industries) {
            if (industry == null) continue;

            String coreId = industry.getAICoreId();
            if (coreId == null || coreId.isEmpty()) continue;

            data.totalCores++;
            switch (coreId) {
                case Commodities.ALPHA_CORE:
                    data.alphaCores++;
                    data.industries.add(industry.getCurrentName() + " (Alpha)");
                    break;
                case Commodities.BETA_CORE:
                    data.betaCores++;
                    data.industries.add(industry.getCurrentName() + " (Beta)");
                    break;
                case Commodities.GAMMA_CORE:
                    data.gammaCores++;
                    data.industries.add(industry.getCurrentName() + " (Gamma)");
                    break;
            }
        }

        return data;
    }

    public DraconisAICoreActivityCause(HostileActivityEventIntel intel) {
        super(intel);
    }

    @Override
    public TooltipMakerAPI.TooltipCreator getTooltip() {
        return new BaseFactorTooltip() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                float opad = 10f;
                Color h = Misc.getHighlightColor();
                Color tc = Misc.getTextColor();

                tooltip.addPara("Your use of AI cores has drawn the attention of the Draconis Alliance. "
                        + "They view these strategic assets as worth acquiring through military force.", 0f);

                int minThreshold = (int) Global.getSettings().getFloat("draconisMinAICoreThreshold");

                tooltip.addPara("Event progress increases with AI core usage. "
                                + "Keeping the total value of %s below %s per colony will avoid "
                                + "antagonizing the Draconis Alliance. ", opad,
                        h, "AI cores", "" + minThreshold);
            }
        };
    }

    @Override
    public boolean shouldShow() {
        return getProgress() != 0;
    }

    @Override
    public int getProgress() {
        // Stop progress if crisis is defeated or reset conditions met
        if (DraconisFleetHostileActivityFactor.meetsResetConditions() ||
                DraconisFleetHostileActivityFactor.isPlayerDefeatedDraconisAttack()) {
            log.info("Draconis: AI Core progress blocked by reset conditions");
            return 0;
        }

        int total = 0;
        float progressRate = Global.getSettings().getFloat("draconisAICoreProgressRate");
        List<AICoreData> coreData = computePlayerAICoreData();

        log.info("Draconis: === Computing AI Core Progress ===");
        log.info("Draconis: Markets with AI cores: " + coreData.size());

        for (AICoreData data : coreData) {
            int progress = data.getProgress(progressRate);
            total += progress;
            log.info("Draconis:   " + data.market.getName() + ": " + progress +
                    " (A:" + data.alphaCores + " B:" + data.betaCores + " G:" + data.gammaCores + ")");
        }

        log.info("Draconis: Total AI core progress: " + total);
        return total;
    }

    @Override
    public String getDesc() {
        return "Strategic AI core usage";
    }

    @Override
    public float getMagnitudeContribution(com.fs.starfarer.api.campaign.StarSystemAPI system) {
        if (getProgress() <= 0) {
            log.info("Draconis: AI Core magnitude for " + system.getName() + ": 0 (progress is 0)");
            return 0f;
        }

        float mag = 0f;
        float perCoreMagnitude = Global.getSettings().getFloat("draconisAICoreMagnitude");
        float maxMagnitude = Global.getSettings().getFloat("draconisAICoreMaxMagnitude");

        for (AICoreData data : computePlayerAICoreData()) {
            if (data.market.getContainingLocation() == system) {
                // Weighted magnitude: Alpha=3, Beta=2, Gamma=1
                float weightedCores = (data.alphaCores * 3f) + (data.betaCores * 2f) + (data.gammaCores * 1f);
                float contribution = weightedCores * perCoreMagnitude;
                mag += contribution;
                log.info("Draconis:     " + data.market.getName() + " cores (A:" + data.alphaCores +
                        " B:" + data.betaCores + " G:" + data.gammaCores + ") -> magnitude: " + contribution);
            }
        }

        boolean capped = mag > maxMagnitude;
        if (capped) mag = maxMagnitude;
        mag = Math.round(mag * 100f) / 100f;

        log.info("Draconis: AI Core magnitude for " + system.getName() + ": " + mag + (capped ? " (CAPPED)" : ""));

        return mag;
    }
}
