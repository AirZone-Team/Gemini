package net.minecraft.world.level.chunk;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.SavedTick;
import net.minecraft.world.ticks.TickContainerAccess;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import net.neoforged.neoforge.attachment.IAttachmentHolder;

public abstract class ChunkAccess implements LightChunk, StructureAccess, BiomeManager.NoiseBiomeSource, IAttachmentHolder {
    public static final int NO_FILLED_SECTION = -1;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final LongSet EMPTY_REFERENCE_SET = new LongOpenHashSet();
    protected final @Nullable ShortList[] postProcessing;
    private volatile boolean unsaved;
    private volatile boolean isLightCorrect;
    protected final ChunkPos chunkPos;
    private long inhabitedTime;
    @Deprecated
    private @Nullable BiomeGenerationSettings carverBiomeSettings;
    protected @Nullable NoiseChunk noiseChunk;
    protected final UpgradeData upgradeData;
    protected final @Nullable BlendingData blendingData;
    protected final Map<Heightmap.Types, Heightmap> heightmaps = Maps.newEnumMap(Heightmap.Types.class);
    protected ChunkSkyLightSources skyLightSources;
    private final Map<Structure, StructureStart> structureStarts = Maps.newHashMap();
    private final Map<Structure, LongSet> structuresRefences = Maps.newHashMap();
    protected final Map<BlockPos, CompoundTag> pendingBlockEntities = Maps.newHashMap();
    protected final Map<BlockPos, BlockEntity> blockEntities = new Object2ObjectOpenHashMap<>();
    protected final LevelHeightAccessor levelHeightAccessor;
    protected final LevelChunkSection[] sections;

    public ChunkAccess(
        ChunkPos chunkPos,
        UpgradeData upgradeData,
        LevelHeightAccessor levelHeightAccessor,
        PalettedContainerFactory containerFactory,
        long inhabitedTime,
        LevelChunkSection @Nullable [] sections,
        @Nullable BlendingData blendingData
    ) {
        this.chunkPos = chunkPos;
        this.upgradeData = upgradeData;
        this.levelHeightAccessor = levelHeightAccessor;
        this.sections = new LevelChunkSection[levelHeightAccessor.getSectionsCount()];
        this.inhabitedTime = inhabitedTime;
        this.postProcessing = new ShortList[levelHeightAccessor.getSectionsCount()];
        this.blendingData = blendingData;
        this.skyLightSources = new ChunkSkyLightSources(levelHeightAccessor);
        if (sections != null) {
            if (this.sections.length == sections.length) {
                System.arraycopy(sections, 0, this.sections, 0, this.sections.length);
            } else {
                LOGGER.warn("Could not set level chunk sections, array length is {} instead of {}", sections.length, this.sections.length);
            }
        }

        replaceMissingSections(containerFactory, this.sections);
    }

    private static void replaceMissingSections(PalettedContainerFactory containerFactory, LevelChunkSection[] sections) {
        for (int i = 0; i < sections.length; i++) {
            if (sections[i] == null) {
                sections[i] = new LevelChunkSection(containerFactory);
            }
        }
    }

    public GameEventListenerRegistry getListenerRegistry(int section) {
        return GameEventListenerRegistry.NOOP;
    }

    public @Nullable BlockState setBlockState(BlockPos pos, BlockState state) {
        return this.setBlockState(pos, state, 3);
    }

    public abstract @Nullable BlockState setBlockState(BlockPos pos, BlockState state, @Block.UpdateFlags int flags);

    public abstract void setBlockEntity(BlockEntity blockEntity);

    public abstract void addEntity(Entity entity);

    public int getHighestFilledSectionIndex() {
        LevelChunkSection[] sections = this.getSections();

        for (int sectionIndex = sections.length - 1; sectionIndex >= 0; sectionIndex--) {
            LevelChunkSection section = sections[sectionIndex];
            if (!section.hasOnlyAir()) {
                return sectionIndex;
            }
        }

        return -1;
    }

    @Deprecated(forRemoval = true)
    public int getHighestSectionPosition() {
        int sectionIndex = this.getHighestFilledSectionIndex();
        return sectionIndex == -1 ? this.getMinY() : SectionPos.sectionToBlockCoord(this.getSectionYFromSectionIndex(sectionIndex));
    }

    public Set<BlockPos> getBlockEntitiesPos() {
        Set<BlockPos> result = Sets.newHashSet(this.pendingBlockEntities.keySet());
        result.addAll(this.blockEntities.keySet());
        return result;
    }

    public LevelChunkSection[] getSections() {
        return this.sections;
    }

    public LevelChunkSection getSection(int sectionIndex) {
        return this.getSections()[sectionIndex];
    }

    public Collection<Entry<Heightmap.Types, Heightmap>> getHeightmaps() {
        return Collections.unmodifiableSet(this.heightmaps.entrySet());
    }

