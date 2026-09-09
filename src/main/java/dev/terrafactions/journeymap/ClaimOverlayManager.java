package dev.terrafactions.journeymap;

import dev.terrafactions.TerraFactions;
import dev.terrafactions.factions.FactionSnapshot;
import dev.terrafactions.factions.FactionSnapshot.CapitalSnapshot;
import dev.terrafactions.factions.FactionSnapshot.ClaimSnapshot;
import dev.terrafactions.territory.TerritoryType;
import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.client.util.UIState;
import journeymap.api.v2.server.overlay.IServerOverlayAPI;
import journeymap.api.v2.server.overlay.OverlayShapeProps;
import journeymap.api.v2.server.overlay.ServerPolygon;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class ClaimOverlayManager {
    private final IServerOverlayAPI overlayApi;
    private final Map<UUID, Set<String>> overlayIds = new HashMap<>();
    private final Map<UUID, FactionSnapshot> knownFactions = new HashMap<>();
    private final Map<UUID, Integer> vulnerabilityMasks = new HashMap<>();
    private final Set<UUID> disabledPlayers = new HashSet<>();
    private boolean flashBright;
    private MinecraftServer server;

    ClaimOverlayManager(IServerOverlayAPI overlayApi) {
        this.overlayApi = overlayApi;
    }

    void initialize() {
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        server = player.getServer();
        syncAll(player);
        rememberCurrentFactions();
    }

    private void syncAll(ServerPlayer player) {
        overlayApi.clearAll(player, TerraFactions.MOD_ID);
        if (disabledPlayers.contains(player.getUUID())) {
            return;
        }
        for (FactionSnapshot faction : currentFactions()) {
            showFaction(player, faction);
        }
    }

    private void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        server = event.getServer();
        syncChanges();
    }

    private void updateFaction(FactionSnapshot faction) {
        removeFactionOverlays(faction.id());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!disabledPlayers.contains(player.getUUID())) {
                showFaction(player, faction);
            }
        }
    }

    private void removeFaction(UUID factionId) {
        removeFactionOverlays(factionId);
    }

    private void rememberCurrentFactions() {
        knownFactions.clear();
        for (FactionSnapshot faction : currentFactions()) {
            knownFactions.put(faction.id(), faction);
        }
    }

    private void syncChanges() {
        flashBright = !flashBright;
        Map<UUID, FactionSnapshot> current = new HashMap<>();
        for (FactionSnapshot faction : currentFactions()) {
            current.put(faction.id(), faction);
            int mask = vulnerabilityMask(faction);
            boolean vulnerabilityChanged = vulnerabilityMasks.getOrDefault(faction.id(), -1) != mask;
            vulnerabilityMasks.put(faction.id(), mask);
            if (!faction.equals(knownFactions.get(faction.id())) || vulnerabilityChanged || mask != 0) {
                updateFaction(faction);
            }
        }

        for (UUID removedId : new HashSet<>(knownFactions.keySet())) {
            if (!current.containsKey(removedId)) {
                removeFaction(removedId);
                vulnerabilityMasks.remove(removedId);
            }
        }

        knownFactions.clear();
        knownFactions.putAll(current);
    }

    private void removeFactionOverlays(UUID factionId) {
        Set<String> ids = overlayIds.remove(factionId);
        if (ids == null || server == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (String id : ids) {
                overlayApi.remove(player, TerraFactions.MOD_ID, id);
            }
        }
    }

    void setEnabled(ServerPlayer player, boolean enabled) {
        if (enabled) {
            disabledPlayers.remove(player.getUUID());
            syncAll(player);
        } else {
            disabledPlayers.add(player.getUUID());
            overlayApi.clearAll(player, TerraFactions.MOD_ID);
        }
    }

    private void showFaction(ServerPlayer player, FactionSnapshot faction) {
        Map<OverlayGroup, List<ClaimSnapshot>> claimsByGroup = new HashMap<>();
        for (ClaimSnapshot claim : faction.claims()) {
            ResourceLocation dimensionId = ResourceLocation.tryParse(claim.dimension());
            if (dimensionId == null) {
                TerraFactions.LOGGER.warn("Ignoring claim with invalid dimension '{}': {}, {}",
                        claim.dimension(), claim.x(), claim.z());
                continue;
            }

            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
            TerritoryType visualType = claim.type() == TerritoryType.CAPITAL ? TerritoryType.CORE : claim.type();
            claimsByGroup.computeIfAbsent(new OverlayGroup(dimension, visualType), ignored -> new ArrayList<>()).add(claim);
        }

        Set<String> ids = overlayIds.computeIfAbsent(faction.id(), ignored -> new HashSet<>());
        for (Map.Entry<OverlayGroup, List<ClaimSnapshot>> entry : claimsByGroup.entrySet()) {
            OverlayGroup group = entry.getKey();
            String id = overlayId(faction.id(), group.dimension(), group.type());
            ids.add(id);
            overlayApi.show(player, TerraFactions.MOD_ID,
                    new ServerPolygon(id, group.dimension(), ClaimPolygonMerger.merge(entry.getValue()),
                            shapeProperties(faction, group.type(),
                                    TerraFactions.territories().isVulnerable(faction.id(), group.type()),
                                    flashBright)));
        }
        showCapital(player, faction, ids);
    }

    private void showCapital(ServerPlayer player, FactionSnapshot faction, Set<String> ids) {
        CapitalSnapshot capital = faction.capital();
        if (capital == null) {
            return;
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(capital.dimension());
        if (dimensionId == null) {
            return;
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        String id = faction.id() + "/capital";
        ids.add(id);
        ClaimSnapshot capitalChunk = new ClaimSnapshot(
                capital.x(), capital.z(), capital.dimension(), TerritoryType.CORE);
        overlayApi.show(player, TerraFactions.MOD_ID,
                new ServerPolygon(id, dimension, ClaimPolygonMerger.merge(List.of(capitalChunk)),
                        capitalProperties(faction)));
    }

    @SuppressWarnings("deprecation")
    private static OverlayShapeProps shapeProperties(FactionSnapshot faction, TerritoryType type,
                                                     boolean vulnerable, boolean flashBright) {
        String description = faction.description();
        String title = description == null || description.isBlank()
                ? faction.name()
                : faction.name() + " - " + description;

        float normalFill = type != TerritoryType.BORDER ? 0.38f : 0.18f;
        float normalStrokeWidth = type != TerritoryType.BORDER ? 2.0f : 1.0f;
        float normalStrokeOpacity = type != TerritoryType.BORDER ? 0.95f : 0.60f;
        return new OverlayShapeProps(
                faction.color(),
                vulnerable ? (flashBright ? normalFill : normalFill * 0.30f) : normalFill,
                vulnerable ? 0xFF3030 : faction.color(),
                vulnerable ? normalStrokeWidth + 1.5f : normalStrokeWidth,
                vulnerable ? (flashBright ? 1.0f : 0.25f) : normalStrokeOpacity,
                1000,
                UIState.FULLSCREEN_ZOOM_MIN,
                UIState.ZOOM_IN_MAX,
                EnumSet.allOf(Context.UI.class),
                EnumSet.allOf(Context.MapType.class),
                null,
                null);
    }

    private static int vulnerabilityMask(FactionSnapshot faction) {
        int mask = 0;
        for (ClaimSnapshot claim : faction.claims()) {
            TerritoryType visualType = claim.type() == TerritoryType.CAPITAL ? TerritoryType.CORE : claim.type();
            if (TerraFactions.territories().isVulnerable(faction.id(), visualType)) {
                mask |= visualType == TerritoryType.BORDER ? 1 : 2;
            }
        }
        return mask;
    }

    @SuppressWarnings("deprecation")
    private static OverlayShapeProps capitalProperties(FactionSnapshot faction) {
        String description = faction.description();
        String title = description == null || description.isBlank()
                ? faction.name()
                : faction.name() + " - " + description;
        return new OverlayShapeProps(
                faction.color(),
                0.0f,
                faction.color(),
                0.0f,
                0.0f,
                1001,
                UIState.FULLSCREEN_ZOOM_MIN,
                UIState.ZOOM_IN_MAX,
                EnumSet.allOf(Context.UI.class),
                EnumSet.allOf(Context.MapType.class),
                faction.name(),
                title);
    }

    private List<FactionSnapshot> currentFactions() {
        return TerraFactions.territories().factions().allFactions();
    }

    private static String overlayId(UUID factionId, ResourceKey<Level> dimension, TerritoryType type) {
        return factionId + "/" + dimension.location() + "/" + type.name().toLowerCase();
    }

    private record OverlayGroup(ResourceKey<Level> dimension, TerritoryType type) {
    }
}
