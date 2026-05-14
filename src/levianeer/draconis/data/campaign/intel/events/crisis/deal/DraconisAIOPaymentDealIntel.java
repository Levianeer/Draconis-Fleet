package levianeer.draconis.data.campaign.intel.events.crisis.deal;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.econ.MonthlyReport.FDNode;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.intel.events.crisis.AIOStrings;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.Set;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Intel entry tracking an active payment deal with the DDA Intelligence Office.
 * <p>
 * While active, the AIO monthly tick is suppressed. The deal deducts credits
 * automatically via the monthly finance report. Monthly cost scales dynamically
 * with the player's current colony count.
 * <p>
 * Cancellation requires returning to a DDA bar - there is no cancel button here.
 */
public class DraconisAIOPaymentDealIntel extends BaseIntelPlugin implements EconomyTickListener, TooltipCreator {

    private static final Logger log = Global.getLogger(DraconisAIOPaymentDealIntel.class);

    public static final String KEY = "$dda_aio_deal_ref";

    // ==================== Static helpers ====================

    public static DraconisAIOPaymentDealIntel get() {
        if (Global.getSector() == null) return null;
        Object o = Global.getSector().getMemoryWithoutUpdate().get(KEY);
        if (o instanceof DraconisAIOPaymentDealIntel d && !d.isEnded()) return d;
        // readResolve() is not called by XStream for non-Serializable classes (BaseIntelPlugin does not implement Serializable).
        // Fall back to the intel manager and restore the key.
        IntelInfoPlugin found = Global.getSector().getIntelManager().getFirstIntel(DraconisAIOPaymentDealIntel.class);
        if (found instanceof DraconisAIOPaymentDealIntel d && !d.isEnded()) {
            Global.getSector().getMemoryWithoutUpdate().set(KEY, d);
            return d;
        }
        return null;
    }

    // ==================== Construction ====================

    public DraconisAIOPaymentDealIntel() {
        Global.getSector().getMemoryWithoutUpdate().set(KEY, this);
        // Persistent (no second arg = false = not transient) - survives save/load
        Global.getSector().getListenerManager().addListener(this);
        Global.getSector().getIntelManager().addIntel(this, false);
        Global.getSector().addScript(this);
        setImportant(true);
        log.info("DDA: AIO payment deal started - monthly rate=" + computeCurrentMonthlyPayment());
    }

    // ==================== Cost computation ====================

