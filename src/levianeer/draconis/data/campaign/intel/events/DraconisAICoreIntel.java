package levianeer.draconis.data.campaign.intel.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.Set;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Intel notification when Draconis intelligence identifies AI cores on enemy markets
 */
public class DraconisAICoreIntel extends BaseIntelPlugin {

    private final MarketAPI targetMarket;
    private final DraconisAICoreTargetingMonitor.AICoreIntelData coreData;
    private final long discoveryDate;

    public DraconisAICoreIntel(MarketAPI market, DraconisAICoreTargetingMonitor.AICoreIntelData data) {
        this.targetMarket = market;
        this.coreData = data;
        this.discoveryDate = Global.getSector().getClock().getTimestamp();
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate,
                                   Color tc, float initPad) {
        float pad = 0f;

        FactionAPI draconisFaction = Global.getSector().getFaction(DRACONIS);
        FactionAPI targetFaction = targetMarket.getFaction();

        // Draconis
        info.addPara("Faction: %s", initPad, tc,
                draconisFaction.getBaseUIColor(),
                draconisFaction.getDisplayName());

        // Target Faction
        info.addPara("Target: %s", pad, tc,
                targetFaction.getBaseUIColor(),
                targetFaction.getDisplayName());

        // Cores found
        String coresText = coreData.totalCores + " AI core" + (coreData.totalCores != 1 ? "s" : "") + " detected";
        info.addPara(coresText, pad, tc, Misc.getHighlightColor(), String.valueOf(coreData.totalCores));
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        Color h = Misc.getHighlightColor();
        Color n = Misc.getNegativeHighlightColor();

        float opad = 10f;

        // Show both faction logos side by side
        FactionAPI draconisFaction = Global.getSector().getFaction(DRACONIS);
        info.addImages(width, 96, opad, opad,
                draconisFaction.getCrest(),
                targetMarket.getFaction().getCrest());

        info.addPara("Draconis intelligence operatives have confirmed the presence of AI cores at %s, " +
                        "a %s colony in the %s.", opad,
                h, targetMarket.getName(),
                targetMarket.getFaction().getDisplayName(),
                targetMarket.getStarSystem().getNameWithLowercaseType());

        // Core breakdown
        info.addSectionHeading("Confirmed AI Core Deployments",
                draconisFaction.getBaseUIColor(),
                draconisFaction.getDarkUIColor(),
                com.fs.starfarer.api.ui.Alignment.MID, opad);

        if (coreData.alphaCores > 0) {
            info.addPara("• Alpha Cores: %s", 3f, h, String.valueOf(coreData.alphaCores));
        }
        if (coreData.betaCores > 0) {
            info.addPara("• Beta Cores: %s", 3f, h, String.valueOf(coreData.betaCores));
        }
        if (coreData.gammaCores > 0) {
            info.addPara("• Gamma Cores: %s", 3f, h, String.valueOf(coreData.gammaCores));
        }

        // Strategic assessment
        info.addSectionHeading("Strategic Assessment",
                draconisFaction.getBaseUIColor(),
                draconisFaction.getDarkUIColor(),
                com.fs.starfarer.api.ui.Alignment.MID, opad);

        String threat = getCoreThreatLevel();
        Color threatColor = getThreatColor();

        info.addPara("The concentration of AI cores represents a %s strategic threat to Draconis interests. " +
                        "These advanced computing systems enhance enemy industrial output and military capabilities. " +
                        "Additionally, they represent valuable salvage opportunities.", opad,
                threatColor, threat);

        info.addPara("Draconis High Command has designated this market as a %s for strategic operations. " +
                        "The acquisition of these AI cores would significantly strengthen our technological position " +
                        "while weakening enemy infrastructure.", opad,
                n, "priority target");

        // Industries with cores
        if (!coreData.industries.isEmpty()) {
            info.addSectionHeading("Confirmed Installations",
                    draconisFaction.getBaseUIColor(),
                    draconisFaction.getDarkUIColor(),
                    com.fs.starfarer.api.ui.Alignment.MID, opad);

            for (String industryName : coreData.industries) {
                info.addPara("• " + industryName, 3f);
            }
        }

        // Call to action
        info.addPara("Intelligence recommends increased military focus on this target. " +
                "Any successful raid should prioritize AI core extraction over conventional objectives.", opad);
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "XLII_ai_found"); // DAT SHIT IS MINE
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add("Military");
        tags.add(DRACONIS);  // Draconis flag
        tags.add(targetMarket.getFactionId());  // Target flag
        return tags;
    }

    @Override
    public String getSortString() {
        return "AI Core Intelligence";
    }

    @Override
    public String getName() {
        return "AI Cores Detected: " + targetMarket.getName();
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return Global.getSector().getFaction(DRACONIS);
    }

    @Override
    public boolean isEnding() {
        // Intel expires if market no longer has cores or is no longer accessible
        return !targetMarket.isInEconomy() ||
                !targetMarket.getMemoryWithoutUpdate().getBoolean(DraconisAICoreTargetingMonitor.AI_CORE_TARGET_FLAG);
    }

    @Override
    public boolean isEnded() {
        return isEnding();
    }

    @Override
    public boolean shouldRemoveIntel() {
        return isEnding();
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return targetMarket.getPrimaryEntity();
    }

    /**
     * Determines threat level based on core quantity and quality
     */
    private String getCoreThreatLevel() {
        int totalValue = (coreData.alphaCores * 3) + (coreData.betaCores * 2) + coreData.gammaCores;

        if (totalValue >= 10) return "critical";
        if (totalValue >= 6) return "significant";
        if (totalValue >= 3) return "moderate";
        return "minor";
    }

    /**
     * Gets color for threat level
     */
    private Color getThreatColor() {
        String threat = getCoreThreatLevel();
        return switch (threat) {
            case "critical" -> Misc.getNegativeHighlightColor();
            case "significant" -> new Color(255, 150, 0);
            case "moderate" -> Misc.getHighlightColor();
            default -> Misc.getTextColor();
        };
    }
}