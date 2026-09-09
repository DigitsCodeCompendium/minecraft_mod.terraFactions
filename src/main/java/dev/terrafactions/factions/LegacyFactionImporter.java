package dev.terrafactions.factions;

import dev.terrafactions.territory.TerritoryClaim;
import dev.terrafactions.territory.TerritoryKey;
import dev.terrafactions.territory.TerritoryType;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Read-only parser for the former Fabric Factions and TerraFactions compatibility data. */
public final class LegacyFactionImporter {
    private static final String LEGACY_BORDER_FILE = "terrafactions_border_territory.dat";

    public LegacyImport read(MinecraftServer server) throws IOException {
        Path sharedDirectory = server.getServerDirectory().resolve("factions").toAbsolutePath().normalize();
        Path worldData = server.getWorldPath(LevelResource.ROOT).resolve("data").toAbsolutePath().normalize();

        Map<UUID, ImportedFaction> factions = readFactions(sharedDirectory.resolve("faction.dat"));
        List<ImportedMember> members = readMembers(sharedDirectory.resolve("user.dat"));
        List<TerritoryClaim> claims = new ArrayList<>(readCoreClaims(sharedDirectory.resolve("claim.dat")));
        Map<UUID, ImportedSettings> settings = readSettings(sharedDirectory.resolve("user.dat"));
        readCompatibilityData(worldData.resolve(LEGACY_BORDER_FILE), factions, claims);
        return new LegacyImport(List.copyOf(factions.values()), List.copyOf(members), List.copyOf(claims),
                Map.copyOf(settings), sharedDirectory, worldData.resolve(LEGACY_BORDER_FILE));
    }

    private static Map<UUID, ImportedFaction> readFactions(Path path) throws IOException {
        Map<UUID, ImportedFaction> result = new HashMap<>();
        for (CompoundTag tag : core(read(path))) {
            UUID id = uuid(tag, "ID");
            if (id == null) continue;
            String name = tag.getString("Name").trim();
            if (name.isBlank()) name = "Faction " + id.toString().substring(0, 8);
            String description = tag.getString("Description");
            int color = color(tag.getString("Color"));
            int power = tag.getInt("Power");
            Map<UUID, FactionRelation> relations = readRelations(tag);
            result.put(id, new ImportedFaction(id, name, description, color, power,
                    FactionTags.defaultFor(name, id), null, relations));
        }
        return result;
    }

    private static List<ImportedMember> readMembers(Path path) throws IOException {
        List<ImportedMember> result = new ArrayList<>();
        for (CompoundTag tag : core(read(path))) {
            UUID playerId = uuid(tag, "ID");
            UUID factionId = uuid(tag, "FactionID");
            if (playerId == null || factionId == null) continue;
            result.add(new ImportedMember(playerId, factionId,
                    enumValue(FactionRank.class, tag.getString("Rank"), FactionRank.MEMBER)));
        }
        return result;
    }

    private static Map<UUID, ImportedSettings> readSettings(Path path) throws IOException {
        Map<UUID, ImportedSettings> result = new HashMap<>();
        for (CompoundTag tag : core(read(path))) {
            UUID playerId = uuid(tag, "ID");
            if (playerId == null) continue;
            result.put(playerId, new ImportedSettings(tag.getBoolean("Radar"),
                    enumValue(FactionChatMode.class, tag.getString("Chat"), FactionChatMode.GLOBAL)));
        }
        return result;
    }

    private static List<TerritoryClaim> readCoreClaims(Path path) throws IOException {
        List<TerritoryClaim> result = new ArrayList<>();
        for (CompoundTag tag : core(read(path))) {
            UUID factionId = uuid(tag, "FactionID");
            if (factionId == null) continue;
            String dimension = tag.getString("Level");
            if (dimension.isBlank()) dimension = "minecraft:overworld";
            TerritoryKey key = new TerritoryKey(dimension, tag.getInt("X"), tag.getInt("Z"));
            result.add(new TerritoryClaim(key, factionId, TerritoryType.CORE));
        }
        return result;
    }

