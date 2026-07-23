package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class DropExperienceBlock extends Block {
    public static final MapCodec<DropExperienceBlock> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(IntProviders.codec(0, 10).fieldOf("experience").forGetter(b -> b.xpRange), propertiesCodec()).apply(i, DropExperienceBlock::new)
    );
    private final IntProvider xpRange;

    @Override
    public MapCodec<? extends DropExperienceBlock> codec() {
        return CODEC;
    }

    public DropExperienceBlock(IntProvider xpRange, BlockBehaviour.Properties properties) {
        super(properties);
        this.xpRange = xpRange;
    }

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, tool, dropExperience);
    }

    // Neo: Patch-in override for getExpDrop. Original vanilla logic passes this.xpRange to tryDropExperience.
    @Override
    public int getExpDrop(BlockState state, net.minecraft.world.level.LevelAccessor level, BlockPos pos,
            net.minecraft.world.level.block.entity.@org.jspecify.annotations.Nullable BlockEntity blockEntity,
            net.minecraft.world.entity.@org.jspecify.annotations.Nullable Entity breaker, ItemStack tool) {
        return this.xpRange.sample(level.getRandom());
    }
}
