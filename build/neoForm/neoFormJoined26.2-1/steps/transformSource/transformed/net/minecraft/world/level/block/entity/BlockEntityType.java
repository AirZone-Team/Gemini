package net.minecraft.world.level.block.entity;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class BlockEntityType<T extends BlockEntity> {
    private final BlockEntityType.BlockEntitySupplier<? extends T> factory;
    /// Neo: This field will be modified by BlockEntityTypeAddBlocksEvent event. Please use the event to add to this field for vanilla or other mod's BlockEntityTypes.
    private final Set<Block> validBlocks;
    private final Holder.Reference<BlockEntityType<?>> builtInRegistryHolder = BuiltInRegistries.BLOCK_ENTITY_TYPE.createIntrusiveHolder(this);
    /// Neo: Allow modded BE types to declare that they have NBT data only OPs can set
    private final boolean onlyOpCanSetNbt;

    public BlockEntityType(BlockEntityType.BlockEntitySupplier<? extends T> factory, Set<Block> validBlocks) {
        this(factory, validBlocks, false);
    }

    public BlockEntityType(BlockEntityType.BlockEntitySupplier<? extends T> factory, Set<Block> validBlocks, boolean onlyOpCanSetNbt) {
        this.factory = factory;
        this.validBlocks = validBlocks;
        this.onlyOpCanSetNbt = onlyOpCanSetNbt;
    }

    // Neo: Additional constructor for convenience.
    public BlockEntityType(BlockEntityType.BlockEntitySupplier<? extends T> factory, Block... validBlocks) {
        this(factory, false, validBlocks);
    }

    // Neo: Additional constructor for convenience.
    public BlockEntityType(BlockEntityType.BlockEntitySupplier<? extends T> factory, boolean onlyOpCanSetNbt, Block... validBlocks) {
        this(factory, Set.of(validBlocks), onlyOpCanSetNbt);
        if (validBlocks.length == 0) {
            throw new IllegalArgumentException("Block entity type instantiated without valid blocks. If this is intentional, pass Set.of() instead of an empty varag.");
        }
    }

    public T create(BlockPos worldPosition, BlockState blockState) {
        return (T)this.factory.create(worldPosition, blockState);
    }

    /**
     * Neo: Add getter for an immutable view of the set of valid blocks.
     */
    public Set<Block> getValidBlocks() {
        return java.util.Collections.unmodifiableSet(this.validBlocks);
    }

    public boolean isValid(BlockState state) {
        return this.validBlocks.contains(state.getBlock());
    }

    @Deprecated
    public Holder.Reference<BlockEntityType<?>> builtInRegistryHolder() {
        return this.builtInRegistryHolder;
    }

    public @Nullable T getBlockEntity(BlockGetter level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        return (T)(entity != null && entity.getType() == this ? entity : null);
    }

    public boolean onlyOpCanSetNbt() {
        if (onlyOpCanSetNbt) return true;
        return BlockEntityTypes.OP_ONLY_CUSTOM_DATA.contains(this);
    }

    @FunctionalInterface
    public interface BlockEntitySupplier<T extends BlockEntity> {
        T create(BlockPos worldPosition, BlockState blockState);
    }
}