    private static void readCompatibilityData(Path path, Map<UUID, ImportedFaction> factions,
                                              List<TerritoryClaim> claims) throws IOException {
        CompoundTag root = read(path);
        if (root == null) return;
        CompoundTag data = root.contains("data", Tag.TAG_COMPOUND) ? root.getCompound("data") : root;
        for (CompoundTag tag : compounds(data.getList("claims", Tag.TAG_COMPOUND))) {
            UUID factionId = uuid(tag, "faction");
            if (factionId == null) continue;
            TerritoryKey key = legacyKey(tag);
            boolean alreadyCore = claims.stream().anyMatch(claim -> claim.key().equals(key));
            if (!alreadyCore) claims.add(new TerritoryClaim(key, factionId, TerritoryType.BORDER));
        }
        for (CompoundTag tag : compounds(data.getList("capitals", Tag.TAG_COMPOUND))) {
            UUID factionId = uuid(tag, "faction");
            ImportedFaction faction = factions.get(factionId);
            if (faction != null) factions.put(factionId, faction.withCapital(legacyKey(tag)));
        }
        for (CompoundTag tag : compounds(data.getList("tags", Tag.TAG_COMPOUND))) {
            UUID factionId = uuid(tag, "faction");
            ImportedFaction faction = factions.get(factionId);
            String importedTag = tag.getString("tag").toUpperCase(Locale.ROOT);
            if (faction != null && importedTag.matches("[A-Z0-9_]{1,4}")) {
                factions.put(factionId, faction.withTag(importedTag));
            }
        }
    }

    private static TerritoryKey legacyKey(CompoundTag tag) {
        return new TerritoryKey(tag.getString("dimension"), tag.getInt("x"), tag.getInt("z"));
    }

    private static CompoundTag read(Path path) throws IOException {
        return Files.isRegularFile(path) ? NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()) : null;
    }

    private static List<CompoundTag> core(CompoundTag root) {
        return root == null ? List.of() : compounds(root.getList("CORE", Tag.TAG_COMPOUND));
    }

    private static List<CompoundTag> compounds(ListTag tags) {
        List<CompoundTag> result = new ArrayList<>(tags.size());
        for (int index = 0; index < tags.size(); index++) result.add(tags.getCompound(index));
        return result;
    }

    private static UUID uuid(CompoundTag tag, String key) {
        return tag.hasUUID(key) ? tag.getUUID(key) : null;
    }

    private static Map<UUID, FactionRelation> readRelations(CompoundTag faction) {
        Map<UUID, FactionRelation> result = new HashMap<>();
        ListTag values = faction.getList("Relationships", Tag.TAG_COMPOUND);
        for (CompoundTag relationship : compounds(values)) {
            UUID target = uuid(relationship, "Target");
            if (target == null) target = uuid(relationship, "target");
            String status = relationship.getString("Status");
            if (status.isBlank()) status = relationship.getString("status");
            if (target == null) continue;
            if (status.equalsIgnoreCase("ALLY") || status.equalsIgnoreCase("ALLIED")) {
                result.put(target, FactionRelation.ALLIED);
            } else if (status.equalsIgnoreCase("ENEMY")) {
                result.put(target, FactionRelation.ENEMY);
            }
        }
        return result;
    }

    private static int color(String name) {
        ChatFormatting formatting = ChatFormatting.getByName(name.toLowerCase(Locale.ROOT));
        return formatting == null || formatting.getColor() == null ? 0xAAAAAA : formatting.getColor();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    public record LegacyImport(List<ImportedFaction> factions, List<ImportedMember> members,
                               List<TerritoryClaim> claims, Map<UUID, ImportedSettings> settings,
                               Path sharedSource, Path borderSource) {
        public String summary() {
            long core = claims.stream().filter(claim -> claim.type() == TerritoryType.CORE).count();
            long border = claims.size() - core;
            return factions.size() + " factions, " + members.size() + " members, " + core
                    + " core claims, and " + border + " border claims";
        }
    }

    public record ImportedFaction(UUID id, String name, String description, int color, int power,
                                  String tag, TerritoryKey capital, Map<UUID, FactionRelation> relations) {
        ImportedFaction withCapital(TerritoryKey value) {
            return new ImportedFaction(id, name, description, color, power, tag, value, relations);
        }

        ImportedFaction withTag(String value) {
            return new ImportedFaction(id, name, description, color, power, value, capital, relations);
        }
    }

    public record ImportedMember(UUID playerId, UUID factionId, FactionRank rank) {
    }

    public record ImportedSettings(boolean radar, FactionChatMode chatMode) {
    }
}
