package levianeer.draconis;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.CampaignPlugin;
import com.fs.starfarer.api.combat.MissileAIPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import levianeer.draconis.data.campaign.characters.XLII_Characters;
import org.apache.log4j.Logger;
import levianeer.draconis.data.campaign.intel.aicore.donation.DraconisAICoreDonationListener;
import levianeer.draconis.data.campaign.intel.aicore.listener.DraconisTargetedRaidMonitor;
import levianeer.draconis.data.campaign.intel.aicore.remnant.DraconisRemnantRaidListener;
import levianeer.draconis.data.campaign.intel.aicore.remnant.DraconisRemnantRaidManager;
import levianeer.draconis.data.campaign.intel.aicore.remnant.DraconisRemnantTargetScanner;
import levianeer.draconis.data.campaign.intel.aicore.scanner.DraconisSingleTargetScanner;
import levianeer.draconis.data.campaign.intel.aicore.raids.DraconisAICoreRaidManager;
import levianeer.draconis.data.campaign.intel.events.crisis.DraconisHostileActivityManager;
import levianeer.draconis.data.campaign.econ.conditions.DraconisSteelCurtainMonitor;
import levianeer.draconis.data.campaign.fleet.DraconisAICoreFleetInflater;
import levianeer.draconis.data.campaign.fleet.DraconisAICoreScalingConfig;
import levianeer.draconis.data.scripts.ai.XLII_antiMissileAI;
import levianeer.draconis.data.scripts.ai.XLII_magicMissileAI;
import levianeer.draconis.data.scripts.world.XLII_WorldGen;

@SuppressWarnings("unused")
public class XLII_ModPlugin extends BaseModPlugin {

    private static final Logger log = Global.getLogger(XLII_ModPlugin.class);
    public static final String PD_MISSILE_ID = "XLII_swordbreaker_shot";
    public static final String SWARM_MISSILE_ID = "XLII_bardiche_shot";
    private static final String NEXERELIN_MOD_ID = "nexerelin";
    private static boolean hasNexerelin = false;

    // Raid system toggles
    private static boolean enableAICoreRaids = true;
    private static boolean enableRemnantRaids = true;

    // AI Core Fleet Scaling toggle
    private static boolean enableAICoreFleetScaling = true;

    @Override
    public void onApplicationLoad() {
        log.info("Draconis: === Mod Loading ===");
        hasNexerelin = Global.getSettings().getModManager().isModEnabled(NEXERELIN_MOD_ID);

        // Load raid system toggles from settings
        try {
            enableAICoreRaids = Global.getSettings().getBoolean("draconisEnableAICoreRaids");
            log.info("Draconis: AI Core Raids: " + (enableAICoreRaids ? "ENABLED" : "DISABLED"));
        } catch (Exception e) {
            log.warn("Draconis: Failed to load draconisEnableAICoreRaids setting, defaulting to true", e);
            enableAICoreRaids = true;
        }

        try {
            enableRemnantRaids = Global.getSettings().getBoolean("draconisEnableRemnantRaids");
            log.info("Draconis: Remnant Raids: " + (enableRemnantRaids ? "ENABLED" : "DISABLED"));
        } catch (Exception e) {
            log.warn("Draconis: Failed to load draconisEnableRemnantRaids setting, defaulting to true", e);
            enableRemnantRaids = true;
        }

        // Load AI Core Fleet Scaling config (initializes singleton)
        DraconisAICoreScalingConfig config = DraconisAICoreScalingConfig.getInstance();
        enableAICoreFleetScaling = config.isEnabled();
        log.info("Draconis: AI Core Fleet Scaling: " + (enableAICoreFleetScaling ? "ENABLED" : "DISABLED"));

        if (hasNexerelin) {
            log.info("Draconis: Nexerelin detected - AI core acquisition system will be enabled");
        } else {
            log.info("Draconis: Nexerelin not detected - AI core system will be disabled");
        }
    }

