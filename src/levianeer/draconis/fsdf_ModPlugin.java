package levianeer.draconis;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.CampaignPlugin;
import com.fs.starfarer.api.combat.MissileAIPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;

import levianeer.draconis.data.scripts.ai.fsdf_antiMissileAI;
import levianeer.draconis.data.scripts.world.fsdf_WorldGen;

public class fsdf_ModPlugin extends BaseModPlugin {

    public static final String PD_MISSILE_ID = "fsdf_swordbreaker_shot";

    @Override
    public void onNewGame() {
        new fsdf_WorldGen().generate(Global.getSector());
    }

    @Override
    public PluginPick<MissileAIPlugin> pickMissileAI(MissileAPI missile, ShipAPI launchingShip) {
        switch (missile.getProjectileSpecId()) {
            case PD_MISSILE_ID:
                return new PluginPick<MissileAIPlugin>(new fsdf_antiMissileAI(missile, launchingShip), CampaignPlugin.PickPriority.MOD_SPECIFIC);
            default:
        }
        return null;
    }
}