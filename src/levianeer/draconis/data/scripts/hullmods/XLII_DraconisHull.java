package levianeer.draconis.data.scripts.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.hullmods.Automated;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.XLII_ModPlugin;
import levianeer.draconis.data.campaign.aicores.XLII_LiteCoreOfficerPlugin;

import java.awt.Color;

public class XLII_DraconisHull extends XLII_SystemHullModBase {

    public static final float DEGRADE_INCREASE_PERCENT = 50f;

    // Marks that we added TAG_AUTOMATED_NO_PENALTY, so we never strip one set by something else.
    public static final String AUXCOG_NO_PENALTY_TAG = "XLII_auxcog_no_penalty";

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getCRLossPerSecondPercent().modifyPercent(id, DEGRADE_INCREASE_PERCENT);
        applyAuxCogPenaltyExemption(stats);
    }

    /**
     * Uses vanilla's no_auto_penalty tag rather than a flat CR offset: the Automated Ships skill
     * also skips no-penalty ships, so a flat offset would stack with the skill's bonus.
     */
    private void applyAuxCogPenaltyExemption(MutableShipStatsAPI stats) {
        FleetMemberAPI member = stats.getFleetMember();
        if (member == null) return;
        ShipVariantAPI variant = member.getVariant();
        if (variant == null) return;

        if (Misc.isAutomated(variant) && hasAuxCogCaptain(member)) {
            variant.addTag(Tags.TAG_AUTOMATED_NO_PENALTY);
            variant.addTag(AUXCOG_NO_PENALTY_TAG);
            // Hullmod apply order isn't guaranteed; if Automated already ran this pass, undo its penalty.
            stats.getMaxCombatReadiness().unmodifyFlat(HullMods.AUTOMATED);
        } else if (variant.hasTag(AUXCOG_NO_PENALTY_TAG)) {
            variant.removeTag(Tags.TAG_AUTOMATED_NO_PENALTY);
            variant.removeTag(AUXCOG_NO_PENALTY_TAG);
        }
    }

    private static boolean hasAuxCogCaptain(FleetMemberAPI member) {
        PersonAPI captain = member.getCaptain();
        return captain != null && captain.isAICore()
                && XLII_LiteCoreOfficerPlugin.CORE_ID.equals(captain.getAICoreId());
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        return switch (index) {
            case 0 -> (int) getChargeTime(HullSize.FRIGATE) + "s";
            case 1 -> (int) getChargeTime(HullSize.DESTROYER) + "s";
            case 2 -> (int) getChargeTime(HullSize.CRUISER) + "s";
            case 3 -> (int) getChargeTime(HullSize.CAPITAL_SHIP) + "s";
            case 4 -> (int) CR_LOSS_ON_WARP + Strings.X;
            default -> null;
        };
    }

    @Override
    public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return false;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();

        tooltip.addPara("Draconis-built hulls have a number of shared properties.", opad);

        addTransverseJumpTooltipSection(tooltip, opad, h);

        // Only Draconis hulls converted via the LunaLib refit button can be automated, so without it
        // this section describes something the player can't reach.
        if (XLII_ModPlugin.hasLunaLib()) {
            String coreName = Global.getSettings().getCommoditySpec(XLII_LiteCoreOfficerPlugin.CORE_ID).getName();
            tooltip.addSectionHeading("AuxCog Integration", Alignment.MID, opad);
            tooltip.addPara("Draconis-built hulls are designed to interface with the %s. If this ship is automated and "
                    + "an %s is in command, the %s automated ship penalty to maximum combat readiness does not apply.",
                    opad, h,
                    coreName, coreName, (int) Math.round(Automated.MAX_CR_PENALTY * 100f) + "%");
        }

        tooltip.addSectionHeading("Maintenance", new Color(255,100,0) , new Color(105,40,0,175) , Alignment.MID, opad);
        tooltip.addPara("Draconis-built hulls require excessive maintenance and do not stand up well under the rigours of prolonged engagements.", opad);

        tooltip.addPara("Increases the rate of in-combat CR decay after peak performance time runs out by %s.",
                opad, h,
                (int) DEGRADE_INCREASE_PERCENT + "%");
    }

    @Override
    public int getDisplaySortOrder() {
        return 0;
    }

    @Override
    public int getDisplayCategoryIndex() {
        return 0;
    }
}
