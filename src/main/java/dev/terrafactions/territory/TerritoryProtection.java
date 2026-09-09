package dev.terrafactions.territory;

import dev.terrafactions.factions.NativeFactionService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

final class TerritoryProtection {
    private final TerritoryService territories;
    private final NativeFactionService factions;

    TerritoryProtection(TerritoryService territories, NativeFactionService factions) {
        this.territories = territories;
        this.factions = factions;
    }

    void register() {
        NeoForge.EVENT_BUS.addListener(this::onBreakBlock);
        NeoForge.EVENT_BUS.addListener(this::onPlaceBlock);
        NeoForge.EVENT_BUS.addListener(this::onUseBlock);
        NeoForge.EVENT_BUS.addListener(this::onUseEntity);
        NeoForge.EVENT_BUS.addListener(this::onUseEntitySpecific);
        NeoForge.EVENT_BUS.addListener(this::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(this::onExplosion);
    }

    private void onBreakBlock(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        TerritoryClaim claim = claimAt(event.getLevel(), event.getPos().getX() >> 4, event.getPos().getZ() >> 4);
        if (claim != null && !factions.hasBlockPermission(claim.factionId(), player.getUUID())) {
            event.setCanceled(true);
            warn(player, claim.type());
        }
    }

    private void onPlaceBlock(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        TerritoryClaim claim = claimAt(event.getLevel(), event.getPos().getX() >> 4, event.getPos().getZ() >> 4);
        if (claim != null && !factions.hasBlockPermission(claim.factionId(), player.getUUID())) {
            event.setCanceled(true);
            warn(player, claim.type());
        }
    }

    private void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        cancelCoreInteraction(player, claimAt(event.getLevel(), event.getPos()), event);
    }

    private void onUseEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        cancelCoreInteraction(player, claimAt(event.getLevel(), event.getTarget().blockPosition()), event);
    }

    private void onUseEntitySpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        cancelCoreInteraction(player, claimAt(event.getLevel(), event.getTarget().blockPosition()), event);
    }

    private void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // Territory protection must never make PvP one-way. Player damage is
        // governed by normal Minecraft/friendly-fire rules in both directions.
        if (event.getTarget() instanceof ServerPlayer) return;
        TerritoryClaim claim = claimAt(player.level(), event.getTarget().blockPosition());
        if (isUnauthorizedCore(player, claim)) {
            event.setCanceled(true);
            warn(player, claim.type());
        }
    }

    private void onExplosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> claimAt(event.getLevel(), pos) != null);
        event.getAffectedEntities().removeIf(entity -> {
            TerritoryClaim claim = claimAt(event.getLevel(), entity.blockPosition());
            return !(entity instanceof ServerPlayer) && claim != null && claim.type() != TerritoryType.BORDER;
        });
    }

    private void cancelCoreInteraction(ServerPlayer player, TerritoryClaim claim,
                                       net.neoforged.bus.api.ICancellableEvent event) {
        if (isUnauthorizedCore(player, claim)) {
            event.setCanceled(true);
            warn(player, claim.type());
        }
    }

    private boolean isUnauthorizedCore(ServerPlayer player, TerritoryClaim claim) {
        return claim != null && claim.type() != TerritoryType.BORDER
                && !factions.hasBlockPermission(claim.factionId(), player.getUUID());
    }

    private TerritoryClaim claimAt(LevelAccessor level, BlockPos pos) {
        return claimAt(level, pos.getX() >> 4, pos.getZ() >> 4);
    }

    private TerritoryClaim claimAt(LevelAccessor level, int x, int z) {
        if (!(level instanceof Level actualLevel)) {
            return null;
        }
        TerritoryKey key = new TerritoryKey(actualLevel.dimension().location().toString(), x, z);
        return territories.claimAt(key);
    }

    private static void warn(Player player, TerritoryType type) {
        player.displayClientMessage(Component.literal("You cannot modify blocks in this faction's "
                + type.name().toLowerCase() + "."), true);
    }
}
