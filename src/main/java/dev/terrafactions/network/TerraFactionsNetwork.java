package dev.terrafactions.network;

import dev.terrafactions.client.TerritoryRadarHud;
import dev.terrafactions.TerraFactions;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class TerraFactionsNetwork {
    private TerraFactionsNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("9");
        registrar.playToClient(TerritoryRadarPayload.TYPE, TerritoryRadarPayload.STREAM_CODEC,
                (payload, context) -> TerritoryRadarHud.accept(payload));
        registrar.playToClient(FactionUiPayload.TYPE, FactionUiPayload.STREAM_CODEC,
                (payload, context) -> dev.terrafactions.client.FactionDashboardScreen.accept(payload));
        registrar.playToServer(FactionActionPayload.TYPE, FactionActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        TerraFactions.territories().handleUiAction(player, payload);
                    }
                }));
        registrar.playToServer(JourneyMapClaimPayload.TYPE, JourneyMapClaimPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        TerraFactions.territories().handleJourneyMapClaim(player, payload);
                    }
                }));
    }
}