    @Override
    public void onNewGame() {
        log.info("Draconis: === onNewGame() ===");

        boolean skipGeneration = false;

        if (hasNexerelin) {
            boolean isRandomSector = Global.getSector().getMemoryWithoutUpdate()
                    .getBoolean("$nex_randomSector");

            if (isRandomSector) {
                skipGeneration = true;
                log.info("Draconis: Nexerelin random sector detected - skipping custom sector generation");
            }
        }

        if (!skipGeneration) {
            log.info("Draconis: Generating custom sector");
            new XLII_WorldGen().generate(Global.getSector());
            log.info("Draconis: Sector generation complete");
        }
    }

    @Override
    public void onGameLoad(boolean newGame) {
        super.onGameLoad(newGame);
        log.info("Draconis: === onGameLoad() ===");
        log.info("Draconis: New game: " + newGame);

        // Initialize Draconis characters
        log.info("Draconis: Initializing characters");
        XLII_Characters.initializeAllCharacters();

        // Add the crisis event system
        log.info("Draconis: Registering crisis systems");
        Global.getSector().addScript(new DraconisHostileActivityManager());
        log.info("Draconis:   - Hostile Activity Manager");
        Global.getSector().addScript(new DraconisSteelCurtainMonitor());
        log.info("Draconis:   - Steel Curtain Monitor");

        // Add AI Core Fleet Scaling system
        if (enableAICoreFleetScaling) {
            log.info("Draconis: Registering AI Core Fleet Scaling");
            Global.getSector().addScript(new DraconisAICoreFleetInflater());
            log.info("Draconis:   - AI Core Fleet Inflater");
        } else {
            log.info("Draconis: AI Core Fleet Scaling DISABLED by settings");
        }

        // If Nexerelin is present, add the AI core acquisition system
        if (hasNexerelin) {
            log.info("Draconis: === Registering AI Core Acquisition System ===");

            // Check if already registered (prevents duplicates on save/load)
            // Check for DraconisAICoreDonationListener since it's always registered regardless of toggles
            boolean alreadyRegistered = false;
            for (Object script : Global.getSector().getScripts()) {
                if (script instanceof DraconisAICoreDonationListener) {
                    alreadyRegistered = true;
                    log.info("Draconis: AI Core system already registered - skipping");
                    break;
                }
            }

            if (!alreadyRegistered) {
                // AI Core Raids on Faction Markets
                if (enableAICoreRaids) {
                    log.info("Draconis: === Registering AI Core Raid System ===");
                    // Scanner - finds high-value AI core targets
                    Global.getSector().addScript(new DraconisSingleTargetScanner());
                    log.info("Draconis:   - AI Core Target Scanner");
                    // Raid Manager - triggers Shadow Fleet raids on high-value targets
                    Global.getSector().addScript(new DraconisAICoreRaidManager());
                    log.info("Draconis:   - AI Core Raid Manager");
                    // Monitor - watches for successful raids and steals AI cores
                    Global.getSector().addScript(new DraconisTargetedRaidMonitor());
                    log.info("Draconis:   - Targeted Raid Monitor");
                } else {
                    log.info("Draconis: AI Core Raids DISABLED by settings");
                }

                // Donation Listener - processes player AI core donations (always enabled)
                Global.getSector().addScript(new DraconisAICoreDonationListener());
                log.info("Draconis:   - AI Core Donation Listener");

                // Remnant Raid System - hunts Remnant installations for AI cores
                if (enableRemnantRaids) {
                    log.info("Draconis: === Registering Remnant Raid System ===");
                    Global.getSector().addScript(new DraconisRemnantTargetScanner());
                    log.info("Draconis:   - Remnant Target Scanner");
                    Global.getSector().addScript(new DraconisRemnantRaidManager());
                    log.info("Draconis:   - Remnant Raid Manager");
                    Global.getSector().addScript(new DraconisRemnantRaidListener());
                    log.info("Draconis:   - Remnant Raid Listener");
                } else {
                    log.info("Draconis: Remnant Raids DISABLED by settings");
                }

                log.info("Draconis: === AI Core Acquisition System Configuration Complete ===");
            }
        } else {
            log.info("Draconis: Nexerelin not present - AI core acquisition system disabled");
        }

        log.info("Draconis: === Game load complete ===");
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