package dev.terrafactions.territory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.terrafactions.TerraFactions;
import dev.terrafactions.factions.FactionIdentity;
import dev.terrafactions.factions.FactionCommandService;
import dev.terrafactions.factions.FactionPower;
import dev.terrafactions.factions.FactionDisplay;
import dev.terrafactions.factions.FactionRelation;
import dev.terrafactions.factions.FactionRank;
import dev.terrafactions.factions.NativeFactionService;
import dev.terrafactions.factions.FactionSnapshot;
import dev.terrafactions.factions.FactionDisplayService;
import dev.terrafactions.journeymap.TerraFactionsJourneyMapPlugin;
import dev.terrafactions.network.TerritoryRadarPayload;
import dev.terrafactions.network.FactionUiPayload;
import dev.terrafactions.network.FactionActionPayload;
import dev.terrafactions.network.JourneyMapClaimPayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TerritoryService {
    private static final SimpleCommandExceptionType NOT_IN_FACTION =
            new SimpleCommandExceptionType(Component.literal("You must belong to a faction to manage territory."));
    private static final SimpleCommandExceptionType GUEST_CANNOT_MANAGE =
            new SimpleCommandExceptionType(Component.literal("Faction guests cannot manage territory."));

    private final NativeFactionService factions = new NativeFactionService();
    private final FactionDisplayService displays = new FactionDisplayService(this, factions);
    private final FactionCommandService factionCommands = new FactionCommandService(
            factions, displays::refreshNow, this::isVulnerable);
    private final Map<UUID, TerritoryRadarPayload> radarStates = new HashMap<>();
    private final Map<UUID, FactionUiPayload> factionUiStates = new HashMap<>();
    private MinecraftServer server;

    public void register() {
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(this::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::onRegisterCommands);
        new TerritoryProtection(this, factions).register();
        displays.register();
    }

    private void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        factions.initialize(server);
        reconcileCapitals();
    }

    private void onServerStopped(ServerStoppedEvent event) {
        radarStates.clear();
        factionUiStates.clear();
        factions.stop();
        server = null;
    }

    private void onServerTick(ServerTickEvent.Post event) {
        if (!factions.isReady()) {
            return;
        }
        int tick = event.getServer().getTickCount();
        if (tick % TerraFactionsConfig.POWER_REGEN_INTERVAL_TICKS.get() == 0) {
            factions.regeneratePower();
        }
        if (tick % 20 == 0) {
            radarStates.keySet().removeIf(playerId ->
                    event.getServer().getPlayerList().getPlayer(playerId) == null);
            factionUiStates.keySet().removeIf(playerId ->
                    event.getServer().getPlayerList().getPlayer(playerId) == null);
            reconcileCapitals();
        }
    }

    private void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            onFactionMovement(player);
        }
    }

    private void onLivingDeath(LivingDeathEvent event) {
        if (!factions.isReady() || !(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        if (!(victim.getKillCredit() instanceof ServerPlayer killer)
                || killer.getUUID().equals(victim.getUUID())) {
            return;
        }
        FactionIdentity identity = factions.factionForPlayer(victim.getUUID());
        if (identity != null) {
            factions.recordDeath(identity.id(), victim.getUUID(), TerraFactionsConfig.DEATH_POWER_PENALTY.get());
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        factionCommands.register(event.getDispatcher(), "terrafactions");
        factionCommands.register(event.getDispatcher(), "tf");
        factionCommands.register(event.getDispatcher(), "factions");
        registerCommands(event.getDispatcher(), "terrafactions");
        registerCommands(event.getDispatcher(), "tf");

        // Brigadier merges this territory subtree into the native faction root.
        event.getDispatcher().register(Commands.literal("factions")
                .then(Commands.literal("claim")
                        .executes(context -> claim(context.getSource(), TerritoryType.CORE))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 7))
                                .executes(context -> bulkClaim(context.getSource(), TerritoryType.CORE,
                                        IntegerArgumentType.getInteger(context, "radius"))))
                        .then(Commands.literal("add")
                                .executes(context -> claim(context.getSource(), TerritoryType.CORE))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 7))
                                        .executes(context -> bulkClaim(context.getSource(), TerritoryType.CORE,
                                                IntegerArgumentType.getInteger(context, "radius")))))
                        .then(Commands.literal("remove")
                                .executes(context -> unclaim(context.getSource()))
                                .then(Commands.argument("size", IntegerArgumentType.integer(1, 7))
                                        .executes(context -> unsupportedBulk(context.getSource())))
                                .then(Commands.literal("all").executes(context -> unclaimAll(context.getSource()))))
                        .then(Commands.literal("auto").executes(context -> unsupportedAutoClaim(context.getSource())))
                        .then(Commands.literal("core")
                                .executes(context -> claim(context.getSource(), TerritoryType.CORE))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 7))
                                        .executes(context -> bulkClaim(context.getSource(), TerritoryType.CORE,
                                                IntegerArgumentType.getInteger(context, "radius")))))
                        .then(Commands.literal("border")
                                .executes(context -> claim(context.getSource(), TerritoryType.BORDER))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 7))
                                        .executes(context -> bulkClaim(context.getSource(), TerritoryType.BORDER,
                                                IntegerArgumentType.getInteger(context, "radius"))))))
                .then(Commands.literal("unclaim").executes(context -> unclaim(context.getSource())))
                .then(Commands.literal("liberate").executes(context -> liberate(context.getSource())))
                .then(Commands.literal("convert")
                        .then(Commands.literal("core").executes(context -> claim(context.getSource(), TerritoryType.CORE)))
                        .then(Commands.literal("border").executes(context -> claim(context.getSource(), TerritoryType.BORDER))))
                .then(Commands.literal("capital")
                        .then(Commands.literal("set").executes(context -> setCapital(context.getSource()))))
                .then(buildOverlayCommands())
                .then(buildTerritoryCommands("territory")));
    }

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, String root) {
        dispatcher.register(Commands.literal(root).then(buildTerritoryCommands("claim"))
                .then(Commands.literal("unclaim").executes(context -> unclaim(context.getSource())))
                .then(Commands.literal("liberate").executes(context -> liberate(context.getSource())))
                .then(Commands.literal("convert")
                        .then(Commands.literal("core").executes(context -> claim(context.getSource(), TerritoryType.CORE)))
                        .then(Commands.literal("border").executes(context -> claim(context.getSource(), TerritoryType.BORDER))))
                .then(Commands.literal("capital")
                        .then(Commands.literal("set").executes(context -> setCapital(context.getSource()))))
                .then(buildOverlayCommands()));
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildTerritoryCommands(String name) {
        return Commands.literal(name)
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 7))
                        .executes(context -> bulkClaim(context.getSource(), TerritoryType.CORE,
                                IntegerArgumentType.getInteger(context, "radius"))))
                .then(Commands.literal("core")
                        .executes(context -> claim(context.getSource(), TerritoryType.CORE))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 7))
                                .executes(context -> bulkClaim(context.getSource(), TerritoryType.CORE,
                                        IntegerArgumentType.getInteger(context, "radius")))))
                .then(Commands.literal("border")
                        .executes(context -> claim(context.getSource(), TerritoryType.BORDER))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 7))
                                .executes(context -> bulkClaim(context.getSource(), TerritoryType.BORDER,
                                        IntegerArgumentType.getInteger(context, "radius")))))
                .then(Commands.literal("unclaim").executes(context -> unclaim(context.getSource())))
                .then(Commands.literal("liberate").executes(context -> liberate(context.getSource())))
                .then(Commands.literal("convert")
                        .then(Commands.literal("core").executes(context -> claim(context.getSource(), TerritoryType.CORE)))
                        .then(Commands.literal("border").executes(context -> claim(context.getSource(), TerritoryType.BORDER))))
                .then(Commands.literal("capital")
                        .then(Commands.literal("set").executes(context -> setCapital(context.getSource()))))
                .then(buildOverlayCommands());
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildOverlayCommands() {
        return Commands.literal("overlay")
                .then(Commands.literal("on").executes(context -> setOverlay(context.getSource(), true)))
                .then(Commands.literal("off").executes(context -> setOverlay(context.getSource(), false)));
    }

    public NativeFactionService factions() {
        return factions;
    }

    /** Handles native dashboard requests directly on the server thread. */
    public void handleUiAction(ServerPlayer player, FactionActionPayload payload) {
        CommandSourceStack source = player.createCommandSourceStack().withSuppressedOutput();
        try {
            switch (payload.action()) {
                case CLAIM_CORE -> claim(source, TerritoryType.CORE);
                case CLAIM_BORDER -> claim(source, TerritoryType.BORDER);
                case UNCLAIM -> unclaim(source);
                case SET_CAPITAL -> setCapital(source);
                case SET_OVERLAY -> setOverlay(source, payload.enabled());
                default -> factionCommands.handleUiAction(player, payload);
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
        }
        syncClientState(player, true);
    }

    /** Handles a chunk selected through JourneyMap, with all authority enforced server-side. */
    public void handleJourneyMapClaim(ServerPlayer player, JourneyMapClaimPayload payload) {
        CommandSourceStack source = player.createCommandSourceStack();
        TerritoryKey playerChunk = currentChunk(player);
        TerritoryKey target = TerritoryKey.of(payload.dimension(), payload.chunkX(), payload.chunkZ());
        int radius = TerraFactionsConfig.JOURNEYMAP_CLAIM_RADIUS.get();
        if (!target.dimension().equals(playerChunk.dimension())) {
            fail(source, "JourneyMap claims must be in your current dimension.");
            return;
        }
        long deltaX = Math.abs((long) target.x() - playerChunk.x());
        long deltaZ = Math.abs((long) target.z() - playerChunk.z());
        if (Math.max(deltaX, deltaZ) > radius) {
            fail(source, "That chunk is outside the JourneyMap claim radius of " + radius + " chunks.");
            return;
        }
        try {
            Actor actor = actor(source);
            switch (payload.action()) {
                case CLAIM_CORE -> claim(source, actor, TerritoryType.CORE, target);
                case CLAIM_BORDER -> claim(source, actor, TerritoryType.BORDER, target);
                case UNCLAIM -> unclaim(source, actor, target);
                case LIBERATE -> liberate(source, actor, target);
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
        }
        syncClientState(player, true);
    }

    public TerritoryClaim claimAt(TerritoryKey key) {
        return factions.isReady() ? factions.claim(key) : null;
    }

    private void onFactionMovement(ServerPlayer player) {
        if (!factions.isReady() || player.getServer() == null) {
            return;
        }
        // Refresh twice per second. This keeps movement responsive without rebuilding faction summaries every tick.
        if (player.getServer().getTickCount() % 10 != 0) return;

        syncClientState(player, false);
    }

    private void syncClientState(ServerPlayer player, boolean force) {
        TerritoryClaim claim = claimAt(currentChunk(player));
        TerritoryRadarPayload payload = createHudPayload(player, claim);
        TerritoryRadarPayload previous = radarStates.put(player.getUUID(), payload);
        if (force || !payload.equals(previous)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
        FactionUiPayload uiPayload = createFactionUiPayload(player);
        FactionUiPayload previousUi = factionUiStates.put(player.getUUID(), uiPayload);
        if (force || !uiPayload.equals(previousUi)) {
            PacketDistributor.sendToPlayer(player, uiPayload);
        }
    }

    private FactionUiPayload createFactionUiPayload(ServerPlayer player) {
        FactionIdentity identity = factions.factionForPlayer(player.getUUID());
        List<FactionUiPayload.FactionEntry> factionEntries = factions.allFactions().stream()
                .filter(faction -> identity == null || !faction.id().equals(identity.id()))
                .map(faction -> new FactionUiPayload.FactionEntry(
                        faction.name(), factions.tag(faction.id()), faction.color(),
                        factions.members(faction.id()).size(),
                        factions.relation(identity == null ? null : identity.id(), faction.id()).ordinal(),
                        factions.declaredRelation(identity == null ? null : identity.id(), faction.id()).ordinal(),
                        factions.declaredRelation(faction.id(), identity == null ? null : identity.id()).ordinal()))
                .toList();
        if (identity == null) {
            return new FactionUiPayload("", "", "", 0xAAAAAA, -1,
                    0, 0, 0, 0, TerraFactionsConfig.BASE_POWER.get(),
                    TerraFactionsConfig.POWER_PER_MEMBER.get(), TerraFactionsConfig.CORE_CLAIM_COST.get(),
                    TerraFactionsConfig.BORDER_CLAIM_COST.get(), 0, 0, 0, "", false, false,
                    factions.radarEnabled(player.getUUID()), factions.chatMode(player.getUUID()).ordinal(),
                    List.of(), List.of(), factionEntries);
        }

        FactionSnapshot faction = factions.snapshot(identity.id());
        FactionPower power = factions.power(identity.id());
        Set<UUID> memberIds = factions.members(identity.id());
        List<FactionUiPayload.MemberEntry> members = memberIds.stream()
                .map(memberId -> {
                    ServerPlayer onlinePlayer = player.getServer().getPlayerList().getPlayer(memberId);
                    String memberName = playerName(player.getServer(), memberId);
                    FactionIdentity member = factions.factionForPlayer(memberId);
                    return new FactionUiPayload.MemberEntry(memberName,
                            member == null ? FactionRank.MEMBER.ordinal() : member.rank().ordinal(),
                            onlinePlayer != null, power.deathLossByPlayer().getOrDefault(memberId, 0));
                })
                .sorted(Comparator.comparingInt(FactionUiPayload.MemberEntry::rankOrdinal)
                        .thenComparing(FactionUiPayload.MemberEntry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<FactionUiPayload.LossEntry> losses = power.deathLossByPlayer().entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> new FactionUiPayload.LossEntry(
                        playerName(player.getServer(), entry.getKey()), entry.getValue(),
                        memberIds.contains(entry.getKey())))
                .sorted(Comparator.comparingInt(FactionUiPayload.LossEntry::amount).reversed()
                        .thenComparing(FactionUiPayload.LossEntry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int capitalClaims = (int) faction.claims().stream().filter(claim -> claim.type() == TerritoryType.CAPITAL).count();
        int coreClaims = (int) faction.claims().stream().filter(claim -> claim.type() == TerritoryType.CORE).count();
        int borderClaims = (int) faction.claims().stream().filter(claim -> claim.type() == TerritoryType.BORDER).count();
        String capital = faction.capital() == null ? "" : faction.capital().x() + ", " + faction.capital().z()
                + " (" + faction.capital().dimension() + ")";
        return new FactionUiPayload(faction.name(), faction.description(), factions.tag(identity.id()),
                faction.color(), identity.rank().ordinal(), power.current(), power.maximum(), power.claimUsage(),
                power.deathLoss(), TerraFactionsConfig.BASE_POWER.get(), TerraFactionsConfig.POWER_PER_MEMBER.get(),
                TerraFactionsConfig.CORE_CLAIM_COST.get(), TerraFactionsConfig.BORDER_CLAIM_COST.get(),
                capitalClaims, coreClaims, borderClaims, capital,
                isVulnerable(identity.id(), TerritoryType.CORE),
                isVulnerable(identity.id(), TerritoryType.BORDER),
                factions.radarEnabled(player.getUUID()), factions.chatMode(player.getUUID()).ordinal(),
                members, losses, factionEntries);
    }

    private static String playerName(MinecraftServer server, UUID playerId) {
        ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(playerId);
        return onlinePlayer != null
                ? onlinePlayer.getGameProfile().getName()
                : server.getProfileCache().get(playerId)
                        .map(profile -> profile.getName()).orElse(playerId.toString());
    }

    private TerritoryRadarPayload createHudPayload(ServerPlayer player, TerritoryClaim claim) {
        FactionIdentity viewer = factions.factionForPlayer(player.getUUID());
        String territoryName = "";
        String territoryFaction = "";
        int relationColor = 0xAAAAAA;
        boolean vulnerable = false;
        FactionPower ownPower = viewer == null ? null : factions.power(viewer.id());
        boolean borderVulnerable = viewer != null && isVulnerable(viewer.id(), TerritoryType.BORDER);
        boolean coreVulnerable = viewer != null && isVulnerable(viewer.id(), TerritoryType.CORE);

        if (factions.radarEnabled(player.getUUID())) {
            if (claim == null) {
                territoryName = "Wilderness";
                territoryFaction = "Unclaimed territory";
            } else {
                FactionDisplay owner = factions.factionDisplay(claim.factionId());
                territoryName = switch (claim.type()) {
                    case CAPITAL -> "Capital";
                    case CORE -> "Core";
                    case BORDER -> "Border";
                };
                territoryFaction = owner == null ? "Unknown Faction" : owner.name();
                relationColor = relationColor(viewer, claim.factionId());
                vulnerable = isVulnerable(claim);
            }
        }

        return new TerritoryRadarPayload(territoryName, territoryFaction, relationColor, vulnerable,
                ownPower != null,
                viewer == null ? -1 : viewer.rank().ordinal(),
                ownPower == null ? 0 : ownPower.current(),
                ownPower == null ? 0 : ownPower.maximum(),
                borderVulnerable, coreVulnerable);
    }

    private int relationColor(FactionIdentity viewer, UUID territoryFactionId) {
        if (viewer != null && viewer.id().equals(territoryFactionId)) return 0x5555FF;
        FactionRelation relation = factions.relation(viewer == null ? null : viewer.id(), territoryFactionId);
        return switch (relation) {
            case ENEMY -> 0xFF5555;
            case ALLIED -> 0x55FF55;
            case NEUTRAL -> 0xAAAAAA;
        };
    }

    public String factionTag(UUID factionId) {
        if (!factions.isReady()) {
            return null;
        }
        return factions.tag(factionId);
    }

    private int claim(CommandSourceStack source, TerritoryType type) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Actor actor = actor(source);
        return claim(source, actor, type, currentChunk(actor.player()));
    }

    private int claim(CommandSourceStack source, Actor actor, TerritoryType type, TerritoryKey key) {
        TerritoryClaim existing = claimAt(key);
        if (existing != null && !existing.factionId().equals(actor.factionId())) {
            return captureEnemyClaim(source, actor, type, key);
        }
        if (existing != null && existing.type() == type) {
            return fail(source, "That chunk is already " + type.name().toLowerCase() + " territory.");
        }
        if (existing != null && existing.type() == TerritoryType.CAPITAL) {
            return fail(source, "Move your capital before changing its claim type.");
        }

        TerritoryType resultType = hasClaims(actor.factionId()) ? type : TerritoryType.CAPITAL;

        Set<TerritoryKey> territory = territory(actor.factionId(), key.dimension());
        if (existing == null && TerraFactionsConfig.REQUIRE_SIDE_CONNECTIVITY.get()
                && !territory.isEmpty() && !TerritoryRules.touches(territory, key)) {
            return fail(source, "New territory must share a side with your faction's existing territory.");
        }
        if (!hasCapacity(actor.factionId(), resultType, existing)) {
            return fail(source, "Your faction does not have enough available power for that claim.");
        }

        replace(existing, actor.factionId(), resultType, key);
        ensureCapital(actor.factionId());
        return success(source, "Claimed " + key.x() + ", " + key.z() + " as "
                + resultType.name().toLowerCase() + ".");
    }

    private int unclaim(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Actor actor = actor(source);
        return unclaim(source, actor, currentChunk(actor.player()));
    }

    private int unclaim(CommandSourceStack source, Actor actor, TerritoryKey key) {
        TerritoryClaim existing = claimAt(key);
        if (existing == null || !existing.factionId().equals(actor.factionId())) {
            return fail(source, "Your faction does not own this chunk.");
        }
        if (existing.type() == TerritoryType.CAPITAL) {
            return fail(source, "Move your capital before unclaiming this chunk.");
        }
        Set<TerritoryKey> territory = territory(actor.factionId(), key.dimension());
        if (TerraFactionsConfig.REQUIRE_SIDE_CONNECTIVITY.get() && !TerritoryRules.remainsConnected(territory, key)) {
            return fail(source, "Unclaiming this chunk would split your faction's territory.");
        }
        remove(existing);
        ensureCapital(actor.factionId());
        return success(source, "Unclaimed " + key.x() + ", " + key.z() + ".");
    }

    private int unclaimAll(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Actor actor = actor(source);
        int removed = factions.removeAllClaims(actor.factionId());
        factions.clearCapital(actor.factionId());
        return success(source, "Removed all " + removed + " claims.");
    }

    private int bulkClaim(CommandSourceStack source, TerritoryType requestedType, int radius)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Actor actor = actor(source);
        TerritoryKey center = currentChunk(actor.player());
        Set<TerritoryKey> square = TerritoryRules.centeredSquare(center, radius);
        Set<TerritoryKey> ownedInDimension = territory(actor.factionId(), center.dimension());
        List<TerritoryKey> additions = square.stream().filter(key -> !ownedInDimension.contains(key)).toList();
        for (TerritoryKey key : additions) {
            TerritoryClaim existing = claimAt(key);
            if (existing != null) {
                return fail(source, "Bulk claim blocked by another faction at " + key.x() + ", " + key.z() + ".");
            }
        }
        if (additions.isEmpty()) {
            return fail(source, "Your faction already owns every chunk in that area.");
        }
        Set<TerritoryKey> resulting = new HashSet<>(ownedInDimension);
        resulting.addAll(additions);
        if (TerraFactionsConfig.REQUIRE_SIDE_CONNECTIVITY.get() && !TerritoryRules.isConnected(resulting)) {
            return fail(source, "The bulk claim must connect to your faction's existing territory.");
        }

        boolean firstClaim = !hasClaims(actor.factionId());
        long addedCost = (long) additions.size() * requestedType.cost();
        if (firstClaim) {
            addedCost += TerritoryType.CAPITAL.cost() - requestedType.cost();
        }
        FactionPower power = factions.power(actor.factionId());
        long grossPower = power == null ? Long.MIN_VALUE : (long) power.current() + power.claimUsage();
        if (power == null || requiredPower(actor.factionId()) + addedCost > grossPower) {
            return fail(source, "Your faction does not have enough available power for " + additions.size() + " claims.");
        }

        List<TerritoryKey> added = new java.util.ArrayList<>();
        try {
            if (firstClaim) {
                add(actor.factionId(), TerritoryType.CAPITAL, center);
                added.add(center);
            }
            for (TerritoryKey key : additions) {
                if (firstClaim && key.equals(center)) continue;
                add(actor.factionId(), requestedType, key);
                added.add(key);
            }
        } catch (RuntimeException exception) {
            added.forEach(factions::removeClaim);
            throw exception;
        }
        ensureCapital(actor.factionId());
        return success(source, "Claimed " + added.size() + " chunks as " + requestedType.name().toLowerCase()
                + " (radius " + radius + ", " + (radius * 2 + 1) + "x" + (radius * 2 + 1) + ").");
    }

    private static int unsupportedBulk(CommandSourceStack source) {
        return fail(source, "Bulk unclaim is not supported; remove claims individually or use /factions claim remove all.");
    }

    private static int unsupportedAutoClaim(CommandSourceStack source) {
        return fail(source, "Autoclaim is disabled. Use /factions claim core or /factions claim border.");
    }

    private int captureEnemyClaim(CommandSourceStack source, Actor actor, TerritoryType resultType, TerritoryKey key) {
        TerritoryClaim target = claimAt(key);
        if (target == null || target.factionId().equals(actor.factionId())) {
            return fail(source, "Stand inside vulnerable enemy territory to claim it.");
        }
        if (!factions.isEnemy(target.factionId(), actor.factionId())) {
            return fail(source, "Only an enemy faction's territory can be attacked.");
        }
        if (!isVulnerable(target)) {
            return fail(source, "This " + target.type().name().toLowerCase() + " claim is not vulnerable.");
        }
        if (!TerritoryRules.touches(territory(actor.factionId(), key.dimension()), key)) {
            return fail(source, "A captured claim must share a side with your faction's territory.");
        }
        if (!hasCapacity(actor.factionId(), resultType, null)) {
            return fail(source, "Your faction does not have enough power capacity for that claim type.");
        }
        UUID defenderId = target.factionId();
        replace(target, actor.factionId(), resultType, key);
        ensureCapital(defenderId);
        ensureCapital(actor.factionId());
        return success(source, "Overclaimed the chunk as " + resultType.name().toLowerCase() + " territory.");
    }

    private int liberate(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Actor actor = actor(source);
        return liberate(source, actor, currentChunk(actor.player()));
    }

    private int liberate(CommandSourceStack source, Actor actor, TerritoryKey key) {
        TerritoryClaim target = claimAt(key);
        if (target == null || target.factionId().equals(actor.factionId())) {
            return fail(source, "Stand inside vulnerable enemy territory to liberate it.");
        }
        if (!factions.isEnemy(target.factionId(), actor.factionId())) {
            return fail(source, "Only an enemy faction's territory can be attacked.");
        }
        if (!isVulnerable(target)) {
            return fail(source, "This " + target.type().name().toLowerCase() + " claim is not vulnerable.");
        }
        remove(target);
        ensureCapital(target.factionId());
        return success(source, "Liberated " + key.x() + ", " + key.z() + "; it is now wilderness.");
    }

    private int setCapital(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Actor actor = actor(source);
        if (!actor.rank().isLeadership()) {
            return fail(source, "Only faction leadership can move the capital.");
        }
        TerritoryKey key = currentChunk(actor.player());
        TerritoryClaim claim = claimAt(key);
        if (claim == null || !claim.factionId().equals(actor.factionId())) {
            return fail(source, "The capital must be placed in territory your faction owns.");
        }
        if (claim.type() == TerritoryType.BORDER) {
            return fail(source, "The capital can only be moved to a core claim.");
        }
        if (claim.type() == TerritoryType.CAPITAL) {
            return fail(source, "This chunk is already your faction capital.");
        }
        TerritoryKey oldKey = factions.capital(actor.factionId());
        TerritoryClaim oldClaim = oldKey == null ? null : claimAt(oldKey);
        if (oldClaim != null && oldClaim.factionId().equals(actor.factionId())) {
            replace(oldClaim, actor.factionId(), TerritoryType.CORE, oldKey);
        }
        replace(claim, actor.factionId(), TerritoryType.CAPITAL, key);
        factions.setCapital(actor.factionId(), key);
        return success(source, "Moved your faction capital to " + key.x() + ", " + key.z() + ".");
    }

    private int setOverlay(CommandSourceStack source, boolean enabled)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!ModList.get().isLoaded("journeymap")
                || !TerraFactionsJourneyMapPlugin.setOverlayEnabled(player, enabled)) {
            return fail(source, "The JourneyMap overlay service is not available.");
        }
        source.sendSuccess(() -> Component.literal("Faction overlays " + (enabled ? "enabled." : "disabled.")), false);
        return 1;
    }

    public boolean isVulnerable(TerritoryClaim claim) {
        return isVulnerable(claim.factionId(), claim.type());
    }

    public boolean isVulnerable(UUID factionId, TerritoryType type) {
        FactionPower power = factions.power(factionId);
        if (power == null || power.current() <= 0) {
            return true;
        }
        if (type != TerritoryType.BORDER || power.maximum() <= 0) {
            return false;
        }
        return (double) power.current() / power.maximum() <= TerraFactionsConfig.BORDER_VULNERABILITY_PERCENT.get();
    }

    private boolean hasCapacity(UUID factionId, TerritoryType addedType, TerritoryClaim replacedOwnClaim) {
        long required = requiredPower(factionId);
        if (replacedOwnClaim != null) {
            required -= replacedOwnClaim.type().cost();
        }
        required += addedType.cost();
        FactionPower power = factions.power(factionId);
        long grossPower = power == null ? Long.MIN_VALUE : (long) power.current() + power.claimUsage();
        return power != null && required <= grossPower;
    }

    private long requiredPower(UUID factionId) {
        long total = 0;
        for (TerritoryClaim claim : allTerritory()) {
            if (claim.factionId().equals(factionId)) {
                total += claim.type().cost();
            }
        }
        return total;
    }

    private boolean hasClaims(UUID factionId) {
        return allTerritory().stream().anyMatch(claim -> claim.factionId().equals(factionId));
    }

    private Set<TerritoryKey> territory(UUID factionId, String dimension) {
        Set<TerritoryKey> result = new HashSet<>();
        for (TerritoryClaim claim : allTerritory()) {
            if (claim.factionId().equals(factionId) && claim.key().dimension().equals(dimension)) {
                result.add(claim.key());
            }
        }
        return result;
    }

    private Collection<TerritoryClaim> allTerritory() {
        return factions.allClaims();
    }

    private void reconcileCapitals() {
        for (FactionSnapshot faction : factions.allFactions()) {
            ensureCapital(faction.id());
        }
    }

    private void ensureCapital(UUID factionId) {
        TerritoryKey current = factions.capital(factionId);
        List<TerritoryClaim> owned = allTerritory().stream()
                .filter(claim -> claim.factionId().equals(factionId))
                .sorted(Comparator.comparing((TerritoryClaim claim) -> claim.key().dimension())
                        .thenComparingInt(claim -> claim.key().x())
                        .thenComparingInt(claim -> claim.key().z()))
                .toList();
        if (owned.isEmpty()) {
            factions.clearCapital(factionId);
            return;
        }

        TerritoryClaim chosen = current == null ? null : owned.stream()
                .filter(claim -> claim.key().equals(current) && claim.type() != TerritoryType.BORDER)
                .findFirst().orElse(null);
        if (chosen == null) chosen = owned.stream().filter(claim -> claim.type() == TerritoryType.CAPITAL)
                .findFirst().orElse(null);
        if (chosen == null) chosen = owned.stream().filter(claim -> claim.type() == TerritoryType.CORE)
                .findFirst().orElse(owned.getFirst());

        TerritoryClaim capital = chosen;
        for (TerritoryClaim claim : owned) {
            TerritoryType desired = claim.key().equals(capital.key()) ? TerritoryType.CAPITAL
                    : claim.type() == TerritoryType.CAPITAL ? TerritoryType.CORE : claim.type();
            if (claim.type() != desired) replace(claim, factionId, desired, claim.key());
        }
        factions.setCapital(factionId, capital.key());
    }

    private void replace(TerritoryClaim oldClaim, UUID newOwner, TerritoryType newType, TerritoryKey key) {
        if (oldClaim != null) {
            remove(oldClaim);
        }
        try {
            add(newOwner, newType, key);
        } catch (RuntimeException exception) {
            if (oldClaim != null) {
                try {
                    add(oldClaim.factionId(), oldClaim.type(), oldClaim.key());
                } catch (RuntimeException rollbackException) {
                    exception.addSuppressed(rollbackException);
                    TerraFactions.LOGGER.error("Could not roll back failed territory transfer at {} {}, {}",
                            key.dimension(), key.x(), key.z(), rollbackException);
                }
            }
            throw exception;
        }
    }

    private void add(UUID owner, TerritoryType type, TerritoryKey key) {
        factions.putClaim(key, owner, type);
    }

    private void remove(TerritoryClaim claim) {
        factions.removeClaim(claim.key());
    }

    private Actor actor(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionIdentity identity = factions.factionForPlayer(player.getUUID());
        if (identity == null) {
            throw NOT_IN_FACTION.create();
        }
        if (identity.rank() == FactionRank.GUEST) {
            throw GUEST_CANNOT_MANAGE.create();
        }
        return new Actor(player, identity.id(), identity.rank());
    }

    private static TerritoryKey currentChunk(ServerPlayer player) {
        ChunkPos chunk = player.chunkPosition();
        return TerritoryKey.of(player.level().dimension().location(), chunk.x, chunk.z);
    }

    private static int success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private record Actor(ServerPlayer player, UUID factionId, FactionRank rank) {
    }

}
