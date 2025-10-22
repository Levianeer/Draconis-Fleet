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

import java.awt.*;
import java.util.List;
import java.util.Set;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Intel notification when Draconis successfully steals AI cores from a market
 */
public class DraconisAICoreTheftIntel extends BaseIntelPlugin {

    private final MarketAPI stolenFromMarket;
    private final MarketAPI installedAtMarket;
    private final List<String> stolenCores;
    private final String actionType; // "raid", "invasion", "ground_battle"
    private final long theftDate;
    private final boolean wasPlayerMarket;

    public DraconisAICoreTheftIntel(MarketAPI stolenFrom, MarketAPI installedAt,
                                    List<String> cores, String actionType, boolean isPlayerMarket) {
        this.stolenFromMarket = stolenFrom;
        this.installedAtMarket = installedAt;
        this.stolenCores = cores;
        this.actionType = actionType;
        this.wasPlayerMarket = isPlayerMarket;
        this.theftDate = Global.getSector().getClock().getTimestamp();
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate,
                                   Color tc, float initPad) {
        float pad = 0f;

        FactionAPI draconisFaction = Global.getSector().getFaction(DRACONIS);
        FactionAPI victimFaction = stolenFromMarket.getFaction();

        // Number of cores stolen
        Color h = Misc.getHighlightColor();
        info.addPara(stolenCores.size() + " AI core" + (stolenCores.size() > 1 ? "s" : "") + " stolen",
                initPad, h, String.valueOf(stolenCores.size()));

        // Victim faction name
        info.addPara(victimFaction.getDisplayName(), pad, tc,
                victimFaction.getBaseUIColor(),
                victimFaction.getDisplayName());

        // Target market
        info.addPara(stolenFromMarket.getName(), pad, h, stolenFromMarket.getName());
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        Color h = Misc.getHighlightColor();
        Color tc = Misc.getTextColor();
        Color bad = Misc.getNegativeHighlightColor();

        float opad = 10f;

        FactionAPI draconisFaction = Global.getSector().getFaction(DRACONIS);
        FactionAPI victimFaction = stolenFromMarket.getFaction();

        // Show both faction logos side by side
        info.addImages(width, 96, opad, opad,
                draconisFaction.getLogo(),
                victimFaction.getLogo());

        // Main description - different text based on if player was victim
        String str;
        LabelAPI para;

        if (wasPlayerMarket) {
            str = "Intelligence reports confirm that Draconis Alliance forces successfully infiltrated "
                    + stolenFromMarket.getName() + " during a " + getActionTypeDisplay()
                    + " and extracted AI cores from your colonial infrastructure.";

            para = info.addPara(str, opad);
            para.setHighlight(stolenFromMarket.getName(), getActionTypeDisplay(), "extracted AI cores");
            para.setHighlightColors(h, bad, bad);
        } else {
            str = "Intelligence sources report that Draconis Alliance forces conducted a "
                    + getActionTypeDisplay() + " against " + stolenFromMarket.getName()
                    + ", a " + victimFaction.getDisplayName()
                    + " colony in the " + stolenFromMarket.getStarSystem().getNameWithLowercaseType()
                    + ", and successfully extracted AI cores.";

            para = info.addPara(str, opad);
            para.setHighlight(getActionTypeDisplay(), stolenFromMarket.getName(),
                    victimFaction.getDisplayName(), "extracted AI cores");
            para.setHighlightColors(bad, h, victimFaction.getBaseUIColor(), bad);
        }

        // Stolen cores section
        info.addSectionHeading("Confirmed Stolen Assets",
                draconisFaction.getBaseUIColor(),
                draconisFaction.getDarkUIColor(),
                com.fs.starfarer.api.ui.Alignment.MID, opad);

        int alphaCores = 0;
        int betaCores = 0;
        int gammaCores = 0;

        for (String coreId : stolenCores) {
            if (coreId.contains("alpha")) alphaCores++;
            else if (coreId.contains("beta")) betaCores++;
            else if (coreId.contains("gamma")) gammaCores++;
        }

        if (alphaCores > 0) {
            info.addPara("• Alpha Cores: %s", 3f, bad, String.valueOf(alphaCores));
        }
        if (betaCores > 0) {
            info.addPara("• Beta Cores: %s", 3f, bad, String.valueOf(betaCores));
        }
        if (gammaCores > 0) {
            info.addPara("• Gamma Cores: %s", 3f, bad, String.valueOf(gammaCores));
        }

        // Installation location
        if (installedAtMarket != null) {
            info.addSectionHeading("Intelligence Assessment",
                    draconisFaction.getBaseUIColor(),
                    draconisFaction.getDarkUIColor(),
                    com.fs.starfarer.api.ui.Alignment.MID, opad);

            str = "Signals intelligence has detected the stolen AI cores in operation at "
                    + installedAtMarket.getName() + ", a Draconis facility in the "
                    + installedAtMarket.getStarSystem().getNameWithLowercaseType()
                    + ". The cores are reportedly enhancing industrial output and military capabilities.";

            para = info.addPara(str, opad);
            para.setHighlight(installedAtMarket.getName(),
                    installedAtMarket.getStarSystem().getNameWithLowercaseType());
            para.setHighlightColors(h, h);
        }

        // Diplomatic impact
        info.addSectionHeading("Diplomatic Impact",
                draconisFaction.getBaseUIColor(),
                draconisFaction.getDarkUIColor(),
                com.fs.starfarer.api.ui.Alignment.MID, opad);

        if (wasPlayerMarket) {
            str = "The theft of strategic assets from your territory represents a significant escalation "
                    + "in Draconis aggression. Your relationship with the Draconis Alliance has deteriorated sharply.";

            para = info.addPara(str, opad);
            para.setHighlight("significant escalation", "deteriorated sharply");
            para.setHighlightColors(bad, bad);
        } else {
            // Show relationship change between Draconis and victim
            addRelationshipChangePara(info, draconisFaction, victimFaction, opad);

            str = "The " + victimFaction.getDisplayName() + " are expected to view this action "
                    + "as a major provocation. Regional tensions are likely to increase.";

            para = info.addPara(str, opad);
            para.setHighlight(victimFaction.getDisplayName(), "major provocation");
            para.setHighlightColors(victimFaction.getBaseUIColor(), bad);
        }

        // Strategic implications
        info.addSectionHeading("Strategic Implications",
                draconisFaction.getBaseUIColor(),
                draconisFaction.getDarkUIColor(),
                com.fs.starfarer.api.ui.Alignment.MID, opad);

        str = "The successful acquisition of these AI cores strengthens Draconis technological capabilities "
                + "while simultaneously degrading " + victimFaction.getDisplayName() + " industrial output. "
                + "This operation demonstrates the effectiveness of Draconis intelligence gathering "
                + "and special operations capabilities.";

        para = info.addPara(str, opad);
        para.setHighlight("strengthens", "degrading", victimFaction.getDisplayName());
        para.setHighlightColors(h, bad, victimFaction.getBaseUIColor());

        // Days ago timestamp
        if (theftDate > 0) {
            info.addPara(Misc.getAgoStringForTimestamp(theftDate) + ".", opad);
        }
    }

