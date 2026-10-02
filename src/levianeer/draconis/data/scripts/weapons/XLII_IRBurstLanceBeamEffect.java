package levianeer.draconis.data.scripts.weapons;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;

public class XLII_IRBurstLanceBeamEffect implements BeamEffectPlugin {

    public static float EFFECT_DUR = 3f;
    public static float DAMAGE_MULT = 2f;
    public static String DAMAGE_MOD_ID = "XLII_LaWS_dam_mod";

    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        CombatEntityAPI target = beam.getDamageTarget();
        if (beam.getBrightness() < 1f || beam.getWeapon() == null || !isValidTarget(target)) return;

        getTracker(engine).mark(target, engine.getTotalElapsedTime(false) + EFFECT_DUR);
    }

    protected boolean isValidTarget(CombatEntityAPI target) {
        if (target instanceof MissileAPI) return true;
        return target instanceof ShipAPI && ((ShipAPI) target).isFighter();
    }

    protected MarkedTargets getTracker(CombatEngineAPI engine) {
        if (!engine.getListenerManager().hasListenerOfClass(MarkedTargets.class)) {
            engine.getListenerManager().addListener(new MarkedTargets());
        }
        return engine.getListenerManager().getListeners(MarkedTargets.class).get(0);
    }

    // Engine-wide (not per-ship) DamageTakenModifier, since MissileAPI can't hold its own listeners.
    public static class MarkedTargets implements DamageTakenModifier {
        protected Map<CombatEntityAPI, Float> expiresAt = new HashMap<CombatEntityAPI, Float>();

        public void mark(CombatEntityAPI target, float expireAt) {
            expiresAt.put(target, expireAt);
        }

        public String modifyDamageTaken(Object param, CombatEntityAPI target,
                                         DamageAPI damage, Vector2f point, boolean shieldHit) {
            Float expireAt = expiresAt.get(target);
            if (expireAt == null) return null;

            if (Global.getCombatEngine().getTotalElapsedTime(false) > expireAt) {
                expiresAt.remove(target);
                return null;
            }

            damage.getModifier().modifyMult(DAMAGE_MOD_ID, DAMAGE_MULT);
            return DAMAGE_MOD_ID;
        }
    }

}
