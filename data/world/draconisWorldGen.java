package data.world;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorGeneratorPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

public class draconisWorldGen implements SectorGeneratorPlugin {
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
		FactionAPI XLII_draconis = sector.getFaction("XLII_draconis");

		// VENGEFUL / HOSTILE / INHOSPITABLE / SUSPICIOUS / NEUTRAL / FAVORABLE / WELCOMING / FRIENDLY / COOPERATIVE

		XLII_draconis.setRelationship(Factions.HEGEMONY, RepLevel.NEUTRAL);
		XLII_draconis.setRelationship(Factions.PERSEAN, RepLevel.NEUTRAL);
		XLII_draconis.setRelationship(Factions.INDEPENDENT, RepLevel.NEUTRAL);
		XLII_draconis.setRelationship(Factions.TRITACHYON, RepLevel.NEUTRAL);
		XLII_draconis.setRelationship(Factions.LUDDIC_PATH, RepLevel.HOSTILE);
		XLII_draconis.setRelationship(Factions.PIRATES, RepLevel.HOSTILE);
		XLII_draconis.setRelationship(Factions.LUDDIC_CHURCH, RepLevel.NEUTRAL);
		XLII_draconis.setRelationship(Factions.DIKTAT, RepLevel.NEUTRAL);
		XLII_draconis.setRelationship(Factions.PLAYER, RepLevel.NEUTRAL);
    }
}