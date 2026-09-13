package dev.terrafactions.network;

import dev.terrafactions.TerraFactions;
import dev.terrafactions.anchor.AnchorPowerState;
import dev.terrafactions.anchor.AnchorConnectionState;
import dev.terrafactions.anchor.AnchorVulnerabilityState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AnchorStatePayload(BlockPos pos, String factionName, String tierName, int powerTenthsPerClaim,
                                 int allocatedPower, int usablePowerTenths, int maximumPower,
                                 int projectedClaims, int projectedRadius, AnchorPowerState powerState,
                                 AnchorConnectionState connectionState,
                                 AnchorVulnerabilityState vulnerabilityState, long isolationSecondsRemaining)
        implements CustomPacketPayload {
    public static final Type<AnchorStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TerraFactions.MOD_ID, "anchor_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AnchorStatePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos);
                buffer.writeUtf(payload.factionName);
                buffer.writeUtf(payload.tierName);
                buffer.writeInt(payload.powerTenthsPerClaim);
                buffer.writeInt(payload.allocatedPower);
                buffer.writeInt(payload.usablePowerTenths);
                buffer.writeInt(payload.maximumPower);
                buffer.writeInt(payload.projectedClaims);
                buffer.writeInt(payload.projectedRadius);
                buffer.writeEnum(payload.powerState);
                buffer.writeEnum(payload.connectionState);
                buffer.writeEnum(payload.vulnerabilityState);
                buffer.writeLong(payload.isolationSecondsRemaining);
            },
            buffer -> new AnchorStatePayload(buffer.readBlockPos(), buffer.readUtf(), buffer.readUtf(),
                    buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(),
                    buffer.readInt(), buffer.readEnum(AnchorPowerState.class),
                    buffer.readEnum(AnchorConnectionState.class), buffer.readEnum(AnchorVulnerabilityState.class),
                    buffer.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
