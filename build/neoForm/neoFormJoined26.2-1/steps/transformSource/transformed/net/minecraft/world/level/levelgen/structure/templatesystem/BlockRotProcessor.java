package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

public class BlockRotProcessor implements StructureProcessor {
    public static final MapCodec<BlockRotProcessor> MAP_CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(
                RegistryCodecs.homogeneousList(Registries.BLOCK).optionalFieldOf("rottable_blocks").forGetter(t -> t.rottableBlocks),
                Codec.floatRange(0.0F, 1.0F).fieldOf("integrity").forGetter(t -> t.integrity)
            )
            .apply(i, BlockRotProcessor::new)
    );
    private final Optional<HolderSet<Block>> rottableBlocks;
    private final float integrity;

    public BlockRotProcessor(HolderSet<Block> tag, float integrity) {
        this(Optional.of(tag), integrity);
    }

    public BlockRotProcessor(float integrity) {
        this(Optional.empty(), integrity);
    }

    private BlockRotProcessor(Optional<HolderSet<Block>> blockTagKey, float integrity) {
        this.integrity = integrity;
        this.rottableBlocks = blockTagKey;
    }

    @Override
    public StructureTemplate.@Nullable StructureBlockInfo processBlock(
        LevelReader level,
        BlockPos targetPosition,
        BlockPos referencePos,
        BlockPos templateRelativePos,
        StructureTemplate.StructureBlockInfo processedBlockInfo,
        StructurePlaceSettings settings
    ) {
        RandomSource random = settings.getRandom(processedBlockInfo.pos());
        return (!this.rottableBlocks.isPresent() || processedBlockInfo.state().is(this.rottableBlocks.get())) && !(random.nextFloat() <= this.integrity)
            ? null
            : processedBlockInfo;
    }

    @Override
    public MapCodec<BlockRotProcessor> codec() {
        return MAP_CODEC;
    }
}
