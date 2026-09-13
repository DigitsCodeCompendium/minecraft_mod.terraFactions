package dev.terrafactions.network;

import dev.terrafactions.TerraFactions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AnchorStateRequestPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<AnchorStateRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TerraFactions.MOD_ID, "anchor_state_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AnchorStateRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBlockPos(payload.pos),
            buffer -> new AnchorStateRequestPayload(buffer.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
