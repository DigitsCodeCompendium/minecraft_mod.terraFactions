package dev.terrafactions.network;

import dev.terrafactions.TerraFactions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TerritoryRadarPayload(
        String territory,
        String faction,
        int relationColor,
        boolean vulnerable,
        boolean factionInfoVisible,
        int power,
        int maximumPower,
        boolean borderVulnerable,
        boolean coreVulnerable)
        implements CustomPacketPayload {
    public static final Type<TerritoryRadarPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TerraFactions.MOD_ID, "territory_radar"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerritoryRadarPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUtf(payload.territory);
                buffer.writeUtf(payload.faction);
                buffer.writeInt(payload.relationColor);
                buffer.writeBoolean(payload.vulnerable);
                buffer.writeBoolean(payload.factionInfoVisible);
                buffer.writeInt(payload.power);
                buffer.writeInt(payload.maximumPower);
                buffer.writeBoolean(payload.borderVulnerable);
                buffer.writeBoolean(payload.coreVulnerable);
            },
            buffer -> new TerritoryRadarPayload(
                    buffer.readUtf(), buffer.readUtf(), buffer.readInt(), buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readInt(), buffer.readInt(), buffer.readBoolean(), buffer.readBoolean()));

    public static TerritoryRadarPayload hidden() {
        return new TerritoryRadarPayload("", "", 0, false, false, 0, 0, false, false);
    }

    public boolean radarVisible() {
        return !territory.isEmpty();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
