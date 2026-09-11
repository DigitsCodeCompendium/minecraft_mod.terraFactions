package dev.terrafactions.journeymap;

import dev.terrafactions.TerraFactions;
import dev.terrafactions.network.JourneyMapClaimPayload;
import dev.terrafactions.network.JourneyMapClaimPayload.Action;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.event.PopupMenuEvent;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/** Adds faction claim actions to JourneyMap's fullscreen right-click menu. */
@JourneyMapPlugin(apiVersion = "2.0.0", dependencies = {TerraFactions.MOD_ID})
public final class TerraFactionsJourneyMapClientPlugin implements IClientPlugin {
    @Override
    public void initialize(IClientAPI api) {
        FullscreenEventRegistry.FULLSCREEN_POPUP_MENU_EVENT.subscribe(
                TerraFactions.MOD_ID, this::addClaimMenu);
    }

    @Override
    public String getModId() {
        return TerraFactions.MOD_ID;
    }

    private void addClaimMenu(PopupMenuEvent.FullscreenPopupMenuEvent event) {
        ResourceKey<Level> dimension = event.getFullscreen().getUiState().dimension;
        var claimMenu = event.getPopupMenu().createSubItemList("journeymap.terrafactions.factions");
        claimMenu.addMenuItem("journeymap.terrafactions.claim_core",
                position -> requestAction(dimension, new ChunkPos(position), Action.CLAIM_CORE));
        claimMenu.addMenuItem("journeymap.terrafactions.claim_border",
                position -> requestAction(dimension, new ChunkPos(position), Action.CLAIM_BORDER));
        claimMenu.addMenuItem("journeymap.terrafactions.unclaim",
                position -> requestAction(dimension, new ChunkPos(position), Action.UNCLAIM));
        claimMenu.addMenuItem("journeymap.terrafactions.liberate",
                position -> requestAction(dimension, new ChunkPos(position), Action.LIBERATE));
    }

    private static void requestAction(ResourceKey<Level> dimension, ChunkPos chunk, Action action) {
        PacketDistributor.sendToServer(new JourneyMapClaimPayload(dimension.location(), chunk.x, chunk.z, action));
    }
}
