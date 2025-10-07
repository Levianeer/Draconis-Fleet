package levianeer.draconis.data.campaign.intel.aicore.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.intel.aicore.listener.DraconisAICoreTargetingMonitor;

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

        // Draconis faction name
        info.addPara(draconisFaction.getDisplayName(), initPad, tc,
                draconisFaction.getBaseUIColor(),
                draconisFaction.getDisplayName());

        // Target faction name
        info.addPara(targetFaction.getDisplayName(), pad, tc,
                targetFaction.getBaseUIColor(),
                targetFaction.getDisplayName());

        // Current relationship status (short form)
        float currentRep = draconisFaction.getRelationship(targetFaction.getId());
        String relation = getRelationStr(currentRep);
        Color relColor = draconisFaction.getRelColor(targetFaction.getId());
        String repString = String.format("%.0f/100 (%s)", currentRep * 100, relation);

        info.addPara("Now at " + repString, pad, tc, relColor, repString);
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        Color h = Misc.getHighlightColor();
        Color tc = Misc.getTextColor();

        float opad = 10f;

        FactionAPI draconisFaction = Global.getSector().getFaction(DRACONIS);
        FactionAPI targetFaction = targetMarket.getFaction();

        // Show both faction logos side by side (matching Nexerelin style)
        info.addImages(width, 96, opad, opad,
                draconisFaction.getLogo(),
                targetFaction.getLogo());

        // Main description
        String str = "Draconis intelligence operatives have confirmed the presence of AI cores at "
                + targetMarket.getName() + ", a " + targetFaction.getDisplayName()
                + " colony in the " + targetMarket.getStarSystem().getNameWithLowercaseType() + ".";

        LabelAPI para = info.addPara(str, opad);
        para.setHighlight(targetMarket.getName(), targetFaction.getDisplayName(),
                targetMarket.getStarSystem().getNameWithLowercaseType());
        para.setHighlightColors(h, targetFaction.getBaseUIColor(), h);

        // Relationship change section (matching Nexerelin format)
        info.addSectionHeading("Effects",
                draconisFaction.getBaseUIColor(),
                draconisFaction.getDarkUIColor(),
                com.fs.starfarer.api.ui.Alignment.MID, opad);

        addRelationshipChangePara(info, draconisFaction, targetFaction, opad);

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

        str = "The concentration of AI cores represents a " + threat
                + " strategic threat to Draconis interests. "
                + "These advanced computing systems enhance enemy industrial output and military capabilities. "
                + "Additionally, they represent valuable salvage opportunities.";

        para = info.addPara(str, opad);
        para.setHighlight(threat);
        para.setHighlightColors(threatColor);

        str = "Draconis High Command has designated this market as a priority target for strategic operations. "
                + "The acquisition of these AI cores would significantly strengthen our technological position "
                + "while weakening " + targetFaction.getDisplayName() + " infrastructure.";

        para = info.addPara(str, opad);
        para.setHighlight("priority target", targetFaction.getDisplayName());
        para.setHighlightColors(Misc.getNegativeHighlightColor(), targetFaction.getBaseUIColor());

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

        // Days ago timestamp
        if (discoveryDate > 0) {
            info.addPara(Misc.getAgoStringForTimestamp(discoveryDate) + ".", opad);
        }
    }

    /**
     * Adds a relationship change paragraph matching Nexerelin's format
     * NOTE: The reputation change was already applied when this intel was created
     */
    private void addRelationshipChangePara(TooltipMakerAPI info, FactionAPI faction1,
                                           FactionAPI faction2, float pad) {
        // Calculate reputation change (matches what was actually applied in DraconisAICoreTargetingMonitor)
        float repChange = -((coreData.alphaCores * 0.03f) + (coreData.betaCores * 0.02f) + (coreData.gammaCores * 0.01f));

        float currentRep = faction1.getRelationship(faction2.getId());
        String newRel = getRelationStr(currentRep);

        // Use hostile/negative color for reputation decrease
        Color deltaColor = Misc.getNegativeHighlightColor();

        String fn1 = faction1.getDisplayName();
        String fn2 = faction2.getDisplayName();

        // Format the reputation change as points (like Nexerelin does)
        int repPoints = Math.round(repChange * 100);

        // Format: "Relations between [Faction1] and [Faction2] reduced by [X], to [Y/100 (relation)]."
        String str = "Relations between " + fn1 + " and " + fn2 + " reduced by "
                + Math.abs(repPoints) + ", to " + String.format("%.0f/100", currentRep * 100)
                + " (" + newRel + ").";

        LabelAPI para = info.addPara(str, pad);
        para.setHighlight(fn1, fn2, String.valueOf(Math.abs(repPoints)),
                String.format("%.0f/100", currentRep * 100) + " (" + newRel + ")");
        para.setHighlightColors(faction1.getBaseUIColor(), faction2.getBaseUIColor(),
                deltaColor, faction1.getRelColor(faction2.getId()));
    }

    /**
     * Gets a short relationship string based on reputation value
     */
    private String getRelationStr(float rep) {
        RepLevel level = RepLevel.getLevelFor(rep);
        if (level == null) return "neutral";
        return level.getDisplayName().toLowerCase();
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "XLII_ai_found");
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);

        // There has to be an easier way...
        String diplomacyTag = "Diplomacy";
        try {
            Class<?> stringHelperClass = Class.forName("exerelin.utilities.StringHelper");
            java.lang.reflect.Method getStringMethod = stringHelperClass.getMethod("getString", String.class, boolean.class);
            diplomacyTag = (String) getStringMethod.invoke(null, "diplomacy", true);
        } catch (Exception e) {
            // Nexerelin not present or StringHelper unavailable, use fallback
        }

        tags.add(diplomacyTag);
        tags.add(DRACONIS);
        tags.add(targetMarket.getFactionId());
        return tags;
    }

    @Override
    public String getSortString() {
        return "Diplomacy";
    }

    @Override
    public String getName() {
        return "Diplomacy - " + "AI Core Intelligence";
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return Global.getSector().getFaction(DRACONIS);
    }

    @Override
    public boolean isEnding() {
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
    protected float getBaseDaysAfterEnd() {
        return 15;
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