    public void setHeightmap(Heightmap.Types key, long[] data) {
        this.getOrCreateHeightmapUnprimed(key).setRawData(this, key, data);
    }

    public Heightmap getOrCreateHeightmapUnprimed(Heightmap.Types type) {
        return this.heightmaps.computeIfAbsent(type, k -> new Heightmap(this, k));
    }

    public boolean hasPrimedHeightmap(Heightmap.Types type) {
        return this.heightmaps.get(type) != null;
    }

    public int getHeight(Heightmap.Types type, int x, int z) {
        Heightmap heightmap = this.heightmaps.get(type);
        if (heightmap == null) {
            if (SharedConstants.IS_RUNNING_IN_IDE && this instanceof LevelChunk) {
                LOGGER.error("Unprimed heightmap: {} {} {}", type, x, z);
            }

            Heightmap.primeHeightmaps(this, EnumSet.of(type));
            heightmap = this.heightmaps.get(type);
        }

        return heightmap.getFirstAvailable(x & 15, z & 15) - 1;
    }

    public ChunkPos getPos() {
        return this.chunkPos;
    }

    @Override
    public @Nullable StructureStart getStartForStructure(Structure structure) {
        return this.structureStarts.get(structure);
    }

    @Override
    public void setStartForStructure(Structure structure, StructureStart structureStart) {
        this.structureStarts.put(structure, structureStart);
        this.markUnsaved();
    }

    public Map<Structure, StructureStart> getAllStarts() {
        return Collections.unmodifiableMap(this.structureStarts);
    }

    public void setAllStarts(Map<Structure, StructureStart> starts) {
        this.structureStarts.clear();
        this.structureStarts.putAll(starts);
        this.markUnsaved();
    }

    @Override
    public LongSet getReferencesForStructure(Structure structure) {
        return this.structuresRefences.getOrDefault(structure, EMPTY_REFERENCE_SET);
    }

    @Override
    public void addReferenceForStructure(Structure structure, long reference) {
        this.structuresRefences.computeIfAbsent(structure, k -> new LongOpenHashSet()).add(reference);
        this.markUnsaved();
    }

    @Override
    public Map<Structure, LongSet> getAllReferences() {
        return Collections.unmodifiableMap(this.structuresRefences);
    }

    @Override
    public void setAllReferences(Map<Structure, LongSet> data) {
        this.structuresRefences.clear();
        this.structuresRefences.putAll(data);
        this.markUnsaved();
    }

    public boolean isYSpaceEmpty(int yStartInclusive, int yEndInclusive) {
        if (yStartInclusive < this.getMinY()) {
            yStartInclusive = this.getMinY();
        }

        if (yEndInclusive > this.getMaxY()) {
            yEndInclusive = this.getMaxY();
        }

        for (int y = yStartInclusive; y <= yEndInclusive; y += 16) {
            if (!this.getSection(this.getSectionIndex(y)).hasOnlyAir()) {
                return false;
            }
        }

        return true;
    }

    public void markUnsaved() {
        this.unsaved = true;
    }

    public boolean tryMarkSaved() {
        if (this.unsaved) {
            this.unsaved = false;
            return true;
        } else {
            return false;
        }
    }

    public boolean isUnsaved() {
        return this.unsaved;
    }

    public abstract ChunkStatus getPersistedStatus();

    public ChunkStatus getHighestGeneratedStatus() {
        ChunkStatus status = this.getPersistedStatus();
        BelowZeroRetrogen belowZeroRetrogen = this.getBelowZeroRetrogen();
        if (belowZeroRetrogen != null) {
            ChunkStatus targetStatus = belowZeroRetrogen.targetStatus();
            return ChunkStatus.max(targetStatus, status);
        } else {
            return status;
        }
    }

    public abstract void removeBlockEntity(BlockPos pos);

    public void markPosForPostProcessing(BlockPos blockPos) {
        LOGGER.warn("Trying to mark a block for post processing @ {}, but this operation is not supported.", blockPos);
    }

    public @Nullable ShortList[] getPostProcessing() {
        return this.postProcessing;
    }

    public void addPackedPostProcess(ShortList packedOffsets, int sectionIndex) {
        getOrCreateOffsetList(this.getPostProcessing(), sectionIndex).addAll(packedOffsets);
    }

    public void setBlockEntityNbt(CompoundTag entityTag) {
        BlockPos posFromTag = BlockEntity.getPosFromTag(this.chunkPos, entityTag);
        if (!this.blockEntities.containsKey(posFromTag)) {
            this.pendingBlockEntities.put(posFromTag, entityTag);
        }
    }

    public @Nullable CompoundTag getBlockEntityNbt(BlockPos blockPos) {
        return this.pendingBlockEntities.get(blockPos);
    }

