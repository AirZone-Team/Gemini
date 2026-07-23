package net.minecraft.world.level.block.state;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

import net.neoforged.neoforge.common.extensions.IBlockStateExtension;

public class BlockState extends BlockBehaviour.BlockStateBase implements IBlockStateExtension {
    public static final Codec<BlockState> CODEC = codec(BuiltInRegistries.BLOCK.byNameCodec(), Block::defaultBlockState, Block::getStateDefinition).stable();

    public BlockState(Block owner, Property<?>[] propertyKeys, Comparable<?>[] propertyValues) {
        super(owner, propertyKeys, propertyValues);
    }

    @Override
    protected BlockState asState() {
        return this;
    }
}
