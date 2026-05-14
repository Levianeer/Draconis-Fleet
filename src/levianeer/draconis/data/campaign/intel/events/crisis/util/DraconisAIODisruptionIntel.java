package levianeer.draconis.data.campaign.intel.events.crisis.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.intel.events.crisis.AIOStrings;

import java.awt.*;
import java.util.Set;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Intel notification shown when the AIO Tracker disrupts an AI-core industry at a player colony.
 * Mirrors the pattern of DraconisAICoreTheftIntel - a short-lived BaseIntelPlugin entry.
 */
public class DraconisAIODisruptionIntel extends BaseIntelPlugin {

    private final MarketAPI market;
    private final String industryName;
    private final int disruptionDays;
    private final long disruptionDate;

    public DraconisAIODisruptionIntel(MarketAPI market, String industryName, int disruptionDays) {
        this.market = market;
        this.industryName = industryName;
        this.disruptionDays = disruptionDays;
        this.disruptionDate = Global.getSector().getClock().getTimestamp();
        Global.getSector().getIntelManager().addIntel(this, false);
        Global.getSector().addScript(this);
    }

    @Override
    public String getName() {
        return industryName + AIOStrings.INTEL_NAME_DISRUPTION_SUFFIX;
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "XLII_security_codes");
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return Global.getSector().getFaction(DRACONIS);
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_COLONIES);
        tags.add(DRACONIS);
        return tags;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return market.getPrimaryEntity();
    }

    @Override
    protected void notifyEnded() {
        super.notifyEnded();
        Global.getSector().removeScript(this);
    }

    /**
     * Drives expiry via timestamp comparison so the duration is save-safe.
     * endImmediately() ensures notifyEnded() fires for script cleanup.
     */
    @Override
    public boolean shouldRemoveIntel() {
        if (isEnded()) return true;
        float elapsed = Global.getSector().getClock().getElapsedDaysSince(disruptionDate);
        if (elapsed >= disruptionDays) {
            endImmediately(); // triggers notifyEnded() -> removeScript
            return true;
        }
        return false;
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate,
                                   Color tc, float initPad) {
        Color bad = Misc.getNegativeHighlightColor();
        Color h   = Misc.getHighlightColor();
        info.addPara(AIOStrings.DISRUPTION_BULLET_FMT, initPad, tc, bad, market.getName());
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        Color h   = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();
        float opad = 10f;

        FactionAPI dda = Global.getSector().getFaction(DRACONIS);
        if (dda != null) {
            info.addImage(dda.getLogo(), width, 128, opad);
        }

        LabelAPI para = info.addPara(
                AIOStrings.DISRUPTION_DESC_PARA1_FMT,
                opad, h, industryName, market.getName(), String.valueOf(disruptionDays));
        para.setHighlightColors(bad, h, bad);

        info.addPara(AIOStrings.DISRUPTION_DESC_PARA2, opad);

        if (disruptionDate > 0) {
            info.addPara(Misc.getAgoStringForTimestamp(disruptionDate) + ".", opad);
        }
    }

    @Override
    public String getSortString() {
        return "Colonies";
    }
}
