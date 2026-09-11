package dev.terrafactions.network;

import dev.terrafactions.TerraFactions;
import dev.terrafactions.factions.FactionChatMode;
import dev.terrafactions.factions.FactionRank;
import dev.terrafactions.factions.FactionRelation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Complete read-only snapshot used by the faction management screen. */
public record FactionUiPayload(
        String name, String description, String tag, int color, int rankOrdinal,
        int power, int maximumPower, int claimUsage, int deathLoss,
        int basePower, int powerPerMember, int coreClaimCost, int borderClaimCost,
        int capitalClaims, int coreClaims, int borderClaims, String capital,
        boolean coreVulnerable, boolean borderVulnerable, boolean radarEnabled, int chatModeOrdinal,
        List<MemberEntry> members, List<LossEntry> losses,
        List<FactionEntry> factions) implements CustomPacketPayload {

    public static final Type<FactionUiPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TerraFactions.MOD_ID, "faction_ui"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FactionUiPayload> STREAM_CODEC = StreamCodec.of(
            FactionUiPayload::write, FactionUiPayload::read);

    public FactionUiPayload {
        members = List.copyOf(members);
        losses = List.copyOf(losses);
        factions = List.copyOf(factions);
    }

    public static FactionUiPayload empty() {
        return new FactionUiPayload("", "", "", 0xAAAAAA, -1,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "", false, false, true,
                FactionChatMode.GLOBAL.ordinal(), List.of(), List.of(), List.of());
    }

    public boolean hasFaction() {
        return rankOrdinal >= 0;
    }

    public FactionRank rank() {
        return enumValue(FactionRank.values(), rankOrdinal, null);
    }

    public FactionChatMode chatMode() {
        return enumValue(FactionChatMode.values(), chatModeOrdinal, FactionChatMode.GLOBAL);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, FactionUiPayload value) {
        buffer.writeUtf(value.name);
        buffer.writeUtf(value.description);
        buffer.writeUtf(value.tag);
        buffer.writeInt(value.color);
        buffer.writeInt(value.rankOrdinal);
        buffer.writeInt(value.power);
        buffer.writeInt(value.maximumPower);
        buffer.writeInt(value.claimUsage);
        buffer.writeInt(value.deathLoss);
        buffer.writeInt(value.basePower);
        buffer.writeInt(value.powerPerMember);
        buffer.writeInt(value.coreClaimCost);
        buffer.writeInt(value.borderClaimCost);
        buffer.writeInt(value.capitalClaims);
        buffer.writeInt(value.coreClaims);
        buffer.writeInt(value.borderClaims);
        buffer.writeUtf(value.capital);
        buffer.writeBoolean(value.coreVulnerable);
        buffer.writeBoolean(value.borderVulnerable);
        buffer.writeBoolean(value.radarEnabled);
        buffer.writeInt(value.chatModeOrdinal);
        buffer.writeVarInt(value.members.size());
        value.members.forEach(entry -> entry.write(buffer));
        buffer.writeVarInt(value.losses.size());
        value.losses.forEach(entry -> entry.write(buffer));
        buffer.writeVarInt(value.factions.size());
        value.factions.forEach(entry -> entry.write(buffer));
    }

    private static FactionUiPayload read(RegistryFriendlyByteBuf buffer) {
        String name = buffer.readUtf();
        String description = buffer.readUtf(256);
        String tag = buffer.readUtf(4);
        int color = buffer.readInt();
        int rank = buffer.readInt();
        int power = buffer.readInt();
        int maximumPower = buffer.readInt();
        int claimUsage = buffer.readInt();
        int deathLoss = buffer.readInt();
        int basePower = buffer.readInt();
        int powerPerMember = buffer.readInt();
        int coreClaimCost = buffer.readInt();
        int borderClaimCost = buffer.readInt();
        int capitalClaims = buffer.readInt();
        int coreClaims = buffer.readInt();
        int borderClaims = buffer.readInt();
        String capital = buffer.readUtf();
        boolean coreVulnerable = buffer.readBoolean();
        boolean borderVulnerable = buffer.readBoolean();
        boolean radarEnabled = buffer.readBoolean();
        int chatMode = buffer.readInt();
        List<MemberEntry> members = readList(buffer, MemberEntry::read);
        List<LossEntry> losses = readList(buffer, LossEntry::read);
        List<FactionEntry> factions = readList(buffer, FactionEntry::read);
        return new FactionUiPayload(name, description, tag, color, rank, power, maximumPower,
                claimUsage, deathLoss, basePower, powerPerMember, coreClaimCost, borderClaimCost,
                capitalClaims, coreClaims, borderClaims, capital,
                coreVulnerable, borderVulnerable, radarEnabled, chatMode, members, losses, factions);
    }

    private static <T> List<T> readList(RegistryFriendlyByteBuf buffer, Reader<T> reader) {
        int size = Math.min(buffer.readVarInt(), 4096);
        List<T> result = new ArrayList<>(size);
        for (int index = 0; index < size; index++) result.add(reader.read(buffer));
        return result;
    }

    private static <T> T enumValue(T[] values, int ordinal, T fallback) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }

    public record MemberEntry(String name, int rankOrdinal, boolean online, int deathLoss) {
        void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(name);
            buffer.writeInt(rankOrdinal);
            buffer.writeBoolean(online);
            buffer.writeInt(deathLoss);
        }

        static MemberEntry read(RegistryFriendlyByteBuf buffer) {
            return new MemberEntry(buffer.readUtf(), buffer.readInt(), buffer.readBoolean(), buffer.readInt());
        }

        public FactionRank rank() {
            return enumValue(FactionRank.values(), rankOrdinal, FactionRank.MEMBER);
        }
    }

    public record LossEntry(String name, int amount, boolean currentMember) {
        void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(name);
            buffer.writeInt(amount);
            buffer.writeBoolean(currentMember);
        }

        static LossEntry read(RegistryFriendlyByteBuf buffer) {
            return new LossEntry(buffer.readUtf(), buffer.readInt(), buffer.readBoolean());
        }
    }

    public record FactionEntry(String name, String tag, int color, int memberCount, int relationOrdinal,
                               int outgoingDeclarationOrdinal, int incomingDeclarationOrdinal) {
        void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(name);
            buffer.writeUtf(tag);
            buffer.writeInt(color);
            buffer.writeInt(memberCount);
            buffer.writeInt(relationOrdinal);
            buffer.writeInt(outgoingDeclarationOrdinal);
            buffer.writeInt(incomingDeclarationOrdinal);
        }

        static FactionEntry read(RegistryFriendlyByteBuf buffer) {
            return new FactionEntry(buffer.readUtf(), buffer.readUtf(4), buffer.readInt(),
                    buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt());
        }

        public FactionRelation relation() {
            return enumValue(FactionRelation.values(), relationOrdinal, FactionRelation.NEUTRAL);
        }

        public FactionRelation outgoingDeclaration() {
            return enumValue(FactionRelation.values(), outgoingDeclarationOrdinal, FactionRelation.NEUTRAL);
        }

        public FactionRelation incomingDeclaration() {
            return enumValue(FactionRelation.values(), incomingDeclarationOrdinal, FactionRelation.NEUTRAL);
        }

        public boolean allyProposed() {
            return relation() == FactionRelation.NEUTRAL
                    && outgoingDeclaration() == FactionRelation.ALLIED
                    && incomingDeclaration() != FactionRelation.ALLIED;
        }

        public boolean allyRequested() {
            return relation() == FactionRelation.NEUTRAL
                    && incomingDeclaration() == FactionRelation.ALLIED
                    && outgoingDeclaration() != FactionRelation.ALLIED;
        }
    }

    @FunctionalInterface
    private interface Reader<T> {
        T read(RegistryFriendlyByteBuf buffer);
    }
}
