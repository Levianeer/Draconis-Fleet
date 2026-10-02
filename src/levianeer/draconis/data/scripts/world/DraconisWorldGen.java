package levianeer.draconis.data.scripts.world;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorGeneratorPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import static levianeer.draconis.data.campaign.ids.Factions.*;

public class DraconisWorldGen implements SectorGeneratorPlugin {
	@Override
    public void generate(SectorAPI sector) {
		initFactionRelationships(sector);
    }

    public static void initFactionRelationships(SectorAPI sector) {
		FactionAPI XLII_draconis = sector.getFaction(DRACONIS);

		for (FactionAPI other : Global.getSector().getAllFactions()) {
			if (!other.getId().equals(DRACONIS)
				&& !other.getId().equals(Factions.TRITACHYON)	// Legally mandated "friendship"
				&& !other.getId().equals(INTELLIGENCE_OFFICE)	// Draconis's own intelligence branch
				&& !other.getId().equals(FORTYSECOND))			// Draconis's own special forces
			{
				XLII_draconis.setRelationship(other.getId(), RepLevel.INHOSPITABLE);
			}
		}

		// VENGEFUL / HOSTILE / INHOSPITABLE / SUSPICIOUS / NEUTRAL / FAVORABLE / WELCOMING / FRIENDLY / COOPERATIVE

		XLII_draconis.setRelationship(FORTYSECOND, RepLevel.NEUTRAL);
		XLII_draconis.setRelationship(INTELLIGENCE_OFFICE, RepLevel.NEUTRAL);

		// Manual overrides for specific factions
		XLII_draconis.setRelationship(Factions.INDEPENDENT, RepLevel.HOSTILE); // Secruity concerns :)
		XLII_draconis.setRelationship(Factions.PIRATES, RepLevel.HOSTILE); // Usual Suspects
		XLII_draconis.setRelationship(Factions.LUDDIC_CHURCH, RepLevel.HOSTILE); // They justifiably hate us
		XLII_draconis.setRelationship(Factions.LUDDIC_PATH, RepLevel.HOSTILE); // They VERY justifiably hate us
		XLII_draconis.setRelationship(Factions.REMNANTS, RepLevel.HOSTILE); // Ops wanted some initiative, blew up their entire quadrant
    }
}