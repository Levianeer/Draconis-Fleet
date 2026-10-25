package levianeer.draconis.data.scripts.refit;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.ids.Factions;
import lunalib.lunaRefit.BaseRefitButton;
import lunalib.lunaRefit.LunaRefitManager;

/**
 * Refit button that toggles a Draconis hull between crewed and automated, at any Draconis market.
 * Based on LunaLib's RefitEffectButtonExample.
 * <p>
 * LunaLib is optional: only reach this class through {@link #register()}, and only once LunaLib is
 * confirmed enabled. Anything that names a LunaLib type outside this class would fail to load
 * without it.
 */
public class XLII_AutomatedConversionButton extends BaseRefitButton {

    // FortySecond skins strip this built-in mod, which is what keeps them out.
    private static final String DRACONIS_HULLMOD_ID = "XLII_draconishull";

    public static void register() {
        LunaRefitManager.INSTANCE.addRefitButton(new XLII_AutomatedConversionButton());
    }

    @Override
    public String getButtonName(FleetMemberAPI member, ShipVariantAPI variant) {
        return isAutomated(variant) ? "Crew Conversion" : "Automated Conversion";
    }

    @Override
    public String getIconName(FleetMemberAPI member, ShipVariantAPI variant) {
        return isAutomated(variant) ? "graphics/icons/skills/crew_training.png"
                                    : "graphics/icons/skills/automated_ships.png";
    }

    @Override
    public int getOrder(FleetMemberAPI member, ShipVariantAPI variant) {
        return 9999;
    }

    @Override
    public boolean shouldShow(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
        return variant.getHullSpec().isBuiltInMod(DRACONIS_HULLMOD_ID);
    }

    @Override
    public boolean isClickable(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
        return isDraconisMarket(market) && !hasCaptain(member);
    }

    @Override
    public boolean hasTooltip(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
        return true;
    }

    @Override
    public void addTooltip(TooltipMakerAPI tooltip, FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
        if (isAutomated(variant)) {
            tooltip.addPara("Refits this hull for a human crew.", 0f);
        } else {
            tooltip.addPara("Refits this hull to run without a crew, as an automated ship.", 0f);
        }

        if (!isDraconisMarket(market)) {
            tooltip.addPara("Requires docking at a Draconis Alliance market.", 10f,
                    Misc.getNegativeHighlightColor(), Misc.getNegativeHighlightColor());
        }
        if (hasCaptain(member)) {
            tooltip.addPara("Cannot be converted while an officer is assigned to the ship.", 10f,
                    Misc.getNegativeHighlightColor(), Misc.getNegativeHighlightColor());
        }
    }

    @Override
    public void onClick(FleetMemberAPI member, ShipVariantAPI variant, InputEventAPI event, MarketAPI market) {
        // LunaLib: always modify the provided variant, never member.getVariant().
        if (isAutomated(variant)) {
            variant.removePermaMod(HullMods.AUTOMATED);
        } else {
            variant.addPermaMod(HullMods.AUTOMATED);
        }
        refreshVariant();
        refreshButtonList();
    }

    private static boolean isAutomated(ShipVariantAPI variant) {
        return variant.hasHullMod(HullMods.AUTOMATED);
    }

    private static boolean isDraconisMarket(MarketAPI market) {
        return market != null && Factions.DRACONIS.equals(market.getFactionId());
    }

    private static boolean hasCaptain(FleetMemberAPI member) {
        return member.getCaptain() != null && !member.getCaptain().isDefault();
    }
}
