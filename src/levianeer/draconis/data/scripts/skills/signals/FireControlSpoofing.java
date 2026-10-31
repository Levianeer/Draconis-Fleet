package levianeer.draconis.data.scripts.skills.signals;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import second_in_command.SCData;

public class FireControlSpoofing extends SignalsSkill {

    public static final float WEAPON_TURN_RATE_PER_ADVANTAGE = 3f;
    public static final float PROJECTILE_SPEED_PER_ADVANTAGE = 2.5f;
    // Stands in for an autofire accuracy debuff: autofireAimAccuracy has base 0 and is clamped to
    // [0, 1] in Ship.getAimAccuracy(), so lowering it only strips skill bonuses, never hurts a plain ship.
    public static final float RECOIL_PER_ADVANTAGE = 2.5f;

    @Override
    public String getAffectsString() {
        return "all enemy ships";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        addScalingHeader(tooltip);
        addEffect(tooltip, "%s enemy weapon turn rate", atCap("-", WEAPON_TURN_RATE_PER_ADVANTAGE));
        addEffect(tooltip, "%s enemy projectile speed", atCap("-", PROJECTILE_SPEED_PER_ADVANTAGE));
        addEffect(tooltip, "%s enemy weapon recoil", atCap("+", RECOIL_PER_ADVANTAGE));
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
        stats.getRecoilPerShotMult().modifyPercent(getId(), advantage * RECOIL_PER_ADVANTAGE);
        stats.getMaxRecoilMult().modifyPercent(getId(), advantage * RECOIL_PER_ADVANTAGE);
    }
}
