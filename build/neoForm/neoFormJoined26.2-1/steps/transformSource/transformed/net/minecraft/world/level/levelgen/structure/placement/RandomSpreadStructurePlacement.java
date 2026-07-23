package net.minecraft.world.level.levelgen.structure.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;

public class RandomSpreadStructurePlacement extends StructurePlacement {
    public static final MapCodec<RandomSpreadStructurePlacement> CODEC = RecordCodecBuilder.<RandomSpreadStructurePlacement>mapCodec(
            i -> placementCodec(i)
                .and(
                    i.group(
                        Codec.intRange(0, 4096).fieldOf("spacing").forGetter(RandomSpreadStructurePlacement::spacing),
                        Codec.intRange(0, 4096).fieldOf("separation").forGetter(RandomSpreadStructurePlacement::separation),
                        RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.LINEAR).forGetter(RandomSpreadStructurePlacement::spreadType)
                    )
                )
                .apply(i, RandomSpreadStructurePlacement::new)
        )
        .validate(RandomSpreadStructurePlacement::validate);
    private final int spacing;
    private final int separation;
    private final RandomSpreadType spreadType;

    private static DataResult<RandomSpreadStructurePlacement> validate(RandomSpreadStructurePlacement c) {
        return c.spacing <= c.separation ? DataResult.error(() -> "Spacing has to be larger than separation") : DataResult.success(c);
    }

    public RandomSpreadStructurePlacement(
        Vec3i locateOffset,
        StructurePlacement.FrequencyReductionMethod frequencyReductionMethod,
        float frequency,
        int salt,
        Optional<StructurePlacement.ExclusionZone> exclusionZone,
        int spacing,
        int separation,
        RandomSpreadType spreadType
    ) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone);
        this.spacing = spacing;
        this.separation = separation;
        this.spreadType = spreadType;
    }

    public RandomSpreadStructurePlacement(int spacing, int separation, RandomSpreadType spreadType, int salt) {
        this(Vec3i.ZERO, StructurePlacement.FrequencyReductionMethod.DEFAULT, 1.0F, salt, Optional.empty(), spacing, separation, spreadType);
    }

    public int spacing() {
        return this.spacing;
    }

    public int separation() {
        return this.separation;
    }

    public RandomSpreadType spreadType() {
        return this.spreadType;
    }

    public ChunkPos getPotentialStructureChunk(long seed, int sourceX, int sourceZ) {
        int spacedGridX = Math.floorDiv(sourceX, this.spacing);
        int spacedGridZ = Math.floorDiv(sourceZ, this.spacing);
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureWithSalt(seed, spacedGridX, spacedGridZ, this.salt());
        int limit = this.spacing - this.separation;
        int spreadX = this.spreadType.evaluate(random, limit);
        int spreadZ = this.spreadType.evaluate(random, limit);
        return new ChunkPos(spacedGridX * this.spacing + spreadX, spacedGridZ * this.spacing + spreadZ);
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int sourceX, int sourceZ) {
        ChunkPos chunkPos = this.getPotentialStructureChunk(state.getLevelSeed(), sourceX, sourceZ);
        return chunkPos.x() == sourceX && chunkPos.z() == sourceZ;
    }

    @Override
    public StructurePlacementType<?> type() {
        return StructurePlacementType.RANDOM_SPREAD;
    }
}
