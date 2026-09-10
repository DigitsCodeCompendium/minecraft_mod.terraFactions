package dev.terrafactions.network;

import dev.terrafactions.client.TerritoryRadarHud;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class TerraFactionsNetwork {
    private TerraFactionsNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("5").playToClient(
                TerritoryRadarPayload.TYPE,
                TerritoryRadarPayload.STREAM_CODEC,
                (payload, context) -> TerritoryRadarHud.accept(payload));
    }
}
