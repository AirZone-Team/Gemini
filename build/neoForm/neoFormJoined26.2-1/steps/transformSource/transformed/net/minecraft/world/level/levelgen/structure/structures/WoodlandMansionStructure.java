package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class WoodlandMansionStructure extends Structure {
    public static final MapCodec<WoodlandMansionStructure> CODEC = simpleCodec(WoodlandMansionStructure::new);

    public WoodlandMansionStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        Rotation rotation = Rotation.getRandom(context.random());
        BlockPos startPos = this.getLowestYIn5by5BoxOffset7Blocks(context, rotation);
        return startPos.getY() < 60
            ? Optional.empty()
            : Optional.of(new Structure.GenerationStub(startPos, builder -> this.generatePieces(builder, context, startPos, rotation)));
    }

    private void generatePieces(StructurePiecesBuilder builder, Structure.GenerationContext context, BlockPos startPos, Rotation rotation) {
        List<WoodlandMansionPieces.WoodlandMansionPiece> wmPieces = Lists.newLinkedList();
        WoodlandMansionPieces.generateMansion(context.structureTemplateManager(), startPos, rotation, wmPieces, context.random());
        wmPieces.forEach(builder::addPiece);
    }

    @Override
    public void afterPlace(
        WorldGenLevel level,
        StructureManager structureManager,
        ChunkGenerator generator,
        RandomSource random,
        BoundingBox chunkBB,
        ChunkPos chunkPos,
        PiecesContainer pieces
    ) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = level.getMinY();
        BoundingBox boundingBox = pieces.calculateBoundingBox();
        int yStart = boundingBox.minY();

        for (int x = chunkBB.minX(); x <= chunkBB.maxX(); x++) {
            for (int z = chunkBB.minZ(); z <= chunkBB.maxZ(); z++) {
                pos.set(x, yStart, z);
                if (!level.isEmptyBlock(pos) && boundingBox.isInside(pos) && pieces.isInsidePiece(pos)) {
                    for (int y = yStart - 1; y > minY; y--) {
                        pos.setY(y);
                        if (!level.isEmptyBlock(pos) && !level.getBlockState(pos).liquid()) {
                            break;
                        }

                        level.setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    @Override
    public StructureType<?> type() {
        return StructureType.WOODLAND_MANSION;
    }
}
