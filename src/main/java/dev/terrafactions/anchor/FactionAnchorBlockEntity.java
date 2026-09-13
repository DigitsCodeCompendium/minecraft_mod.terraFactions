package dev.terrafactions.anchor;

import dev.terrafactions.TerraFactions;
import dev.terrafactions.registry.TerraFactionsBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public final class FactionAnchorBlockEntity extends BlockEntity {
    private UUID factionId;
    private int allocatedPower;

    public FactionAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(TerraFactionsBlockEntities.FACTION_ANCHOR.get(), pos, state);
    }

    public UUID factionId() {
        return factionId;
    }

    public int allocatedPower() {
        return allocatedPower;
    }

    public AnchorTier tier() {
        return getBlockState().getBlock() instanceof FactionAnchorBlock anchor
                ? anchor.tier() : AnchorTier.BASIC;
    }

    public void assign(UUID factionId) {
        this.factionId = factionId;
        setChanged();
    }

    public void updateProjection(int allocatedPower) {
        this.allocatedPower = allocatedPower;
        setChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) TerraFactions.territories().loadAnchor(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        factionId = tag.hasUUID("faction") ? tag.getUUID("faction") : null;
        allocatedPower = Math.max(0, tag.contains("allocated_power") ? tag.getInt("allocated_power")
                : tag.contains("dedicated_power") ? tag.getInt("dedicated_power") : tag.getInt("radius"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (factionId != null) tag.putUUID("faction", factionId);
        tag.putInt("allocated_power", allocatedPower);
    }
}
