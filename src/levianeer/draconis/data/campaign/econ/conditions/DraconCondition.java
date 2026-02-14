package levianeer.draconis.data.campaign.econ.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

import static levianeer.draconis.data.campaign.ids.Factions.DRACONIS;

/**
 * Market condition representing the current DRACON (Draconis Readiness Condition) level.
 * Applied to all DDA markets by DraconManager. Reads the current level from sector memory
 * and applies corresponding stat modifiers.
 * <p>
 * DRACON 5 (COLD FORGE): Peacetime bonuses
 * <p>
 * DRACON 4 (LONG WATCH): First military escalation
 * <p>
 * DRACON 3 (DRAWN SWORD): War economy, boss faction territory
 * <p>
 * DRACON 2 (BARE STEEL): Full mobilization, severe economic cost
 * <p>
 * DRACON 1 (DEAD LIGHT): Total war, self-destructive power
 */
public class DraconCondition extends BaseMarketConditionPlugin {

    @Override
    public void apply(String id) {
        int level = getCurrentLevel();

        // Clear all possible modifiers first, then apply only current level's stats.
        // This handles level transitions cleanly when stats appear/disappear between levels.
        unapply(id);

        switch (level) {
            case 5: // COLD FORGE
                market.getStability().modifyFlat(id, 1f, "DRACON 5 - COLD FORGE");
                market.getAccessibilityMod().modifyFlat(id, 0.05f, "DRACON 5 - COLD FORGE");
                break;

            case 4: // LONG WATCH
                market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                        .modifyMult(id, 1.25f, "DRACON 4 - LONG WATCH");
                market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SPAWN_RATE_MULT)
                        .modifyMult(id, 1.25f, "DRACON 4 - LONG WATCH");
                market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD)
                        .modifyMult(id, 1.25f, "DRACON 4 - LONG WATCH");
                market.getAccessibilityMod().modifyFlat(id, -0.05f, "DRACON 4 - LONG WATCH");
                break;

