package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class NetherRootsBlock extends VegetationBlock {
    public static final MapCodec<NetherRootsBlock> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(TagKey.codec(Registries.BLOCK).fieldOf("support_blocks").forGetter(b -> b.supportBlocks), propertiesCodec())
            .apply(i, NetherRootsBlock::new)
    );
    private static final VoxelShape SHAPE = Block.column(12.0, 0.0, 13.0);
    private final TagKey<Block> supportBlocks;

    @Override
    public MapCodec<NetherRootsBlock> codec() {
        return CODEC;
    }

    public NetherRootsBlock(TagKey<Block> supportBlocks, BlockBehaviour.Properties properties) {
        super(properties);
        this.supportBlocks = supportBlocks;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(this.supportBlocks);
    }
}
