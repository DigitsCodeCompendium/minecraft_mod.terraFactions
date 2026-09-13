package dev.terrafactions.journeymap;

import dev.terrafactions.TerraFactions;
import dev.terrafactions.anchor.AnchorMapSnapshot;
import dev.terrafactions.anchor.AnchorNetworkRules;
import dev.terrafactions.anchor.AnchorNetworkRules.LinkType;
import dev.terrafactions.anchor.AnchorVulnerabilityState;
import dev.terrafactions.factions.FactionSnapshot;
import dev.terrafactions.factions.FactionSnapshot.CapitalSnapshot;
import dev.terrafactions.factions.FactionSnapshot.ClaimSnapshot;
import dev.terrafactions.territory.TerritoryType;
import dev.terrafactions.territory.TerritoryClaim;
import dev.terrafactions.territory.TerritoryKey;
import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.client.util.UIState;
import journeymap.api.v2.server.overlay.IServerOverlayAPI;
import journeymap.api.v2.server.overlay.OverlayShapeProps;
import journeymap.api.v2.server.overlay.OverlayPoints;
import journeymap.api.v2.server.overlay.OverlayPolygon;
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
    private final Map<UUID, List<AnchorMapSnapshot>> knownAnchors = new HashMap<>();
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
        knownAnchors.clear();
        knownAnchors.putAll(currentAnchors());
    }

    private void syncChanges() {
        flashBright = !flashBright;
        Map<UUID, List<AnchorMapSnapshot>> anchors = currentAnchors();
        Map<UUID, FactionSnapshot> current = new HashMap<>();
        for (FactionSnapshot faction : currentFactions()) {
            current.put(faction.id(), faction);
            int mask = vulnerabilityMask(faction);
            boolean vulnerabilityChanged = vulnerabilityMasks.getOrDefault(faction.id(), -1) != mask;
            vulnerabilityMasks.put(faction.id(), mask);
            if (!faction.equals(knownFactions.get(faction.id()))
                    || !anchors.getOrDefault(faction.id(), List.of())
                    .equals(knownAnchors.getOrDefault(faction.id(), List.of()))
                    || vulnerabilityChanged || mask != 0) {
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
        knownAnchors.clear();
        knownAnchors.putAll(anchors);
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
            TerritoryClaim territoryClaim = TerraFactions.territories().claimAt(
                    new TerritoryKey(claim.dimension(), claim.x(), claim.z()));
            AnchorVulnerabilityState state = territoryClaim == null ? AnchorVulnerabilityState.PROTECTED
                    : TerraFactions.territories().vulnerabilityState(territoryClaim);
            claimsByGroup.computeIfAbsent(new OverlayGroup(dimension, visualType, state),
                    ignored -> new ArrayList<>()).add(claim);
        }

        Set<String> ids = overlayIds.computeIfAbsent(faction.id(), ignored -> new HashSet<>());
        for (Map.Entry<OverlayGroup, List<ClaimSnapshot>> entry : claimsByGroup.entrySet()) {
            OverlayGroup group = entry.getKey();
            String id = overlayId(faction.id(), group.dimension(), group.type(), group.state());
            ids.add(id);
            overlayApi.show(player, TerraFactions.MOD_ID,
                    new ServerPolygon(id, group.dimension(), ClaimPolygonMerger.merge(entry.getValue()),
                            shapeProperties(faction, group.type(), group.state(), flashBright)));
        }
        showCapital(player, faction, ids);
        showAnchors(player, faction, ids);
    }

    private void showAnchors(ServerPlayer player, FactionSnapshot faction, Set<String> ids) {
        List<AnchorMapSnapshot> anchors = currentAnchors().getOrDefault(faction.id(), List.of());
        showAnchorConnections(player, faction, anchors, ids);
        for (AnchorMapSnapshot anchor : anchors) {
            ResourceLocation dimensionId = ResourceLocation.tryParse(anchor.dimension());
            if (dimensionId == null) continue;
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
            String baseId = faction.id() + "/anchor/" + Integer.toUnsignedString(anchor.id().hashCode());
            String iconId = baseId + "/icon";
            ids.add(iconId);
            overlayApi.show(player, TerraFactions.MOD_ID,
                    new ServerPolygon(iconId, dimension, List.of(anchorIcon(anchor)),
                            anchorIconProperties(faction, anchor)));

        }
    }

    private void showAnchorConnections(ServerPlayer player, FactionSnapshot faction,
                                       List<AnchorMapSnapshot> anchors, Set<String> ids) {
        for (int firstIndex = 0; firstIndex < anchors.size(); firstIndex++) {
            AnchorMapSnapshot first = anchors.get(firstIndex);
            ResourceLocation dimensionId = ResourceLocation.tryParse(first.dimension());
            if (dimensionId == null) continue;
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
            for (int secondIndex = firstIndex + 1; secondIndex < anchors.size(); secondIndex++) {
                AnchorMapSnapshot second = anchors.get(secondIndex);
                LinkType linkType = AnchorNetworkRules.linkType(first, second);
                if (linkType == LinkType.NONE) continue;

                String id = faction.id() + "/anchor-link/"
                        + Integer.toUnsignedString(first.id().hashCode()) + "/"
                        + Integer.toUnsignedString(second.id().hashCode());
                List<OverlayPolygon> shapes = new ArrayList<>();
                shapes.add(connectionLine(first, second));
                if (linkType == LinkType.ARROW_TO_FIRST) {
                    shapes.add(connectionArrow(second, first));
                } else if (linkType == LinkType.ARROW_TO_SECOND) {
                    shapes.add(connectionArrow(first, second));
                }
                ids.add(id);
                overlayApi.show(player, TerraFactions.MOD_ID,
                        new ServerPolygon(id, dimension, shapes,
                                anchorConnectionProperties(faction, first, second)));
            }
        }
    }

    private static OverlayPolygon connectionLine(AnchorMapSnapshot first, AnchorMapSnapshot second) {
        double dx = second.x() - first.x();
        double dz = second.z() - first.z();
        double length = Math.max(1.0D, Math.sqrt(dx * dx + dz * dz));
        // Server-side JourneyMap overlays only expose closed polygons. A one-block strip is the
        // narrowest valid polygon and renders as a line once its outline is disabled.
        double offsetX = -dz / length;
        double offsetZ = dx / length;
        return polygon(List.of(
                point(first.x(), first.z()),
                point(second.x(), second.z()),
                point(second.x() + offsetX, second.z() + offsetZ),
                point(first.x() + offsetX, first.z() + offsetZ)));
    }

    private static OverlayPolygon connectionArrow(AnchorMapSnapshot source, AnchorMapSnapshot target) {
        double dx = target.x() - source.x();
        double dz = target.z() - source.z();
        double length = Math.max(1.0D, Math.sqrt(dx * dx + dz * dz));
        double unitX = dx / length;
        double unitZ = dz / length;
        double perpendicularX = -unitZ;
        double perpendicularZ = unitX;
        double middleX = (source.x() + target.x()) / 2.0D;
        double middleZ = (source.z() + target.z()) / 2.0D;
        double tipX = middleX + unitX * 5.0D;
        double tipZ = middleZ + unitZ * 5.0D;
        double baseX = middleX - unitX * 5.0D;
        double baseZ = middleZ - unitZ * 5.0D;
        return polygon(List.of(
                point(tipX, tipZ),
                point(baseX + perpendicularX * 4.0D, baseZ + perpendicularZ * 4.0D),
                point(baseX - perpendicularX * 4.0D, baseZ - perpendicularZ * 4.0D)));
    }

    private static OverlayPolygon anchorIcon(AnchorMapSnapshot anchor) {
        int x = anchor.x();
        int z = anchor.z();
        int size = 5;
        return polygon(List.of(point(x, z - size), point(x + size, z), point(x, z + size), point(x - size, z)));
    }

    private static OverlayPolygon polygon(List<Long> points) {
        return new OverlayPolygon(new OverlayPoints(points), List.of());
    }

    private static long point(double x, double z) {
        return new net.minecraft.core.BlockPos((int) Math.round(x), 64, (int) Math.round(z)).asLong();
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
                                                     AnchorVulnerabilityState state, boolean flashBright) {
        String description = faction.description();
        String title = description == null || description.isBlank()
                ? faction.name()
                : faction.name() + " - " + description;

        float normalFill = type != TerritoryType.BORDER ? 0.38f : 0.18f;
        float normalStrokeWidth = type != TerritoryType.BORDER ? 2.0f : 1.0f;
        float normalStrokeOpacity = type != TerritoryType.BORDER ? 0.95f : 0.60f;
        boolean vulnerable = state == AnchorVulnerabilityState.VULNERABLE;
        boolean isolated = state == AnchorVulnerabilityState.GRACE_PERIOD;
        return new OverlayShapeProps(
                isolated ? 0xFFAA00 : faction.color(),
                vulnerable ? (flashBright ? normalFill : normalFill * 0.30f)
                        : isolated ? normalFill * 0.65f : normalFill,
                vulnerable ? 0xFF3030 : isolated ? 0xFFAA00 : faction.color(),
                vulnerable ? normalStrokeWidth + 1.5f : isolated ? normalStrokeWidth + 0.5f : normalStrokeWidth,
                vulnerable ? (flashBright ? 1.0f : 0.25f) : isolated ? 0.85f : normalStrokeOpacity,
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
            TerritoryClaim territoryClaim = TerraFactions.territories().claimAt(
                    new TerritoryKey(claim.dimension(), claim.x(), claim.z()));
            if (territoryClaim != null && TerraFactions.territories().isVulnerable(territoryClaim)) {
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

    @SuppressWarnings("deprecation")
    private static OverlayShapeProps anchorIconProperties(FactionSnapshot faction, AnchorMapSnapshot anchor) {
        int color = anchor.vulnerabilityState() == AnchorVulnerabilityState.VULNERABLE ? 0xFF3030
                : anchor.vulnerabilityState() == AnchorVulnerabilityState.GRACE_PERIOD ? 0xFFAA00
                : faction.color();
        return new OverlayShapeProps(color, 0.90f, 0xFFFFFF, 1.5f, 0.95f, 1003,
                UIState.FULLSCREEN_ZOOM_MIN, UIState.ZOOM_IN_MAX,
                EnumSet.allOf(Context.UI.class), EnumSet.allOf(Context.MapType.class), "◆",
                anchor.tier().displayName() + " Faction Anchor — " + faction.name()
                        + " — " + anchor.connectionState().name() + " / " + anchor.vulnerabilityState().name()
                        + " — " + anchor.allocatedPower() + " allocated, "
                        + formatPower(anchor.usablePowerTenths()) + " usable");
    }

    @SuppressWarnings("deprecation")
    private static OverlayShapeProps anchorConnectionProperties(FactionSnapshot faction,
                                                                 AnchorMapSnapshot first,
                                                                 AnchorMapSnapshot second) {
        AnchorVulnerabilityState state = first.vulnerabilityState() == AnchorVulnerabilityState.VULNERABLE
                || second.vulnerabilityState() == AnchorVulnerabilityState.VULNERABLE
                ? AnchorVulnerabilityState.VULNERABLE
                : first.vulnerabilityState() == AnchorVulnerabilityState.GRACE_PERIOD
                || second.vulnerabilityState() == AnchorVulnerabilityState.GRACE_PERIOD
                ? AnchorVulnerabilityState.GRACE_PERIOD : AnchorVulnerabilityState.PROTECTED;
        int color = state == AnchorVulnerabilityState.VULNERABLE ? 0xFF3030
                : state == AnchorVulnerabilityState.GRACE_PERIOD ? 0xFFAA00 : faction.color();
        return new OverlayShapeProps(color, 0.68f, color, 0.0f, 0.0f, 1002,
                UIState.FULLSCREEN_ZOOM_MIN, UIState.ZOOM_IN_MAX,
                EnumSet.allOf(Context.UI.class), EnumSet.allOf(Context.MapType.class),
                null, null);
    }

    private static String formatPower(int tenths) {
        return tenths % 10 == 0 ? Integer.toString(tenths / 10)
                : (tenths / 10) + "." + Math.abs(tenths % 10);
    }

    private List<FactionSnapshot> currentFactions() {
        return TerraFactions.territories().factions().allFactions();
    }

    private Map<UUID, List<AnchorMapSnapshot>> currentAnchors() {
        Map<UUID, List<AnchorMapSnapshot>> result = new HashMap<>();
        for (AnchorMapSnapshot anchor : TerraFactions.territories().factions().allAnchors()) {
            result.computeIfAbsent(anchor.factionId(), ignored -> new ArrayList<>()).add(anchor);
        }
        result.values().forEach(values -> values.sort(java.util.Comparator.comparing(AnchorMapSnapshot::id)));
        return result;
    }

    private static String overlayId(UUID factionId, ResourceKey<Level> dimension, TerritoryType type,
                                    AnchorVulnerabilityState state) {
        return factionId + "/" + dimension.location() + "/" + type.name().toLowerCase()
                + "/" + state.name().toLowerCase();
    }

    private record OverlayGroup(ResourceKey<Level> dimension, TerritoryType type,
                                AnchorVulnerabilityState state) {
    }
}
