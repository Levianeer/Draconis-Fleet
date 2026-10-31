package levianeer.draconis.data.scripts.skills.signals;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import second_in_command.SCData;

public class HardenedEmitters extends SignalsSkill {

    public static final float FRIGATE_SHIELD_DAMAGE_TAKEN_PER_ADVANTAGE = 2.5f;
    public static final float DESTROYER_SHIELD_DAMAGE_TAKEN_PER_ADVANTAGE = 2f;
    public static final float CRUISER_SHIELD_DAMAGE_TAKEN_PER_ADVANTAGE = 1.5f;
    public static final float CAPITAL_SHIELD_DAMAGE_TAKEN_PER_ADVANTAGE = 1f;

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        addScalingHeader(tooltip);
        addEffect(tooltip, "%s / %s / %s / %s shield damage taken for frigates / destroyers / cruisers / capital ships",
                atCap("-", FRIGATE_SHIELD_DAMAGE_TAKEN_PER_ADVANTAGE),
                atCap("-", DESTROYER_SHIELD_DAMAGE_TAKEN_PER_ADVANTAGE),
                atCap("-", CRUISER_SHIELD_DAMAGE_TAKEN_PER_ADVANTAGE),
                atCap("-", CAPITAL_SHIELD_DAMAGE_TAKEN_PER_ADVANTAGE));
        addAdvantageNote(tooltip);
    }

    @Override
    public void applyAdvantage(MutableShipStatsAPI stats, float advantage, float maximum) {
        float perAdvantage = CAPITAL_SHIELD_DAMAGE_TAKEN_PER_ADVANTAGE;
        if (stats.getEntity() instanceof ShipAPI) {
            switch (((ShipAPI) stats.getEntity()).getHullSize()) {
                case FRIGATE: perAdvantage = FRIGATE_SHIELD_DAMAGE_TAKEN_PER_ADVANTAGE; break;
                case DESTROYER: perAdvantage = DESTROYER_SHIELD_DAMAGE_TAKEN_PER_ADVANTAGE; break;
                case CRUISER: perAdvantage = CRUISER_SHIELD_DAMAGE_TAKEN_PER_ADVANTAGE; break;
                default: break;
            }
        }
        stats.getShieldDamageTakenMult().modifyMult(getId(), 1f - advantage * perAdvantage / 100f);
    }
}