    public abstract @Nullable CompoundTag getBlockEntityNbtForSaving(BlockPos blockPos, HolderLookup.Provider registryAccess);

    @Override
    public final void findBlockLightSources(BiConsumer<BlockPos, BlockState> consumer) {
        this.findBlocks(p_284897_ -> p_284897_.hasDynamicLightEmission() || p_284897_.getLightEmission(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO) != 0, (p_284897_, pos) -> p_284897_.getLightEmission(this, pos) != 0, consumer);
    }

    public void findBlocks(Predicate<BlockState> predicate, BiConsumer<BlockPos, BlockState> consumer) {
        findBlocks(predicate, (state, pos) -> predicate.test(state), consumer);
    }

    public void findBlocks(Predicate<BlockState> predicate, java.util.function.BiPredicate<BlockState, BlockPos> fineFilter, BiConsumer<BlockPos, BlockState> consumer) {
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int sectionY = this.getMinSectionY(); sectionY <= this.getMaxSectionY(); sectionY++) {
            LevelChunkSection section = this.getSection(this.getSectionIndexFromSectionY(sectionY));
            if (section.maybeHas(predicate)) {
                BlockPos origin = SectionPos.of(this.chunkPos, sectionY).origin();

                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            BlockState state = section.getBlockState(x, y, z);
                            mutablePos.setWithOffset(origin, x, y, z);
                            if (fineFilter.test(state, mutablePos)) {
                                consumer.accept(mutablePos, state);
                            }
                        }
                    }
                }
            }
        }
    }

    public abstract TickContainerAccess<Block> getBlockTicks();

    public abstract TickContainerAccess<Fluid> getFluidTicks();

    public void collectBiomesInPalette(Set<Holder<Biome>> output) {
        for (LevelChunkSection section : this.sections) {
            section.getBiomes().forEachInPalette(output::add);
        }
    }

    public boolean canBeSerialized() {
        return true;
    }

    public abstract ChunkAccess.PackedTicks getTicksForSerialization(long currentTick);

    public UpgradeData getUpgradeData() {
        return this.upgradeData;
    }

    public boolean isOldNoiseGeneration() {
        return this.blendingData != null;
    }

    public @Nullable BlendingData getBlendingData() {
        return this.blendingData;
    }

    public long getInhabitedTime() {
        return this.inhabitedTime;
    }

    public void incrementInhabitedTime(long inhabitedTimeDelta) {
        this.inhabitedTime += inhabitedTimeDelta;
    }

    public void setInhabitedTime(long inhabitedTime) {
        this.inhabitedTime = inhabitedTime;
    }

    public static ShortList getOrCreateOffsetList(@Nullable ShortList[] list, int sectionIndex) {
        ShortList result = list[sectionIndex];
        if (result == null) {
            result = new ShortArrayList();
            list[sectionIndex] = result;
        }

        return result;
    }

    public boolean isLightCorrect() {
        return this.isLightCorrect;
    }

    public void setLightCorrect(boolean isLightCorrect) {
        this.isLightCorrect = isLightCorrect;
        this.markUnsaved();
    }

    @Override
    public int getMinY() {
        return this.levelHeightAccessor.getMinY();
    }

    @Override
    public int getHeight() {
        return this.levelHeightAccessor.getHeight();
    }

    public NoiseChunk getOrCreateNoiseChunk(Function<ChunkAccess, NoiseChunk> factory) {
        if (this.noiseChunk == null) {
            this.noiseChunk = factory.apply(this);
        }

        return this.noiseChunk;
    }

    @Deprecated
    public BiomeGenerationSettings carverBiome(Supplier<BiomeGenerationSettings> source) {
        if (this.carverBiomeSettings == null) {
            this.carverBiomeSettings = source.get();
        }

        return this.carverBiomeSettings;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ) {
        try {
            int quartMinY = QuartPos.fromBlock(this.getMinY());
            int quartMaxY = quartMinY + QuartPos.fromBlock(this.getHeight()) - 1;
            int clampedQuartY = Mth.clamp(quartY, quartMinY, quartMaxY);
            int sectionIndex = this.getSectionIndex(QuartPos.toBlock(clampedQuartY));
            return this.sections[sectionIndex].getNoiseBiome(quartX & 3, clampedQuartY & 3, quartZ & 3);
        } catch (Throwable t) {
            CrashReport report = CrashReport.forThrowable(t, "Getting biome");
            CrashReportCategory category = report.addCategory("Biome being got");
            category.setDetail("Location", () -> CrashReportCategory.formatLocation(this, quartX, quartY, quartZ));
            throw new ReportedException(report);
        }
    }

    public void fillBiomesFromNoise(BiomeResolver biomeResolver, Climate.Sampler sampler) {
        ChunkPos pos = this.getPos();
        int quartMinX = QuartPos.fromBlock(pos.getMinBlockX());
        int quartMinZ = QuartPos.fromBlock(pos.getMinBlockZ());
        LevelHeightAccessor heightAccessor = this.getHeightAccessorForGeneration();

        for (int sectionY = heightAccessor.getMinSectionY(); sectionY <= heightAccessor.getMaxSectionY(); sectionY++) {
            LevelChunkSection section = this.getSection(this.getSectionIndexFromSectionY(sectionY));
            int quartMinY = QuartPos.fromSection(sectionY);
            section.fillBiomesFromNoise(biomeResolver, sampler, quartMinX, quartMinY, quartMinZ);
        }
    }

    public boolean hasAnyStructureReferences() {
        return !this.getAllReferences().isEmpty();
    }

    public @Nullable BelowZeroRetrogen getBelowZeroRetrogen() {
        return null;
    }

    public boolean isUpgrading() {
        return this.getBelowZeroRetrogen() != null;
    }

    public LevelHeightAccessor getHeightAccessorForGeneration() {
        return this;
    }

    public void initializeLightSources() {
        this.skyLightSources.fillFrom(this);
    }

    @Override
    public ChunkSkyLightSources getSkyLightSources() {
        return this.skyLightSources;
    }

    // Neo: Hook in AttachmentHolder to chunks for data storage and retrieval
    private final net.neoforged.neoforge.attachment.AttachmentHolder.AsField attachmentHolder = new net.neoforged.neoforge.attachment.AttachmentHolder.AsField(this);

    @Override
    public boolean hasAttachments() {
        return getAttachmentHolder().hasAttachments();
    }

    @Override
    public boolean hasData(net.neoforged.neoforge.attachment.AttachmentType<?> type) {
        return getAttachmentHolder().hasData(type);
    }

    @Override
    public <T> T getData(net.neoforged.neoforge.attachment.AttachmentType<T> type) {
        return getAttachmentHolder().getData(type);
    }

    @Override
    @Nullable
    public <T> T getExistingDataOrNull(net.neoforged.neoforge.attachment.AttachmentType<T> type) {
        return getAttachmentHolder().getExistingDataOrNull(type);
    }

    @Override
    @Nullable
    public <T> T setData(net.neoforged.neoforge.attachment.AttachmentType<T> type, T data) {
        markUnsaved();
        return getAttachmentHolder().setData(type, data);
    }

    @Override
    @Nullable
    public <T> T removeData(net.neoforged.neoforge.attachment.AttachmentType<T> type) {
        markUnsaved();
        return getAttachmentHolder().removeData(type);
    }

    /**
     * <strong>FOR INTERNAL USE ONLY</strong>
     * <p>
     * Only public for use in {@link net.minecraft.world.level.chunk.storage.SerializableChunkData}.
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    @Nullable
    public final CompoundTag writeAttachmentsToNBT(HolderLookup.Provider provider) {
        ProblemReporter.Collector reporter = new ProblemReporter.Collector();
        var tag = net.minecraft.world.level.storage.TagValueOutput.createWithContext(reporter, provider);
        getAttachmentHolder().serializeAttachments(tag);
        if (!reporter.isEmpty()) throw new IllegalArgumentException("Attachments failed to serialise: " + reporter.getReport());
        return tag.isEmpty() ? null : tag.buildResult();
    }

    /**
     * <strong>FOR INTERNAL USE ONLY</strong>
     * <p>
     * Only public for use in {@link net.minecraft.world.level.chunk.storage.SerializableChunkData}.
     *
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    public final void readAttachmentsFromNBT(HolderLookup.Provider provider, CompoundTag tag) {
        ProblemReporter.Collector reporter = new ProblemReporter.Collector();
        var input = net.minecraft.world.level.storage.TagValueInput.create(reporter, provider, tag);
        getAttachmentHolder().deserializeInternal(provider, input);
        if (!reporter.isEmpty()) {
            throw new IllegalArgumentException("Attachments failed to deserialise: " + reporter.getReport());
        }
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public net.neoforged.neoforge.attachment.AttachmentHolder.AsField getAttachmentHolder() {
        return attachmentHolder;
    }

    // Neo: Allow for exposing the Level a chunk is tied to if available
    public net.minecraft.world.level.@Nullable Level getLevel() { return null; }

    public static ProblemReporter.PathElement problemPath(ChunkPos pos) {
        return new ChunkAccess.ChunkPathElement(pos);
    }

    public ProblemReporter.PathElement problemPath() {
        return problemPath(this.getPos());
    }

    private record ChunkPathElement(ChunkPos pos) implements ProblemReporter.PathElement {
        @Override
        public String get() {
            return "chunk@" + this.pos;
        }
    }

    public record PackedTicks(List<SavedTick<Block>> blocks, List<SavedTick<Fluid>> fluids) {
    }
}
