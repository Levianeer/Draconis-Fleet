package levianeer.draconis.data.scripts.skills.signals;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import second_in_command.SCData;

public class FullSpectrum extends SignalsSkill {

    public static final float DAMAGE_PER_ADVANTAGE = 1.5f;

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        addScalingHeader(tooltip);
        addEffect(tooltip, "%s damage dealt by all weapons", atCap("+", DAMAGE_PER_ADVANTAGE));
        addAdvantageNote(tooltip);
    }

    @Override
    public void applyAdvantage(MutableShipStatsAPI stats, float advantage, float maximum) {
        float bonus = advantage * DAMAGE_PER_ADVANTAGE;
        stats.getBallisticWeaponDamageMult().modifyPercent(getId(), bonus);
        stats.getEnergyWeaponDamageMult().modifyPercent(getId(), bonus);
        stats.getMissileWeaponDamageMult().modifyPercent(getId(), bonus);
    }
}
