package levianeer.draconis;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.CampaignPlugin;
import com.fs.starfarer.api.combat.MissileAIPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import levianeer.draconis.data.campaign.characters.XLII_Characters;
import levianeer.draconis.data.campaign.intel.aicore.listener.DraconisTargetedRaidMonitor;
import levianeer.draconis.data.campaign.intel.aicore.scanner.DraconisSingleTargetScanner;
import levianeer.draconis.data.campaign.intel.events.crisis.DraconisHostileActivityManager;
import levianeer.draconis.data.campaign.econ.conditions.DraconisSteelCurtainMonitor;
import levianeer.draconis.data.scripts.ai.XLII_antiMissileAI;
import levianeer.draconis.data.scripts.ai.XLII_magicMissileAI;
import levianeer.draconis.data.scripts.world.XLII_WorldGen;

public class XLII_ModPlugin extends BaseModPlugin {

    public static final String PD_MISSILE_ID = "XLII_swordbreaker_shot";
    public static final String SWARM_MISSILE_ID = "XLII_bardiche_shot";

    private static final String NEXERELIN_MOD_ID = "nexerelin";
    private static boolean hasNexerelin = false;

    @Override
    public void onApplicationLoad() {
        Global.getLogger(this.getClass()).info("=== Draconis Mod Loading ===");

        hasNexerelin = Global.getSettings().getModManager().isModEnabled(NEXERELIN_MOD_ID);

        if (hasNexerelin) {
            Global.getLogger(this.getClass()).info("Nexerelin detected - AI core acquisition system will be enabled");
        } else {
            Global.getLogger(this.getClass()).info("Nexerelin not detected - AI core system will be disabled");
        }
    }

    @Override
    public void onNewGame() {
        Global.getLogger(this.getClass()).info("=== Draconis Mod: onNewGame() ===");

        boolean skipGeneration = false;

        if (hasNexerelin) {
            boolean isRandomSector = Global.getSector().getMemoryWithoutUpdate()
                    .getBoolean("$nex_randomSector");

            if (isRandomSector) {
                skipGeneration = true;
                Global.getLogger(this.getClass()).info(
                        "Nexerelin random sector detected - skipping custom sector generation"
                );
            }
        }

        if (!skipGeneration) {
            Global.getLogger(this.getClass()).info("Generating custom Draconis sector");
            new XLII_WorldGen().generate(Global.getSector());
            Global.getLogger(this.getClass()).info("Sector generation complete");
        }
    }

    @Override
    public void onGameLoad(boolean newGame) {
        super.onGameLoad(newGame);

        Global.getLogger(this.getClass()).info("=== Draconis Mod: onGameLoad() ===");
        Global.getLogger(this.getClass()).info("New game: " + newGame);

        // Initialize Draconis characters
        Global.getLogger(this.getClass()).info("Initializing Draconis characters");
        XLII_Characters.initializeAllCharacters();

        // Add the crisis event system
        Global.getLogger(this.getClass()).info("Registering crisis systems");
        Global.getSector().addScript(new DraconisHostileActivityManager());
        Global.getLogger(this.getClass()).info("  - Hostile Activity Manager");

        Global.getSector().addScript(new DraconisSteelCurtainMonitor());
        Global.getLogger(this.getClass()).info("  - Steel Curtain Monitor");

        // If Nexerelin is present, add the AI core acquisition system
        if (hasNexerelin) {
            Global.getLogger(this.getClass()).info("=== Registering AI Core Acquisition System ===");

            // Scanner - finds high-value AI core targets
            Global.getSector().addScript(new DraconisSingleTargetScanner());
            Global.getLogger(this.getClass()).info("  - AI Core Target Scanner");

            // Monitor - watches for successful raids against AI core targets
            Global.getSector().addScript(new DraconisTargetedRaidMonitor());
            Global.getLogger(this.getClass()).info("  - Targeted Raid Monitor");

            // Note: Concern is registered via strategicAIconfig.json
            Global.getLogger(this.getClass()).info("  - Strategic AI concern defined in JSON");

            Global.getLogger(this.getClass()).info("=== AI Core Acquisition System Active ===");
        } else {
            Global.getLogger(this.getClass()).info("Nexerelin not present - AI core acquisition system disabled");
        }

        Global.getLogger(this.getClass()).info("=== Draconis Mod: Game load complete ===");
    }

    @Override
    public PluginPick<MissileAIPlugin> pickMissileAI(MissileAPI missile, ShipAPI launchingShip) {
        if (missile.getProjectileSpecId().equals(PD_MISSILE_ID)) {
            return new PluginPick<>(new XLII_antiMissileAI(missile, launchingShip), CampaignPlugin.PickPriority.MOD_SPECIFIC);
        }
        if (missile.getProjectileSpecId().equals(SWARM_MISSILE_ID)) {
            return new PluginPick<>(new XLII_magicMissileAI(missile, launchingShip), CampaignPlugin.PickPriority.MOD_SPECIFIC);
        }
        return null;
    }
}