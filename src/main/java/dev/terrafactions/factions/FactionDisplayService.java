package dev.terrafactions.factions;

import dev.terrafactions.territory.TerritoryService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Relation-aware faction tags in global chat and overhead player names. */
public final class FactionDisplayService {
    private final TerritoryService territories;
    private final NativeFactionService factions;
    private final Map<ViewerTarget, DisplayState> sentNameTags = new HashMap<>();
    private boolean refreshRequested;

    public FactionDisplayService(TerritoryService territories, NativeFactionService factions) {
        this.territories = territories;
        this.factions = factions;
    }

    public void register() {
        NeoForge.EVENT_BUS.addListener(this::onChat);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
    }

    public void refreshNow() {
        refreshRequested = true;
    }

    private void onChat(ServerChatEvent event) {
        FactionIdentity senderFaction = factions.factionForPlayer(event.getPlayer().getUUID());
        UUID senderFactionId = senderFaction == null ? null : senderFaction.id();
        String tag = senderFaction == null ? FactionTags.FACTIONLESS : territories.factionTag(senderFactionId);

        event.setCanceled(true);
        MinecraftServer server = event.getPlayer().getServer();
        if (server == null) {
            return;
        }
        FactionChatMode senderMode = factions.chatMode(event.getPlayer().getUUID());
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (receivesMessage(viewer, senderFactionId, senderMode)) {
                viewer.sendSystemMessage(chatLine(viewer, event.getPlayer(), senderFactionId, tag,
                        senderMode, event.getMessage()));
            }
        }
    }

    private boolean receivesMessage(ServerPlayer viewer, UUID senderFactionId, FactionChatMode senderMode) {
        if (senderMode == FactionChatMode.GLOBAL) {
            return factions.chatMode(viewer.getUUID()) != FactionChatMode.FOCUS;
        }
        FactionIdentity viewerFaction = factions.factionForPlayer(viewer.getUUID());
        return senderFactionId != null && viewerFaction != null && viewerFaction.id().equals(senderFactionId);
    }

    private Component chatLine(ServerPlayer viewer, ServerPlayer sender, UUID senderFactionId, String tag,
                               FactionChatMode mode, Component message) {
        MutableComponent line = Component.empty();
        if (mode != FactionChatMode.GLOBAL) {
            line.append(Component.literal("[Faction] ").withStyle(ChatFormatting.AQUA));
        }
        line.append(tagPrefix(tag, colorFor(viewer, senderFactionId)));
        return line.append(sender.getDisplayName()).append(Component.literal(": ")).append(message);
    }

    private void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!refreshRequested && server.getTickCount() % 20 != 0) {
            return;
        }
        refreshRequested = false;
        updateNameTags(server, server.getTickCount() % 100 == 0);
    }

    private void updateNameTags(MinecraftServer server, boolean reassert) {
        Map<ViewerTarget, DisplayState> current = new HashMap<>();
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            for (ServerPlayer target : server.getPlayerList().getPlayers()) {
                FactionIdentity targetFaction = factions.factionForPlayer(target.getUUID());
                UUID targetFactionId = targetFaction == null ? null : targetFaction.id();
                String tag = targetFaction == null ? FactionTags.FACTIONLESS : territories.factionTag(targetFactionId);
                ViewerTarget key = new ViewerTarget(viewer.getUUID(), target.getUUID());
                if (tag == null) {
                    removeIfSent(viewer, target, key, true);
                    continue;
                }
                ChatFormatting color = colorFor(viewer, targetFactionId);
                DisplayState state = new DisplayState(tag, color, baseTeamSignature(target));
                current.put(key, state);
                if (reassert || !state.equals(sentNameTags.get(key))) {
                    removeIfSent(viewer, target, key, false);
                    sendNameTag(viewer, target, tag, color);
                }
            }
        }
        sentNameTags.keySet().removeIf(key -> !current.containsKey(key));
        sentNameTags.putAll(current);
    }

    private void removeIfSent(ServerPlayer viewer, ServerPlayer target, ViewerTarget key, boolean restoreBaseTeam) {
        if (sentNameTags.containsKey(key)) {
            viewer.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(
                    virtualTeam(target, "", ChatFormatting.GRAY)));
            sentNameTags.remove(key);
            PlayerTeam base = target.getTeam();
            if (restoreBaseTeam && base != null) {
                viewer.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(base,
                        target.getGameProfile().getName(), ClientboundSetPlayerTeamPacket.Action.ADD));
            }
        }
    }

    private void sendNameTag(ServerPlayer viewer, ServerPlayer target, String tag, ChatFormatting color) {
        PlayerTeam team = virtualTeam(target, tag, color);
        viewer.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
    }

    private static PlayerTeam virtualTeam(ServerPlayer target, String tag, ChatFormatting tagColor) {
        Scoreboard scoreboard = new Scoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam(teamName(target.getUUID()));
        PlayerTeam base = target.getTeam();
        if (base != null) {
            team.setDisplayName(base.getDisplayName());
            team.setPlayerPrefix(base.getPlayerPrefix());
            team.setPlayerSuffix(base.getPlayerSuffix());
            team.setColor(base.getColor());
            team.setAllowFriendlyFire(base.isAllowFriendlyFire());
            team.setSeeFriendlyInvisibles(base.canSeeFriendlyInvisibles());
            team.setNameTagVisibility(base.getNameTagVisibility());
            team.setDeathMessageVisibility(base.getDeathMessageVisibility());
            team.setCollisionRule(base.getCollisionRule());
        }
        if (!tag.isEmpty()) {
            team.setPlayerPrefix(tagPrefix(tag, tagColor).append(team.getPlayerPrefix()));
        }
        scoreboard.addPlayerToTeam(target.getGameProfile().getName(), team);
        return team;
    }

    private ChatFormatting colorFor(ServerPlayer viewer, UUID otherFactionId) {
        FactionIdentity viewerFaction = factions.factionForPlayer(viewer.getUUID());
        if (viewerFaction != null && viewerFaction.id().equals(otherFactionId)) {
            return ChatFormatting.BLUE;
        }
        FactionRelation relation = factions.relation(viewerFaction == null ? null : viewerFaction.id(), otherFactionId);
        return switch (relation) {
            case ALLIED -> ChatFormatting.GREEN;
            case ENEMY -> ChatFormatting.RED;
            case NEUTRAL -> ChatFormatting.GRAY;
        };
    }

    private static MutableComponent tagPrefix(String tag, ChatFormatting color) {
        return Component.empty().append(Component.literal("[" + tag + "] ").withStyle(color));
    }

    private static String teamName(UUID playerId) {
        return "tf" + playerId.toString().replace("-", "").substring(0, 14);
    }

    private static int baseTeamSignature(Player target) {
        PlayerTeam team = target.getTeam();
        return team == null ? 0 : Objects.hash(team.getName(), team.getPlayerPrefix(), team.getPlayerSuffix(),
                team.getColor(), team.isAllowFriendlyFire(), team.canSeeFriendlyInvisibles(),
                team.getNameTagVisibility(), team.getDeathMessageVisibility(), team.getCollisionRule());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        sentNameTags.clear();
        refreshRequested = false;
    }

    private record ViewerTarget(UUID viewerId, UUID targetId) {
    }

    private record DisplayState(String tag, ChatFormatting color, int baseTeamSignature) {
    }
}
