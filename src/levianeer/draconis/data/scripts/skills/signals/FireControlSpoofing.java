package levianeer.draconis.data.scripts.skills.signals;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import second_in_command.SCData;

public class FireControlSpoofing extends SignalsSkill {

    public static final float WEAPON_TURN_RATE_PER_ADVANTAGE = 2f;
    public static final float PROJECTILE_SPEED_PER_ADVANTAGE = 1f;

    @Override
    public String getAffectsString() {
        return "all enemy ships";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        addScalingHeader(tooltip);
        addEffect(tooltip, "%s enemy weapon turn rate", atCap("-", WEAPON_TURN_RATE_PER_ADVANTAGE));
        addEffect(tooltip, "%s enemy projectile speed", atCap("-", PROJECTILE_SPEED_PER_ADVANTAGE));
        addAdvantageNote(tooltip);
    }

    @Override
    public boolean affectsEnemy() {
        return true;
    }

    @Override
    public void applyAdvantage(MutableShipStatsAPI stats, float advantage, float maximum) {
        stats.getWeaponTurnRateBonus().modifyMult(getId(), 1f - advantage * WEAPON_TURN_RATE_PER_ADVANTAGE / 100f);
        stats.getProjectileSpeedMult().modifyMult(getId(), 1f - advantage * PROJECTILE_SPEED_PER_ADVANTAGE / 100f);
    }
}
