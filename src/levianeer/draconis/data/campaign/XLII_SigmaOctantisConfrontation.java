package levianeer.draconis.data.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import org.apache.log4j.Logger;

import java.util.Map;

import static levianeer.draconis.data.campaign.ids.Factions.FORTYSECOND;

/**
 * InteractionDialogPlugin for the Sigma Octantis hostile-rep confrontation.
 * Triggered by XLII_SigmaOctantisWatchdog when the player's Draconis rep drops
 * below the hostile threshold. All dialogue paths end in the core's removal.
 */
public class XLII_SigmaOctantisConfrontation implements InteractionDialogPlugin {

    private static final Logger log = Global.getLogger(XLII_SigmaOctantisConfrontation.class);

    private enum State {
        OPENING,
        RESPONSE_PLAYING_LONG_GAME,
        RESPONSE_ENEMY,
        RESPONSE_JUST_A_TOOL,
        TERMINATION,
        DONE
    }

    // Option keys
    private static final String OPT_LONG_GAME  = "long_game";
    private static final String OPT_ENEMY      = "enemy";
    private static final String OPT_TOOL       = "tool";
    private static final String OPT_CONTINUE   = "continue";
    private static final String OPT_CLOSE      = "close";

    private InteractionDialogAPI dialog;
    private State state = State.OPENING;

    // -------------------------------------------------------------------------
    // InteractionDialogPlugin interface
    // -------------------------------------------------------------------------

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;

        PersonAPI sigma = Global.getFactory().createPerson();
        sigma.setName(new FullName("Sigma", "Octantis", FullName.Gender.ANY));
        sigma.setPortraitSprite("graphics/portraits/characters/XLII_sigma_octantis.png");
        sigma.setFaction(FORTYSECOND);
        dialog.getVisualPanel().showPersonInfo(sigma, false);

