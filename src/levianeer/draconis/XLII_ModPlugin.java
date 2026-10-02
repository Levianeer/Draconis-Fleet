package levianeer.draconis;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.CampaignPlugin;
import levianeer.draconis.data.campaign.XLII_CampaignPlugin;
import levianeer.draconis.data.campaign.intel.fafnir.XLII_FafnirSystemMonitor;
import levianeer.draconis.data.campaign.intel.longsight.XLII_LongsightWatchdog;
import levianeer.draconis.data.campaign.intel.longsight.crisis.XLII_LongsightCrisisManager;
import levianeer.draconis.data.campaign.intel.longsight.crisis.XLII_LongsightCrisisCombatListener;
import com.fs.starfarer.api.combat.MissileAIPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.combat.ShipAPI;
import levianeer.draconis.data.campaign.characters.XLII_Characters;
import levianeer.draconis.data.campaign.companion.KorrinCommentScript;
import levianeer.draconis.data.campaign.companion.KorrinObserver;
import levianeer.draconis.data.campaign.companion.KorrinRateLimit;
import levianeer.draconis.data.campaign.companion.KorrinCompanion;
import levianeer.draconis.data.campaign.companion.KorrinTalkMenu;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import levianeer.draconis.data.campaign.intel.aicore.donation.DraconisAICoreDonationListener;
import levianeer.draconis.data.campaign.intel.aicore.listener.DraconisTargetedRaidMonitor;
import levianeer.draconis.data.campaign.intel.aicore.remnant.DraconisRemnantRaidListener;
import levianeer.draconis.data.campaign.intel.aicore.remnant.DraconisRemnantRaidManager;
import levianeer.draconis.data.campaign.intel.aicore.remnant.DraconisRemnantTargetScanner;
import levianeer.draconis.data.campaign.intel.aicore.scanner.DraconisSingleTargetScanner;
import levianeer.draconis.data.campaign.intel.aicore.raids.DraconisAICoreRaidManager;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import levianeer.draconis.data.campaign.events.DraconisAIOPaymentBarEventCreator;
import levianeer.draconis.data.campaign.events.XLII_FafnirTTBarEventCreator;
import levianeer.draconis.data.campaign.events.XLII_AIOOperativeBarEventCreator;
import levianeer.draconis.data.campaign.events.XLII_FafnirRingPortBarEventCreator;
import levianeer.draconis.data.campaign.events.XLII_MissionBarEventWatchdog;
import levianeer.draconis.data.campaign.intel.events.crisis.util.DraconisHostileActivityManager;
import levianeer.draconis.data.campaign.intel.events.crisis.listener.DraconisFleetCombatListener;
import levianeer.draconis.data.campaign.events.XLII_SectorTourListener;
import levianeer.draconis.data.campaign.econ.conditions.DraconConfig;
import levianeer.draconis.data.campaign.econ.conditions.DraconManager;
import levianeer.draconis.data.campaign.econ.conditions.DraconisSteelCurtainMonitor;
import levianeer.draconis.data.campaign.fleet.DraconisAICoreFleetInflater;
import levianeer.draconis.data.campaign.fleet.DraconisQRFManager;
import levianeer.draconis.data.campaign.fleet.DraconisAICoreScalingConfig;
import levianeer.draconis.data.campaign.fleet.DraconisWeaponEscalationMonitor;
import levianeer.draconis.data.scripts.ai.XLII_antiMissileAI;
import levianeer.draconis.data.scripts.ai.XLII_PhaseTorpedoAI;
import levianeer.draconis.data.scripts.ai.XLII_SabreAI;
import levianeer.draconis.data.scripts.ai.XLII_SlapERMissileAI;
import levianeer.draconis.data.scripts.world.XLII_WorldGen;
import levianeer.draconis.data.scripts.world.systems.XLII_System;
import levianeer.draconis.data.campaign.events.XLII_BastionDestructionMonitor;
import levianeer.draconis.data.campaign.intel.blind_eye.XLII_OfficeContactMonitor;
import levianeer.draconis.data.scripts.world.systems.XLII_OfficeGarrisonManager;
import levianeer.draconis.data.scripts.world.systems.XLII_OfficeSystem;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;

@SuppressWarnings("unused")
public class XLII_ModPlugin extends BaseModPlugin {

