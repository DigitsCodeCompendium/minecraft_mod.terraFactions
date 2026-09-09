package dev.terrafactions.journeymap;

import dev.terrafactions.TerraFactions;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.server.IServerAPI;
import journeymap.api.v2.server.IServerPlugin;
import net.minecraft.server.level.ServerPlayer;

@JourneyMapPlugin(apiVersion = "2.0.0")
public final class TerraFactionsJourneyMapPlugin implements IServerPlugin {
    private static ClaimOverlayManager overlayManager;

    @Override
    public synchronized void initialize(IServerAPI api) {
        if (overlayManager == null) {
            overlayManager = new ClaimOverlayManager(api.getOverlayApi());
            overlayManager.initialize();
        }
        TerraFactions.LOGGER.info("Native NeoForge JourneyMap integration initialized");
    }

    @Override
    public String getModId() {
        return TerraFactions.MOD_ID;
    }

    public static boolean setOverlayEnabled(ServerPlayer player, boolean enabled) {
        ClaimOverlayManager manager = overlayManager;
        if (manager == null) {
            return false;
        }
        manager.setEnabled(player, enabled);
        return true;
    }
}
