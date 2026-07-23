package net.minecraft.world.level;

import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributeReader;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import net.neoforged.neoforge.common.extensions.ILevelReaderExtension;

public interface LevelReader extends BlockAndLightGetter, CollisionGetter, SignalGetter, BiomeManager.NoiseBiomeSource, ILevelReaderExtension {
    @Nullable ChunkAccess getChunk(final int chunkX, final int chunkZ, final ChunkStatus targetStatus, final boolean loadOrGenerate);

    @Deprecated
    boolean hasChunk(int chunkX, int chunkZ);

    int getHeight(Heightmap.Types type, int x, int z);

    default int getHeight(Heightmap.Types type, BlockPos pos) {
        return this.getHeight(type, pos.getX(), pos.getZ());
    }

    int getSkyDarken();

    BiomeManager getBiomeManager();

    default Holder<Biome> getBiome(BlockPos pos) {
        return this.getBiomeManager().getBiome(pos);
    }

    default Stream<BlockState> getBlockStatesIfLoaded(AABB box) {
        int x0 = Mth.floor(box.minX);
        int x1 = Mth.floor(box.maxX);
        int y0 = Mth.floor(box.minY);
        int y1 = Mth.floor(box.maxY);
        int z0 = Mth.floor(box.minZ);
        int z1 = Mth.floor(box.maxZ);
        return this.hasChunksAt(x0, y0, z0, x1, y1, z1) ? this.getBlockStates(box) : Stream.empty();
    }

    @Override
    default Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ) {
        ChunkAccess chunk = this.getChunk(QuartPos.toSection(quartX), QuartPos.toSection(quartZ), ChunkStatus.BIOMES, false);
        return chunk != null ? chunk.getNoiseBiome(quartX, quartY, quartZ) : this.getUncachedNoiseBiome(quartX, quartY, quartZ);
    }

    Holder<Biome> getUncachedNoiseBiome(int quartX, int quartY, int quartZ);

    boolean isClientSide();

    int getSeaLevel();

    DimensionType dimensionType();

    @Override
    default int getMinY() {
        return this.dimensionType().minY();
    }

    @Override
    default int getHeight() {
        return this.dimensionType().height();
    }

    default BlockPos getHeightmapPos(Heightmap.Types type, BlockPos pos) {
        return new BlockPos(pos.getX(), this.getHeight(type, pos.getX(), pos.getZ()), pos.getZ());
    }

    default boolean isEmptyBlock(BlockPos pos) {
        return this.getBlockState(pos).isAir();
    }

    default boolean canSeeSkyFromBelowWater(BlockPos pos) {
        if (pos.getY() >= this.getSeaLevel()) {
            return this.canSeeSky(pos);
        }

        BlockPos scanPoint = new BlockPos(pos.getX(), this.getSeaLevel(), pos.getZ());
        if (!this.canSeeSky(scanPoint)) {
            return false;
        }

        for (BlockPos var4 = scanPoint.below(); var4.getY() > pos.getY(); var4 = var4.below()) {
            BlockState state = this.getBlockState(var4);
            if (state.getLightDampening() > 0 && !state.liquid()) {
                return false;
            }
        }

        return true;
    }

    default float getPathfindingCostFromLightLevels(BlockPos pos) {
        return this.getLightLevelDependentMagicValue(pos) - 0.5F;
    }

    @Deprecated
    default float getLightLevelDependentMagicValue(BlockPos pos) {
        float v = this.getMaxLocalRawBrightness(pos) / 15.0F;
        float curvedV = v / (4.0F - 3.0F * v);
        return Mth.lerp(this.dimensionType().ambientLight(), curvedV, 1.0F);
    }

    default ChunkAccess getChunk(BlockPos pos) {
        return this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    default ChunkAccess getChunk(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
    }

    default ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus status) {
        return this.getChunk(chunkX, chunkZ, status, true);
    }

    @Override
    default @Nullable BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ, ChunkStatus.EMPTY, false);
    }

    default boolean isWaterAt(BlockPos pos) {
        return this.getFluidState(pos).is(FluidTags.WATER);
    }

    default boolean containsAnyLiquid(AABB box) {
        int x0 = Mth.floor(box.minX);
        int x1 = Mth.ceil(box.maxX);
        int y0 = Mth.floor(box.minY);
        int y1 = Mth.ceil(box.maxY);
        int z0 = Mth.floor(box.minZ);
        int z1 = Mth.ceil(box.maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    BlockState blockState = this.getBlockState(pos.set(x, y, z));
                    if (!blockState.getFluidState().isEmpty()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    default int getMaxLocalRawBrightness(BlockPos pos) {
        return this.getMaxLocalRawBrightness(pos, this.getSkyDarken());
    }

    default int getMaxLocalRawBrightness(BlockPos pos, int skyDarkening) {
        return pos.getX() >= -30000000 && pos.getZ() >= -30000000 && pos.getX() < 30000000 && pos.getZ() < 30000000
            ? this.getRawBrightness(pos, skyDarkening)
            : 15;
    }

    default int getEffectiveSkyBrightness(BlockPos pos) {
        return this.getBrightness(LightLayer.SKY, pos) - this.getSkyDarken();
    }

    @Deprecated
    default boolean hasChunkAt(int blockX, int blockZ) {
        return this.hasChunk(SectionPos.blockToSectionCoord(blockX), SectionPos.blockToSectionCoord(blockZ));
    }

    @Deprecated
    default boolean hasChunkAt(BlockPos pos) {
        return this.hasChunkAt(pos.getX(), pos.getZ());
    }

    @Deprecated
    default boolean hasChunksAt(BlockPos pos0, BlockPos pos1) {
        return this.hasChunksAt(pos0.getX(), pos0.getY(), pos0.getZ(), pos1.getX(), pos1.getY(), pos1.getZ());
    }

    @Deprecated
    default boolean hasChunksAt(int x0, int y0, int z0, int x1, int y1, int z1) {
        return y1 >= this.getMinY() && y0 <= this.getMaxY() ? this.hasChunksAt(x0, z0, x1, z1) : false;
    }

    @Deprecated
    default boolean hasChunksAt(int x0, int z0, int x1, int z1) {
        int chunkX0 = SectionPos.blockToSectionCoord(x0);
        int chunkX1 = SectionPos.blockToSectionCoord(x1);
        int chunkZ0 = SectionPos.blockToSectionCoord(z0);
        int chunkZ1 = SectionPos.blockToSectionCoord(z1);

        for (int chunkX = chunkX0; chunkX <= chunkX1; chunkX++) {
            for (int chunkZ = chunkZ0; chunkZ <= chunkZ1; chunkZ++) {
                if (!this.hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }

        return true;
    }

    RegistryAccess registryAccess();

    FeatureFlagSet enabledFeatures();

    default <T> HolderLookup<T> holderLookup(ResourceKey<? extends Registry<? extends T>> key) {
        Registry<T> registry = this.registryAccess().lookupOrThrow(key);
        return registry.filterFeatures(this.enabledFeatures());
    }

    EnvironmentAttributeReader environmentAttributes();
}
