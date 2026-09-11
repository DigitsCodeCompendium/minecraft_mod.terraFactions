package dev.terrafactions.network;

import dev.terrafactions.TerraFactions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A territory action requested for one chunk from JourneyMap's fullscreen map. */
public record JourneyMapClaimPayload(ResourceLocation dimension, int chunkX, int chunkZ, Action action)
        implements CustomPacketPayload {
    public static final Type<JourneyMapClaimPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TerraFactions.MOD_ID, "journeymap_claim"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JourneyMapClaimPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeResourceLocation(payload.dimension);
                buffer.writeInt(payload.chunkX);
                buffer.writeInt(payload.chunkZ);
                buffer.writeEnum(payload.action);
            },
            buffer -> new JourneyMapClaimPayload(buffer.readResourceLocation(), buffer.readInt(), buffer.readInt(),
                    buffer.readEnum(Action.class)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        CLAIM_CORE,
        CLAIM_BORDER,
        UNCLAIM,
        LIBERATE
    }
}