    // Terrible code, awful, possibly the worst.
    // Psalm 86:1
    private static final Logger log = Global.getLogger(XLII_ModPlugin.class);
    public static final String PD_MISSILE_ID = "XLII_swordbreaker_shot";
    public static final String PHASE_TORPEDO_ID = "XLII_phasetorp";
    public static final String SABRE_MISSILE_ID = "XLII_sabre_torp";
    public static final String SLAP_ER_MISSILE_ID = "XLII_SLAP-ER_torp";
    public static final String BILLHOOK_ID = "XLII_billhook_shot";
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
            log.info("Draconis: Story mission system enabled - 'The Nanoforge Gambit' available at Ring-Port");
        } else {
            log.info("Draconis: Nexerelin not detected - AI core system will be disabled");
            log.info("Draconis: Story mission system disabled (requires Nexerelin)");
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

        // Office Takeover crisis is actually triggered from XLII_NanoforgeExchange's give_uplink
        // branch (Stage 7 of work/outline/office-takeover-crisis-checklist.md); createIfNecessary()
        // here is a no-op unless DEBUG_FORCE_KEY was already set for real - same routine
        // safety-net re-registration pattern as XLII_LongsightWatchdog's own.
        XLII_LongsightCrisisManager.createIfNecessary();

        // Reset config singletons so they re-read settings.json on each game load.
        // Without this, the cached instance from a previous load (or onApplicationLoad)
        // persists and a settings change between loads would be ignored.
        DraconConfig.reset();
        DraconisAICoreScalingConfig.reset();
        DraconisWeaponEscalationMonitor.reset();
        KorrinTalkMenu.reset();
        KorrinRateLimit.reset();

        cleanupOldScripts();

        // Backfills the Rift beacon into saves made before it existed; see XLII_System.ensureRiftBeacon().
        XLII_System.ensureRiftBeacon();

        // Register campaign plugin (handles AI core officer picks, etc.)
        // Unregister first to prevent duplicates across save/load cycles
        Global.getSector().unregisterPlugin(XLII_CampaignPlugin.PLUGIN_ID);
        Global.getSector().registerPlugin(new XLII_CampaignPlugin());
        log.info("Draconis: Registered campaign plugin");

        // Register Fafnir system monitor whenever there is pending intercept work
        // (BF dialog not yet fired, BF 3-day timer running, or transverse jump possible).
        if (XLII_FafnirSystemMonitor.shouldRegister()) {
            Global.getSector().addScript(new XLII_FafnirSystemMonitor());
            log.info("Draconis:   - Fafnir System Monitor");
        }

        // Register Office contact monitor whenever the referral has been made but the courier
        // hasn't delivered Ladon's coordinates yet. First-time registration is handled by
        // XLII_BeginOfficeContact (mid-session); this re-registers it on subsequent loads.
        if (XLII_OfficeContactMonitor.shouldRegister()) {
            Global.getSector().addScript(new XLII_OfficeContactMonitor());
            log.info("Draconis:   - Office Contact Monitor");
        }

        // Register the bastion-destruction watch for a player who reloaded mid-Burn-the-Machine
        // finale (Kori strike done, Ladon not yet destroyed). First-time registration is handled
        // by XLII_KoriStrike.finalizeStrike().
        if (XLII_BastionDestructionMonitor.shouldRegister()) {
            Global.getSector().addScript(new XLII_BastionDestructionMonitor());
            log.info("Draconis:   - Bastion Destruction Monitor");
        }

        // Register Longsight watchdog only once the player actually holds the Longsight
        // uplink (Office Takeover / Cave ending) and the confrontation hasn't fired yet.
        // First-time registration is handled by XLII_NanoforgeExchange.give_uplink; this
        // re-registers it on subsequent loads. Previously gated on nanoforge delivery being
        // complete, which is true for virtually every player regardless of ending - see
        // XLII_LongsightWatchdog.UPLINK_GRANTED_FLAG's own notes for why that let the watchdog
        // re-arm for a player who went the Burn the Machine (destroy) route and never held the
        // core at all.
        boolean sigmaUplinkGranted = Global.getSector().getMemoryWithoutUpdate()
                .getBoolean(XLII_LongsightWatchdog.UPLINK_GRANTED_FLAG);
        boolean sigmaConfrontationDone = Global.getSector().getMemoryWithoutUpdate()
                .getBoolean(XLII_LongsightWatchdog.CONFRONTATION_FLAG);
        if (sigmaUplinkGranted && !sigmaConfrontationDone) {
            // Reset the warning flag on each load so a save captured after dismissal doesn't
            // permanently suppress re-triggering. The confrontation flag is the true one-shot
            // gate; the warning is expected to re-fire on load if rep is still in range.
            Global.getSector().getMemoryWithoutUpdate()
                    .unset(XLII_LongsightWatchdog.WARNING_FLAG);
            Global.getSector().addScript(new XLII_LongsightWatchdog());
            log.info("Draconis:   - Longsight Watchdog");
        }

        // Initialize Draconis characters
        log.info("Draconis: Initializing characters");
        XLII_Characters.initializeAllCharacters();

        // Korrin companion system - restores his intel entry and officer-slot modifier, then
        // re-registers the script that drives his comments and tracks the officer roster.
        KorrinCompanion.init();
        Global.getSector().addScript(new KorrinCommentScript());

        // Watches the base game on Korrin's behalf. Transient (not saved) - re-added fresh each
        // game load, no cleanup needed.
        KorrinObserver.register();
        log.info("Draconis:   - Korrin Observer (base-game reactions)");
        log.info("Draconis:   - Korrin Comment Script");

        // These four bar events used to be registered as BarEventCreators, which subjects them
        // to BarEventManager's random, capacity-limited sector-wide pick - unreliable for
        // narrative content that should show up as soon as its trigger condition is met.
        // XLII_MissionBarEventWatchdog now creates them deterministically instead. The old
        // creator instances are serialized into saves from prior mod versions, so strip any
        // stale ones on load to avoid the random path ever creating a duplicate instance.
        BarEventManager bar = BarEventManager.getInstance();
        if (bar != null) {
            bar.getCreators().removeIf(c ->
                    c instanceof XLII_FafnirTTBarEventCreator ||
                    c instanceof XLII_FafnirRingPortBarEventCreator ||
                    c instanceof XLII_AIOOperativeBarEventCreator ||
                    c instanceof DraconisAIOPaymentBarEventCreator);
        }
        Global.getSector().addScript(new XLII_MissionBarEventWatchdog());
        log.info("Draconis:   - Mission Bar Event Watchdog");

        // Add the crisis event system
        log.info("Draconis: Registering crisis systems");
        Global.getSector().addScript(new DraconisHostileActivityManager());
        log.info("Draconis:   - Hostile Activity Manager");

        // Register fleet combat listener for AIO tracker one-time factors.
        // Transient (not saved) - re-added fresh each game load, no cleanup needed.
        Global.getSector().getListenerManager().addListener(new DraconisFleetCombatListener(), true);
        log.info("Draconis:   - Fleet Combat Listener (AIO factors)");

        // Register sector-tour listener - detects victories over the Remnants/Omega/Threat/Dweller
        // for the sector-tour arc gating the Kori archive infiltration. Transient, same pattern as
        // the fleet combat listener above.
        XLII_SectorTourListener.migrate();
        Global.getSector().getListenerManager().addListener(new XLII_SectorTourListener(), true);
        log.info("Draconis:   - Sector Tour Listener (Remnant/Omega/Threat/Dweller)");

        // Office Takeover crisis combat listener - the player-vs-Draconis hostility axis (attacking
        // a crisis fleet costs Draconis rep, independent of the Sector-vs-Draconis fallout that
        // happens automatically once a market is actually captured). Transient, same pattern as the
        // other listeners above.
        Global.getSector().getListenerManager().addListener(new XLII_LongsightCrisisCombatListener(), true);
        log.info("Draconis:   - Longsight Crisis Combat Listener");

        // Add AI Core Fleet Scaling system
        if (enableAICoreFleetScaling) {
            log.info("Draconis: Registering AI Core Fleet Scaling");
            Global.getSector().addScript(new DraconisAICoreFleetInflater());
            log.info("Draconis:   - AI Core Fleet Inflater");
        } else {
            log.info("Draconis: AI Core Fleet Scaling DISABLED by settings");
        }

        // QRF Manager - dynamic assignment logic for HighCommand garrison fleets
        Global.getSector().addScript(new DraconisQRFManager());
        log.info("Draconis:   - QRF Manager");

        // Office Garrison Manager - single defending fleet for Ladon's one bastion. No-op if the
        // bastion doesn't exist (e.g. Nexerelin random sector) or was already destroyed.
        StarSystemAPI officeSystem = Global.getSector().getStarSystem(XLII_OfficeSystem.SYSTEM_ID);
        if (officeSystem != null) {
            SectorEntityToken bastion = officeSystem.getEntityById(XLII_OfficeSystem.BASTION_ID);
            if (bastion instanceof CampaignFleetAPI bastionFleet && !bastionFleet.isEmpty()) {
                Global.getSector().addScript(new XLII_OfficeGarrisonManager(officeSystem, bastionFleet, 5f));
                log.info("Draconis:   - Office Garrison Manager");
            }
        }

        // If Nexerelin is present, add DRACON system and AI core acquisition
        if (hasNexerelin) {
            // DRACON (Draconis Readiness Condition) - replaces Steel Curtain
            DraconConfig draconConfig = DraconConfig.getInstance();
            if (draconConfig.isEnabled()) {
                Global.getSector().addScript(new DraconManager());
                log.info("Draconis:   - DRACON Manager (Draconis Readiness Condition)");
            } else {
                Global.getSector().addScript(new DraconisSteelCurtainMonitor());
                log.info("Draconis:   - Steel Curtain Monitor (DRACON disabled by settings)");
            }
            log.info("Draconis: === Registering AI Core Acquisition System ===");

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
        } else {
            log.info("Draconis: Nexerelin not present - AI core acquisition system disabled");
            // Without Nex, fall back to Steel Curtain (static condition)
            Global.getSector().addScript(new DraconisSteelCurtainMonitor());
            log.info("Draconis:   - Steel Curtain Monitor (Nexerelin not present)");
        }

        log.info("Draconis: === Game load complete ===");
    }

    /**
     * Removes all Draconis EveryFrameScript instances from the sector before re-adding them.
     * Meant to prevent script accumulation across save/load cycles (scripts are serialized into saves,
     * so without cleanup each onGameLoad() would add duplicates).
     * Also handles 0.6.0 -> 0.6.1 migration: removes old DraconisSteelCurtainMonitor instances
     * that would conflict with the new DraconManager.
     */
    private void cleanupOldScripts() {
        List<EveryFrameScript> toRemove = new ArrayList<>();

        for (EveryFrameScript script : Global.getSector().getScripts()) {
            if (script instanceof DraconisHostileActivityManager
                    || script instanceof DraconisAICoreFleetInflater
                    || script instanceof DraconisSteelCurtainMonitor
                    || script instanceof DraconManager
                    || script instanceof DraconisSingleTargetScanner
                    || script instanceof DraconisAICoreRaidManager
                    || script instanceof DraconisTargetedRaidMonitor
                    || script instanceof DraconisAICoreDonationListener
                    || script instanceof DraconisRemnantTargetScanner
                    || script instanceof DraconisRemnantRaidManager
                    || script instanceof DraconisRemnantRaidListener
                    || script instanceof XLII_LongsightWatchdog
                    || script instanceof XLII_FafnirSystemMonitor
                    || script instanceof DraconisQRFManager
                    || script instanceof XLII_OfficeGarrisonManager
                    || script instanceof XLII_OfficeContactMonitor
                    || script instanceof XLII_BastionDestructionMonitor
                    || script instanceof KorrinCommentScript) {
                toRemove.add(script);
            }
        }

        for (EveryFrameScript script : toRemove) {
            Global.getSector().removeScript(script);
        }

        if (!toRemove.isEmpty()) {
            log.info("Draconis: Cleaned up " + toRemove.size() + " old script instance(s) from save");
        }
    }

    @Override
    public PluginPick<MissileAIPlugin> pickMissileAI(MissileAPI missile, ShipAPI launchingShip) {
        if (missile.getProjectileSpecId().equals(PD_MISSILE_ID)) {
            return new PluginPick<>(new XLII_antiMissileAI(missile, launchingShip), CampaignPlugin.PickPriority.MOD_SPECIFIC);
        }
        if (missile.getProjectileSpecId().equals(PHASE_TORPEDO_ID)) {
            // Phase torpedoes launched from weapons (not ship system) start unphased
            // Ship system handles its own AI creation with proper phase state
            boolean startedPhased = false;
            return new PluginPick<>(new XLII_PhaseTorpedoAI(missile, startedPhased), CampaignPlugin.PickPriority.MOD_SPECIFIC);
        }
        if (missile.getProjectileSpecId().equals(SABRE_MISSILE_ID)) {
            return new PluginPick<>(new XLII_SabreAI(missile, launchingShip), CampaignPlugin.PickPriority.MOD_SPECIFIC);
        }
        if (missile.getProjectileSpecId().equals(SLAP_ER_MISSILE_ID)) {
            return new PluginPick<>(new XLII_SlapERMissileAI(missile), CampaignPlugin.PickPriority.MOD_SPECIFIC);
        }
        return null;
    }
}