    /**
     * Computes the current monthly cost based on AI cores installed across all player colonies.
     * Gamma cores count as 1×base, beta as 2×base, alpha as 3×base.
     * Minimum is base (1 gamma-equivalent) if no cores are deployed.
     * Called fresh each tick so cost scales dynamically with deployment.
     */
    public static float computeCurrentMonthlyPayment() {
        float base = Global.getSettings().getFloat("draconisAIOPaymentCost");
        int totalWeight = 0;
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!Factions.PLAYER.equals(m.getFactionId())) continue;
            for (Industry industry : m.getIndustries()) {
                if (industry == null) continue;
                String coreId = industry.getAICoreId();
                if (coreId == null || coreId.isEmpty()) continue;
                if (Commodities.ALPHA_CORE.equals(coreId)) totalWeight += 3;
                else if (Commodities.BETA_CORE.equals(coreId)) totalWeight += 2;
                else totalWeight += 1; // gamma
            }
        }
        return base * Math.max(1, totalWeight);
    }

    /** Counts installed AI cores by tier across all player colonies for display purposes. */
    private int[] countCoresByTier() {
        // returns [gamma, beta, alpha]
        int[] counts = new int[3];
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!Factions.PLAYER.equals(m.getFactionId())) continue;
            for (Industry industry : m.getIndustries()) {
                if (industry == null) continue;
                String coreId = industry.getAICoreId();
                if (coreId == null || coreId.isEmpty()) continue;
                if (Commodities.ALPHA_CORE.equals(coreId)) counts[2]++;
                else if (Commodities.BETA_CORE.equals(coreId)) counts[1]++;
                else counts[0]++;
            }
        }
        return counts;
    }

    // ==================== EconomyTickListener ====================

    @Override
    public void reportEconomyTick(int iterIndex) {
        float numIter = Global.getSettings().getFloat("economyIterPerMonth");
        float perTick = computeCurrentMonthlyPayment() / numIter;

        MonthlyReport report = SharedData.getData().getCurrentReport();
        FDNode outpostsNode = report.getNode(MonthlyReport.OUTPOSTS);
        if (outpostsNode.name == null) {
            outpostsNode.name = "Colonies";
            outpostsNode.custom = MonthlyReport.OUTPOSTS;
            outpostsNode.tooltipCreator = report.getMonthlyReportTooltip();
        }

        FDNode dealNode = report.getNode(outpostsNode, "dda_aio_payment");
        dealNode.name = AIOStrings.DEAL_MONTHLY_REPORT_NODE_NAME;
        dealNode.upkeep += perTick;
        dealNode.tooltipCreator = this;

        FactionAPI faction = Global.getSector().getFaction(DRACONIS);
        if (faction != null) dealNode.icon = faction.getCrest();
    }

    @Override
    public void reportEconomyMonthEnd() {
        // no-op
    }

    // ==================== Lifecycle ====================

    @Override
    protected void notifyEnding() {
        super.notifyEnding();
        Global.getSector().getMemoryWithoutUpdate().unset(KEY);
        Global.getSector().getListenerManager().removeListener(this);
        log.info("DDA: AIO payment deal ended");
    }

    @Override
    protected void notifyEnded() {
        super.notifyEnded();
        Global.getSector().removeScript(this);
    }

    // ==================== Intel panel ====================

    @Override
    public String getName() {
        return AIOStrings.INTEL_NAME_DEAL;
    }

    @Override
    public String getIcon() {
        FactionAPI f = Global.getSector().getFaction(DRACONIS);
        return f != null ? f.getCrest() : Global.getSettings().getSpriteName("intel", "dda");
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(com.fs.starfarer.api.impl.campaign.ids.Tags.INTEL_COLONIES);
        tags.add(DRACONIS);
        return tags;
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        Color pos = Misc.getPositiveHighlightColor();
        Color neg = Misc.getNegativeHighlightColor();

        FactionAPI f = Global.getSector().getFaction(DRACONIS);
        Color fc = f != null ? f.getBaseUIColor() : h;

        info.addPara(AIOStrings.DEAL_DESC_PARA1_FMT, opad, fc, AIOStrings.DEAL_DESC_PARA1_HIGHLIGHT);

        float monthly = computeCurrentMonthlyPayment();
        info.addPara(AIOStrings.DEAL_DESC_PARA2_FMT, opad, neg, Misc.getWithDGS((long) monthly) + " credits");

        info.addPara(AIOStrings.DEAL_DESC_PARA3_FMT, opad, pos, AIOStrings.DEAL_DESC_PARA3_HIGHLIGHT);

        info.addPara(AIOStrings.DEAL_DESC_PARA4_FMT, opad, fc, AIOStrings.DEAL_DESC_PARA4_HIGHLIGHT);
    }

    // ==================== TooltipCreator ====================

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
        Color h = Misc.getHighlightColor();
        Color neg = Misc.getNegativeHighlightColor();

        int[] cores = countCoresByTier();
        int gamma = cores[0], beta = cores[1], alpha = cores[2];
        float base = Global.getSettings().getFloat("draconisAIOPaymentCost");

        tooltip.addPara(AIOStrings.DEAL_TOOLTIP_PARA1, 0f);

        tooltip.addPara(AIOStrings.DEAL_TOOLTIP_PARA2_FMT, 10f, h,
                Misc.getDGSCredits((long) base),
                Misc.getDGSCredits((long) (base * 2)),
                Misc.getDGSCredits((long) (base * 3)));

        if (gamma > 0 || beta > 0 || alpha > 0) {
            String breakdown = buildCoreBreakdown(gamma, beta, alpha);
            tooltip.addPara(String.format(AIOStrings.DEAL_TOOLTIP_PARA3_FMT, breakdown), 10f);
        } else {
            tooltip.addPara(AIOStrings.DEAL_TOOLTIP_PARA4, 10f, neg, AIOStrings.DEAL_TOOLTIP_PARA4_HIGHLIGHT);
        }
    }

    @Override
    public float getTooltipWidth(Object tooltipParam) {
        return 450f;
    }

    @Override
    public boolean isTooltipExpandable(Object tooltipParam) {
        return false;
    }

    private String buildCoreBreakdown(int gamma, int beta, int alpha) {
        StringBuilder sb = new StringBuilder();
        if (alpha > 0) sb.append(alpha).append(" alpha");
        if (beta > 0) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(beta).append(" beta");
        }
        if (gamma > 0) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(gamma).append(" gamma");
        }
        return sb.toString();
    }

}
