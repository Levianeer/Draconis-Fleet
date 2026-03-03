package levianeer.draconis.data.campaign;

import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.AICoreOfficerPlugin;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.CampaignPlugin;

public class XLII_CampaignPlugin extends BaseCampaignPlugin {

    public static final String PLUGIN_ID = "XLII_campaignPlugin";

    @Override
    public String getId() {
        return PLUGIN_ID;
    }

    @Override
    public PluginPick<AICoreOfficerPlugin> pickAICoreOfficerPlugin(String commodityId) {
        if (XLII_SigmaOctantisOfficerPlugin.CORE_ID.equals(commodityId)) {
            return new PluginPick<>(
                new XLII_SigmaOctantisOfficerPlugin(),
                CampaignPlugin.PickPriority.MOD_SPECIFIC
            );
        }
        return null;
    }
}