package data.world;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorGeneratorPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.Global;

public class draconisLove implements SectorGeneratorPlugin {
	@Override
    public void generate(SectorAPI sector) {
		initFactionRelationships(sector);
    }

	public static void initFactionRelationships(SectorAPI sector) {
        	FactionAPI hegemony = sector.getFaction(Factions.HEGEMONY);
		FactionAPI tritachyon = sector.getFaction(Factions.TRITACHYON);
		FactionAPI pirates = sector.getFaction(Factions.PIRATES);
		FactionAPI independent = sector.getFaction(Factions.INDEPENDENT);
		FactionAPI church = sector.getFaction(Factions.LUDDIC_CHURCH);
		FactionAPI path = sector.getFaction(Factions.LUDDIC_PATH);
		FactionAPI player = sector.getFaction(Factions.PLAYER);
		FactionAPI diktat = sector.getFaction(Factions.DIKTAT);
        	FactionAPI league = sector.getFaction(Factions.PERSEAN);
        	FactionAPI fsdf_draconis = sector.getFaction("fsdf_draconis");

///DO NOT CHANGE ANYTHING ELSE APART FROM REP LEVELS - YOU AIN'T THAT SMART!
/// REP LEVELS: VENGEFUL/HOSTILE/INHOSPITABLE/SUSPICIOUS/NEUTRAL/FAVORABLE/WELCOMING/FRIENDLY/COOPERATIVE
///STARTING REP LEVELS

		fsdf_draconis.setRelationship(Factions.HEGEMONY, RepLevel.NEUTRAL);
		fsdf_draconis.setRelationship(Factions.PERSEAN, RepLevel.FRIENDLY);
		fsdf_draconis.setRelationship(Factions.INDEPENDENT, RepLevel.NEUTRAL);
		fsdf_draconis.setRelationship(Factions.TRITACHYON, RepLevel.NEUTRAL);
		fsdf_draconis.setRelationship(Factions.LUDDIC_PATH, RepLevel.HOSTILE);
		fsdf_draconis.setRelationship(Factions.PIRATES, RepLevel.HOSTILE);
		fsdf_draconis.setRelationship(Factions.LUDDIC_CHURCH, RepLevel.NEUTRAL);
		fsdf_draconis.setRelationship(Factions.DIKTAT, RepLevel.FRIENDLY);
		fsdf_draconis.setRelationship(Factions.PLAYER, RepLevel.NEUTRAL);
    }

}