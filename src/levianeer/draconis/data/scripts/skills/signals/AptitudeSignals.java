package levianeer.draconis.data.scripts.skills.signals;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import levianeer.draconis.data.campaign.econ.XLII_HighCommand;
import levianeer.draconis.data.campaign.ids.Factions;
import second_in_command.SCData;
import second_in_command.specs.SCAptitudeSection;
import second_in_command.specs.SCBaseAptitudePlugin;

public class AptitudeSignals extends SCBaseAptitudePlugin {

    // Against SiC's ~12 base aptitudes at ~1 each: roughly 25% per Draconis market per month-end
    // refresh, and 28-35% on Draconis patrols. See .claude/systems/signals-aptitude.md.
    private static final float DRACONIS_MARKET_WEIGHT_MULT = 12f;
    private static final float DRACONIS_FLEET_WEIGHT = 3f;
    private static final float OTHER_FLEET_WEIGHT = 0.25f;

    @Override
    public String getOriginSkillId() {
        return "XLII_signals_doctrine";
    }

    @Override
    public void createSections() {
        SCAptitudeSection core = new SCAptitudeSection(true, 0, "combat2");
        core.addSkill("XLII_signals_ghost_contacts");
        core.addSkill("XLII_signals_hardened_emitters");
        core.addSkill("XLII_signals_fire_control_spoofing");
        core.addSkill("XLII_signals_seeker_blinding");
        core.addSkill("XLII_signals_interlock");
        core.addSkill("XLII_signals_comms_disruption");
        addSection(core);

        SCAptitudeSection advanced = new SCAptitudeSection(true, 2, "combat3");
        advanced.addSkill("XLII_signals_target_painting");
        advanced.addSkill("XLII_signals_crosslink");
        addSection(advanced);

        SCAptitudeSection capstone = new SCAptitudeSection(false, 4, "combat4");
        capstone.addSkill("XLII_signals_overclocked_arrays");
        capstone.addSkill("XLII_signals_full_spectrum");
        addSection(capstone);
    }

    @Override
    public Float getNPCFleetSpawnWeight(SCData data, CampaignFleetAPI fleet) {
        return isDraconis(fleet.getFaction().getId()) ? DRACONIS_FLEET_WEIGHT : OTHER_FLEET_WEIGHT;
    }

    // SiC takes guaranteed aptitudes before its weighted roll, so these fleets never consult the
    // weight above. Still gated by SiC's "NPCs spawn with skills" setting. SiC asks on first
    // inflation, not at spawn, so the faction is whatever the fleet flies under by then.
    @Override
    public Boolean guaranteePick(CampaignFleetAPI fleet) {
        String factionId = fleet.getFaction().getId();
        return Factions.FORTYSECOND.equals(factionId)
                || Factions.INTELLIGENCE_OFFICE.equals(factionId)
                || fleet.getMemoryWithoutUpdate().getBoolean(XLII_HighCommand.DETACHMENT_KEY);
    }

    // Only recruitable from Draconis comm directories; never found in cryopods.
    @Override
    public Float getMarketSpawnweight(MarketAPI market) {
        if (!isDraconis(market.getFactionId())) return 0f;
        return spec.getSpawnWeight() * DRACONIS_MARKET_WEIGHT_MULT;
    }

    @Override
    public Float getCryopodSpawnWeight(StarSystemAPI system) {
        return 0f;
    }

    @Override
    public void addCodexDescription(TooltipMakerAPI tooltip) {
        tooltip.addPara("Signals is a combat aptitude built on electronic warfare. Its skills scale with the fleet's "
                + "ECM advantage, the enemy weapon range penalty from your ECM rating, and reach full strength at %s. "
                + "It rewards fleets that field ECM Packages and hold sensor jammers. "
                + "Signals officers keep the doctrine of the old XLII Signal Corps and are only found for hire on Alliance worlds, "
                + "though every XLII Battlegroup and Alliance Intelligence Office fleet carries one.",
                0f, Misc.getTextColor(), Misc.getHighlightColor(), "Signals", "ECM advantage", "10%");
    }

    private static boolean isDraconis(String factionId) {
        return Factions.DRACONIS.equals(factionId)
                || Factions.FORTYSECOND.equals(factionId)
                || Factions.INTELLIGENCE_OFFICE.equals(factionId);
    }
}
