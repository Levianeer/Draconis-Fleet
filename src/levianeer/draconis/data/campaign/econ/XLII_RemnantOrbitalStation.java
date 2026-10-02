package levianeer.draconis.data.campaign.econ;

import com.fs.starfarer.api.impl.campaign.econ.impl.OrbitalStation;
import levianeer.draconis.data.campaign.ids.Factions;

/**
 * The Draconis-exclusive orbital station tier - a purpose-built Draconis station hull
 * (see {@code industries.csv}'s {@code XLII_orbitalstation_remnant} row) standing in for the
 * vanilla Low/Mid/High Tech tiers. Reuses vanilla's {@link OrbitalStation} wholesale; only
 * faction gating is added here, mirroring {@link XLII_HighCommand}'s isHidden()/isAvailableToBuild()
 * pattern.
 */
public class XLII_RemnantOrbitalStation extends OrbitalStation {

    private boolean isDraconis() {
        return market.getFactionId().equals(Factions.DRACONIS);
    }

    @Override
    public boolean isHidden() {
        return !isDraconis();
    }

    @Override
    public boolean isAvailableToBuild() {
        return isDraconis() && super.isAvailableToBuild();
    }

    @Override
    public String getUnavailableReason() {
        if (!isDraconis()) {
            return "Only available to the Draconis Defence Alliance";
        }
        return super.getUnavailableReason();
    }
}
