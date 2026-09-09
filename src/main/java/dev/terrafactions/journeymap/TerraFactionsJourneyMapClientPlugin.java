package dev.terrafactions.journeymap;

import dev.terrafactions.TerraFactions;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.event.MappingEvent;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.ClientEventRegistry;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

@JourneyMapPlugin(apiVersion = "2.0.0")
public final class TerraFactionsJourneyMapClientPlugin implements IClientPlugin {
    private static final ResourceLocation LAYERS_ICON =
            ResourceLocation.fromNamespaceAndPath("journeymap", "theme/flat/icon/layers.png");

    private boolean enabled;

    @Override
    public void initialize(IClientAPI api) {
        enabled = TerraFactionsClientConfig.OVERLAY_ENABLED.get();
        FullscreenEventRegistry.ADDON_BUTTON_DISPLAY_EVENT.subscribe(this, TerraFactions.MOD_ID, event -> {
            var button = event.getThemeButtonDisplay().addThemeToggleButton(
                    "Factions: On", "Factions: Off", LAYERS_ICON, enabled, pressed -> {
                        pressed.toggle();
                        enabled = pressed.getToggled();
                        TerraFactionsClientConfig.OVERLAY_ENABLED.set(enabled);
                        TerraFactionsClientConfig.OVERLAY_ENABLED.save();
                        sendPreference();
                    });
            button.setTooltip("Show or hide faction territory overlays");
        });
        ClientEventRegistry.MAPPING_EVENT.subscribe(this, TerraFactions.MOD_ID, event -> {
            if (event.getStage() == MappingEvent.Stage.MAPPING_STARTED) {
                sendPreference();
            }
        });
        TerraFactions.LOGGER.info("JourneyMap factions overlay toggle initialized");
    }

    private void sendPreference() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.connection != null) {
            minecraft.player.connection.sendCommand("tf overlay " + (enabled ? "on" : "off"));
        }
    }

    @Override
    public String getModId() {
        return TerraFactions.MOD_ID;
    }
}