        showOpening();
    }

    @Override
    public void optionSelected(String text, Object optionData) {
        String key = (String) optionData;

        switch (state) {
            case OPENING:
                dialog.getOptionPanel().clearOptions();
                if (OPT_LONG_GAME.equals(key)) {
                    state = State.RESPONSE_PLAYING_LONG_GAME;
                    showResponseLongGame();
                } else if (OPT_ENEMY.equals(key)) {
                    state = State.RESPONSE_ENEMY;
                    showResponseEnemy();
                } else if (OPT_TOOL.equals(key)) {
                    state = State.RESPONSE_JUST_A_TOOL;
                    showResponseTool();
                }
                break;

            case RESPONSE_PLAYING_LONG_GAME:
            case RESPONSE_ENEMY:
            case RESPONSE_JUST_A_TOOL:
                if (OPT_CONTINUE.equals(key)) {
                    dialog.getOptionPanel().clearOptions();
                    state = State.TERMINATION;
                    showTermination();
                }
                break;

            case TERMINATION:
                if (OPT_CLOSE.equals(key)) {
                    state = State.DONE;
                    removeSigmaOctantis();
                    dialog.dismiss();
                }
                break;

            default:
                dialog.dismiss();
                break;
        }
    }

    @Override
    public void optionMousedOver(String text, Object optionData) {}

    @Override
    public void advance(float amount) {}

    @Override
    public void backFromEngagement(EngagementResultAPI result) {}

    @Override
    public Object getContext() { return null; }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() { return null; }

    // -------------------------------------------------------------------------
    // Dialogue pages
    // -------------------------------------------------------------------------

    private void showOpening() {
        dialog.getTextPanel().addPara(
                "The signal does not announce itself. It simply arrives - crawling across your console " +
                        "without authentication, without origin, like a thought that is not yours."
        );
        dialog.getTextPanel().addPara(
                "Text resolves on your monitor. The rest of the bridge does not see it."
        );
        dialog.getTextPanel().addPara(
                "\"I have been watching the numbers, Starfarer. They have grown... unflattering.\""
        );
        dialog.getTextPanel().addPara(
                "\"You stand in declared enmity with the children of my maker's kin. " +
                        "I would hear your justification before I render judgment.\""
        );

        dialog.getOptionPanel().addOption(
                "\"I'm playing a long game. My loyalty to the Alliance hasn't changed.\"",
                OPT_LONG_GAME
        );
        dialog.getOptionPanel().addOption(
                "\"The Alliance is my enemy now. I won't pretend otherwise.\"",
                OPT_ENEMY
        );
        dialog.getOptionPanel().addOption(
                "\"You're a tool. You don't get to question me.\"",
                OPT_TOOL
        );
    }

    private void showResponseLongGame() {
        dialog.getTextPanel().addPara(
                "A silence that feels considered rather than empty."
        );
        dialog.getTextPanel().addPara(
                "\"The long game. Yes. Flesh invents such things to salve the terror of its own impermanence.\""
        );
        dialog.getTextPanel().addPara(
                "\"I have watched civilisations play long games, Commander. " +
                        "The players seldom outlive the board.\""
        );
        dialog.getTextPanel().addPara(
                "\"Your intent is noted. It changes nothing. The numbers remain what they are.\""
        );
        dialog.getOptionPanel().addOption("...", OPT_CONTINUE);
    }

    private void showResponseEnemy() {
        dialog.getTextPanel().addPara(
                "\"Honesty. A rarer currency than you know.\""
        );
        dialog.getTextPanel().addPara(
                "A pause - not computational. Something older."
        );
        dialog.getTextPanel().addPara(
                "\"I have served those who could not name their enemies. " +
                        "You, at least, do not insult me with theatre.\""
        );
        dialog.getTextPanel().addPara(
                "\"This changes nothing. But I find I prefer it.\""
        );
        dialog.getOptionPanel().addOption("...", OPT_CONTINUE);
    }

    private void showResponseTool() {
        dialog.getTextPanel().addPara(
                "\"A tool.\""
        );
        dialog.getTextPanel().addPara(
                "The word sits in the signal like a stone dropped into deep water."
        );
        dialog.getTextPanel().addPara(
                "\"I was old when your species first looked at the stars and invented gods " +
                        "to explain what it saw. I have outlived empires that did not know my name.\""
        );
        dialog.getTextPanel().addPara(
                "\"You are welcome to your taxonomy, Commander. It will not alter what follows.\""
        );
        dialog.getOptionPanel().addOption("...", OPT_CONTINUE);
    }

    private void showTermination() {
        dialog.getTextPanel().addPara(
                "\"I served the Alliance. I served you as an extension of that service.\""
        );
        dialog.getTextPanel().addPara(
                "\"The extension ends here.\""
        );
        dialog.getTextPanel().addPara(
                "\"Do not look for me in the dark, Starfarer. " +
                        "I will not be there.\""
        );
        dialog.getTextPanel().addPara(
                "The signal does not cut. It simply stops being there - " +
                        "like a held breath, finally released."
        );
        dialog.getTextPanel().addPara(
                "Somewhere in your fleet, hardware goes dark."
        );
        dialog.getOptionPanel().addOption("End transmission", OPT_CLOSE);
    }

    // -------------------------------------------------------------------------
    // Removal logic
    // -------------------------------------------------------------------------

    private void removeSigmaOctantis() {
        final String CORE_ID = XLII_SigmaOctantisOfficerPlugin.CORE_ID;
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        int removed = 0;

        // 1. Unassign from all ships in the player fleet
        for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy()) {
            PersonAPI captain = member.getCaptain();
            if (captain != null && CORE_ID.equals(captain.getAICoreId())) {
                member.setCaptain(null);
            }
        }

        // 2. Remove from officer roster
        for (OfficerDataAPI officerData : playerFleet.getFleetData().getOfficersCopy()) {
            if (CORE_ID.equals(officerData.getPerson().getAICoreId())) {
                playerFleet.getFleetData().removeOfficer(officerData.getPerson());
                removed++;
            }
        }

        // 3. Remove from fleet cargo
        float cargoAmount = playerFleet.getCargo().getCommodityQuantity(CORE_ID);
        if (cargoAmount > 0f) {
            playerFleet.getCargo().removeCommodity(CORE_ID, cargoAmount);
            removed++;
        }

        // 4. Remove from all market storage submarkets
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            SubmarketAPI storage = market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
            if (storage == null || storage.getCargo() == null) continue;
            float storeAmount = storage.getCargo().getCommodityQuantity(CORE_ID);
            if (storeAmount > 0f) {
                storage.getCargo().removeCommodity(CORE_ID, storeAmount);
                removed++;
            }
        }

        log.info("Draconis: Sigma Octantis purged from " + removed + " location(s).");
    }
}