package levianeer.draconis.data.scripts.world;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorGeneratorPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

public class DraconisWorldGen implements SectorGeneratorPlugin {
	@Override
    public void generate(SectorAPI sector) {
		initFactionRelationships(sector);
    }

	@SuppressWarnings("unused")
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
		FactionAPI remnant = sector.getFaction(Factions.REMNANTS);
		FactionAPI XLII_draconis = sector.getFaction("XLII_draconis");

		// VENGEFUL / HOSTILE / INHOSPITABLE / SUSPICIOUS / NEUTRAL / FAVORABLE / WELCOMING / FRIENDLY / COOPERATIVE

		XLII_draconis.setRelationship(Factions.HEGEMONY, RepLevel.NEUTRAL);
		XLII_draconis.setRelationship(Factions.PERSEAN, RepLevel.NEUTRAL);
		XLII_draconis.setRelationship(Factions.INDEPENDENT, RepLevel.NEUTRAL);
		XLII_draconis.setRelationship(Factions.TRITACHYON, RepLevel.HOSTILE);
		XLII_draconis.setRelationship(Factions.LUDDIC_PATH, RepLevel.HOSTILE);
		XLII_draconis.setRelationship(Factions.PIRATES, RepLevel.HOSTILE);
		XLII_draconis.setRelationship(Factions.LUDDIC_CHURCH, RepLevel.HOSTILE);
		XLII_draconis.setRelationship(Factions.DIKTAT, RepLevel.NEUTRAL);
		XLII_draconis.setRelationship(Factions.PLAYER, RepLevel.NEUTRAL);
		XLII_draconis.setRelationship(Factions.REMNANTS, RepLevel.HOSTILE);

		// ========================================
		// MODDED FACTIONS
		// ========================================
        // Um, is this needed? I feel like there is a better way to do all this *without* NEX
        //
		// Add new modded factions here - one line per faction
		// Format: setModdedRelation(sector, XLII_draconis, "faction_id", RepLevel.RELATION);

		setModdedRelation(sector, XLII_draconis, "diableavionics", RepLevel.HOSTILE);
            // Why: Would probably be seen as actively hostile to the cause, plus free AI Cores.
        setModdedRelation(sector, XLII_draconis, "uaf", RepLevel.WELCOMING);
            // Why: Given the distance, their isolation etc, probably seen as more trustworthy.
    }

	/**
	 * Safely sets relationship with a modded faction
	 * Only applies if the faction exists (mod is installed)
	 * @param sector The sector
	 * @param draconis Draconis faction
	 * @param factionId The modded faction ID to check
	 * @param relation The relationship level to set
	 */
	private static void setModdedRelation(SectorAPI sector, FactionAPI draconis,
										 String factionId, RepLevel relation) {
		FactionAPI faction = sector.getFaction(factionId);
		if (faction != null) {
			draconis.setRelationship(factionId, relation);
		}
	}
}