    /**
     * Adds a relationship change paragraph
     * Handles non-standard factions (Pirates, Luddic Path, etc.) that may not have normal relationships
     */
    private void addRelationshipChangePara(TooltipMakerAPI info, FactionAPI faction1,
                                           FactionAPI faction2, float pad) {
        try {
            float currentRep = faction1.getRelationship(faction2.getId());
            String newRel = getRelationStr(currentRep);

            Color deltaColor = Misc.getNegativeHighlightColor();

            String fn1 = faction1.getDisplayName();
            String fn2 = faction2.getDisplayName();

            // Generic message about worsened relations
            String str = "Relations between " + fn1 + " and " + fn2
                    + " have worsened to " + String.format("%.0f/100", currentRep * 100)
                    + " (" + newRel + ") as a result of this incident.";

            LabelAPI para = info.addPara(str, pad);
            para.setHighlight(fn1, fn2, "worsened",
                    String.format("%.0f/100", currentRep * 100) + " (" + newRel + ")");

            // Safe color retrieval with fallback
            Color relColor;
            try {
                relColor = faction1.getRelColor(faction2.getId());
            } catch (Exception e) {
                relColor = Misc.getNegativeHighlightColor(); // Fallback for non-standard factions
            }

            para.setHighlightColors(faction1.getBaseUIColor(), faction2.getBaseUIColor(),
                    deltaColor, relColor);
        } catch (Exception e) {
            // Fallback for factions with broken diplomatic systems (Pirates, etc.)
            Global.getLogger(this.getClass()).warn(
                    "Could not display relationship change for " + faction1.getId() +
                    " and " + faction2.getId() + ": " + e.getMessage()
            );

            // Display a simpler message without specific relationship values
            String str = "Relations between " + faction1.getDisplayName() + " and " +
                    faction2.getDisplayName() + " have deteriorated as a result of this incident.";
            info.addPara(str, pad);
        }
    }

    /**
     * Gets a short relationship string based on reputation value
     */
    private String getRelationStr(float rep) {
        RepLevel level = RepLevel.getLevelFor(rep);
        if (level == null) return "neutral";
        return level.getDisplayName().toLowerCase();
    }

    /**
     * Gets display text for the action type
     */
    private String getActionTypeDisplay() {
        return switch (actionType) {
            case "invasion" -> "planetary invasion";
            case "ground_battle" -> "ground assault";
            default -> "strategic raid";
        };
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "XLII_ai_found");
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);

        // Try to add Nexerelin's diplomacy tag
        String diplomacyTag = "Diplomacy";
        try {
            Class<?> stringHelperClass = Class.forName("exerelin.utilities.StringHelper");
            java.lang.reflect.Method getStringMethod = stringHelperClass.getMethod("getString", String.class, boolean.class);
            diplomacyTag = (String) getStringMethod.invoke(null, "diplomacy", true);
        } catch (Exception e) {
            // Fallback
        }

        tags.add(diplomacyTag);
        tags.add(DRACONIS);
        tags.add(stolenFromMarket.getFactionId());

        if (wasPlayerMarket) {
            tags.add("important");
        }

        return tags;
    }

    @Override
    public String getSortString() {
        return "Diplomacy";
    }

    @Override
    public String getName() {
        if (wasPlayerMarket) {
            return "AI Core Theft - " + stolenFromMarket.getName();
        }
        return "Diplomacy - " + "AI Core Acquisition";
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return Global.getSector().getFaction(DRACONIS);
    }

    @Override
    protected float getBaseDaysAfterEnd() {
        return 30; // Keep intel around for a month
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        // Show location of the theft (victim market)
        return stolenFromMarket.getPrimaryEntity();
    }
}