            case 3: // DRAWN SWORD
                market.getStability().modifyFlat(id, -1f, "DRACON 3 - DRAWN SWORD");
                market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                        .modifyMult(id, 1.5f, "DRACON 3 - DRAWN SWORD");
                market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SPAWN_RATE_MULT)
                        .modifyMult(id, 1.5f, "DRACON 3 - DRAWN SWORD");
                market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD)
                        .modifyMult(id, 1.5f, "DRACON 3 - DRAWN SWORD");
                market.getAccessibilityMod().modifyFlat(id, -0.10f, "DRACON 3 - DRAWN SWORD");
                market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD)
                        .modifyFlat(id, 0.10f, "DRACON 3 - DRAWN SWORD");
                break;

            case 2: // BARE STEEL
                market.getStability().modifyFlat(id, -2f, "DRACON 2 - BARE STEEL");
                market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                        .modifyMult(id, 2.0f, "DRACON 2 - BARE STEEL");
                market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SPAWN_RATE_MULT)
                        .modifyMult(id, 2.0f, "DRACON 2 - BARE STEEL");
                market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD)
                        .modifyMult(id, 2.0f, "DRACON 2 - BARE STEEL");
                market.getAccessibilityMod().modifyFlat(id, -0.20f, "DRACON 2 - BARE STEEL");
                market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD)
                        .modifyFlat(id, 0.20f, "DRACON 2 - BARE STEEL");
                market.getHazard().modifyFlat(id, 0.25f, "DRACON 2 - BARE STEEL");
                break;

            case 1: // DEAD LIGHT
                market.getStability().modifyFlat(id, -3f, "DRACON 1 - DEAD LIGHT");
                market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                        .modifyMult(id, 2.5f, "DRACON 1 - DEAD LIGHT");
                market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SPAWN_RATE_MULT)
                        .modifyMult(id, 2.5f, "DRACON 1 - DEAD LIGHT");
                market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD)
                        .modifyMult(id, 2.5f, "DRACON 1 - DEAD LIGHT");
                market.getAccessibilityMod().modifyFlat(id, -0.30f, "DRACON 1 - DEAD LIGHT");
                market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD)
                        .modifyFlat(id, 0.30f, "DRACON 1 - DEAD LIGHT");
                market.getHazard().modifyFlat(id, 0.50f, "DRACON 1 - DEAD LIGHT");
                break;
        }
    }

    @Override
    public void unapply(String id) {
        market.getStability().unmodifyFlat(id);
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(id);
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SPAWN_RATE_MULT).unmodifyMult(id);
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyMult(id);
        market.getAccessibilityMod().unmodifyFlat(id);
        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).unmodifyFlat(id);
        market.getHazard().unmodifyFlat(id);
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        int level = getCurrentLevel();
        String levelName = DraconConfig.getLevelName(level);

        float opad = 10f;
        Color h = Misc.getHighlightColor();
        Color pos = Misc.getPositiveHighlightColor();
        Color neg = Misc.getNegativeHighlightColor();
        Color draconis = Global.getSector().getFaction(DRACONIS).getBaseUIColor();

        // Level header
        tooltip.addPara("Current readiness: DRACON %s - %s", opad,
                new Color[]{getLevelColor(level), draconis},
                String.valueOf(level), levelName);

        // Stat effects
        switch (level) {
            case 5:
                addStatLine(tooltip, opad, "Stability", "+1", pos);
                addStatLine(tooltip, 3f, "Accessibility", "+5%", pos);
                break;

            case 4:
                addStatLine(tooltip, opad, "Fleet size", "+25%", pos);
                addStatLine(tooltip, 3f, "Fleet spawn rate", "+25%", pos);
                addStatLine(tooltip, 3f, "Ground defenses", "+25%", pos);
                addStatLine(tooltip, 3f, "Accessibility", "-5%", neg);
                break;

            case 3:
                addStatLine(tooltip, opad, "Stability", "-1", neg);
                addStatLine(tooltip, 3f, "Fleet size", "+50%", pos);
                addStatLine(tooltip, 3f, "Fleet spawn rate", "+50%", pos);
                addStatLine(tooltip, 3f, "Ground defenses", "+50%", pos);
                addStatLine(tooltip, 3f, "Accessibility", "-10%", neg);
                addStatLine(tooltip, 3f, "Ship quality", "+10%", pos);
                break;

            case 2:
                addStatLine(tooltip, opad, "Stability", "-2", neg);
                addStatLine(tooltip, 3f, "Fleet size", "+100%", pos);
                addStatLine(tooltip, 3f, "Fleet spawn rate", "+100%", pos);
                addStatLine(tooltip, 3f, "Ground defenses", "+100%", pos);
                addStatLine(tooltip, 3f, "Accessibility", "-20%", neg);
                addStatLine(tooltip, 3f, "Ship quality", "+20%", pos);
                addStatLine(tooltip, 3f, "Hazard rating", "+25%", neg);
                addStatLine(tooltip, 3f, "Fleet AI core coverage", "increased", pos);
                addStatLine(tooltip, 3f, "Fleet AI core quality", "enhanced", pos);
                break;

            case 1:
                addStatLine(tooltip, opad, "Stability", "-3", neg);
                addStatLine(tooltip, 3f, "Fleet size", "+150%", pos);
                addStatLine(tooltip, 3f, "Fleet spawn rate", "+150%", pos);
                addStatLine(tooltip, 3f, "Ground defenses", "+150%", pos);
                addStatLine(tooltip, 3f, "Accessibility", "-30%", neg);
                addStatLine(tooltip, 3f, "Ship quality", "+30%", pos);
                addStatLine(tooltip, 3f, "Hazard rating", "+50%", neg);
                addStatLine(tooltip, 3f, "Fleet AI officers", "100% Saturation", pos);
                break;
        }

        // Flavor text
        String flavor = getFlavorText(level);
        tooltip.addPara(flavor, opad, Misc.getGrayColor(), Misc.getGrayColor());
    }

    private void addStatLine(TooltipMakerAPI tooltip, float pad, String statName, String value, Color valueColor) {
        tooltip.addPara(statName + ": %s", pad, valueColor, value);
    }

    private int getCurrentLevel() {
        DraconConfig config = DraconConfig.getInstance();
        int testOverride = config.getTestLevelOverride();
        if (testOverride >= 1 && testOverride <= 5) {
            return testOverride;
        }

        Object stored = Global.getSector().getMemoryWithoutUpdate().get(DraconManager.LEVEL_KEY);
        if (stored instanceof Number) {
            return ((Number) stored).intValue();
        }
        return 5; // Default to peacetime
    }

    private Color getLevelColor(int level) {
        return switch (level) {
            case 4 -> new Color(0, 141, 255);
            case 3 -> new Color(0, 210, 0);
            case 2 -> new Color(255, 255, 0);
            case 1 -> new Color(255, 0, 0);
            default -> Color.WHITE;
        };
    }

    private String getFlavorText(int level) {
        return switch (level) {
            case 5 -> "The Alliance maintains standard defensive posture. Patrol rotations follow peacetime " +
                    "schedules along Rift approach corridors. Kori's nanoforge operates at sustainment output " +
                    "- replacement hulls, spare parts, routine refits. Shore leave is authorized; reserve " +
                    "personnel remain on seventy-two-hour recall. The Office monitors external communications " +
                    "through normal channels.\n\n" +
                    "Most of the Alliance's existence is spent at DRACON 5. This is not peace. It is the " +
                    "absence of anything worse.";
            case 4 -> "Intelligence assets across all monitored hyperlanes shift to active collection. Shore " +
                    "leave is suspended for front-line units; reserve recall compresses to twenty-four hours. " +
                    "Patrol frequency doubles along Rift transit corridors. Civilian shipping receives updated " +
                    "transponder protocols.\n\n" +
                    "The Office assumes direct oversight of external communications and begins compartmentalising " +
                    "operational data. Athebyne's garrison conducts unscheduled readiness drills. Kori's " +
                    "shipyards begin pre-positioning war reserves.\n\n" +
                    "DRACON 4 is raised more often than the public knows. Most elevations resolve without " +
                    "incident. Some do not.";
            case 3 -> "All reserve formations recalled to active service. Civilian traffic through the Rift is " +
                    "suspended indefinitely. Kori's nanoforge shifts to wartime production - loss-replacement " +
                    "cycles shorten, quality tolerances widen. Mining operations on Athebyne and Vorium's moons " +
                    "redirect output to military allocation.\n\n" +
                    "Nuclear release authority is delegated to fleet group commanders. Planetary defence batteries " +
                    "arm and orient. Ring-Port receives its customary warning: stay clear or be cleared.\n\n" +
                    "The Alliance has entered DRACON 3 four times since its founding. Three of those are a " +
                    "matter of public record.";
            case 2 -> "All Alliance forces deploy to pre-assigned battle positions. Scorched-earth protocols are " +
                    "armed across both worlds. Athebyne's orbital defence grid activates in full - what remains " +
                    "of it. Itoron's population centres begin civil defence procedures that have been rehearsed " +
                    "since the war.\n\n" +
                    "The Office activates contingency networks and burns non-essential intelligence assets. " +
                    "Tri-Tachyon liaison channels go silent, though certain encrypted frequencies do not. " +
                    "Deploy-to-engage timeline: six hours or less.\n\n" +
                    "Deep-Kori facilities receive updated targeting parameters. Requests for this data are " +
                    "processed without human review.";
            case 1 -> "All assets weapons-free. Fleet commanders are authorised to execute scorched-earth " +
                    "protocols at their discretion. There is no centralised coordination requirement - if Kori " +
                    "falls silent, standing orders suffice.\n\n" +
                    "Every ship fights. Every station fights. Itoron's agri-domes vent atmosphere before they " +
                    "are taken. Athebyne has already burned once; its garrison knows what is expected of them.\n\n" +
                    "Deep-Kori assets are unrestricted.";
            default -> "";
        };
    }
}