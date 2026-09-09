package dev.terrafactions;

import com.mojang.logging.LogUtils;
import dev.terrafactions.journeymap.TerraFactionsClientConfig;
import dev.terrafactions.network.TerraFactionsNetwork;
import dev.terrafactions.territory.TerraFactionsConfig;
import dev.terrafactions.territory.TerritoryService;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(TerraFactions.MOD_ID)
public final class TerraFactions {
    public static final String MOD_ID = "terrafactions";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static TerritoryService territories;

    public TerraFactions(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, TerraFactionsConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, TerraFactionsClientConfig.SPEC);
        modBus.addListener(TerraFactionsNetwork::registerPayloads);
        territories = new TerritoryService();
        territories.register();
        LOGGER.info("TerraFactions initialized on NeoForge");
    }

    public static TerritoryService territories() {
        return territories;
    }
}
