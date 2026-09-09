package dev.terrafactions.network;

import dev.terrafactions.TerraFactions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TerritoryRadarPayload(String territory, String faction, int color)
        implements CustomPacketPayload {
    public static final Type<TerritoryRadarPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TerraFactions.MOD_ID, "territory_radar"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerritoryRadarPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUtf(payload.territory);
                buffer.writeUtf(payload.faction);
                buffer.writeInt(payload.color);
            },
            buffer -> new TerritoryRadarPayload(buffer.readUtf(), buffer.readUtf(), buffer.readInt()));

    public static TerritoryRadarPayload hidden() {
        return new TerritoryRadarPayload("", "", 0);
    }

    public boolean visible() {
        return !territory.isEmpty();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
