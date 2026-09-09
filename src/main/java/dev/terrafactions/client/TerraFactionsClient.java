package dev.terrafactions.client;

import dev.terrafactions.TerraFactions;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@Mod(value = TerraFactions.MOD_ID, dist = Dist.CLIENT)
public final class TerraFactionsClient {
    public TerraFactionsClient(IEventBus modBus) {
        modBus.addListener(TerraFactionsClient::registerGuiLayers);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(TerraFactions.MOD_ID, "territory_radar"),
                TerritoryRadarHud::render);
    }
}
