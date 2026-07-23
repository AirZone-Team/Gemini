package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import org.jspecify.annotations.Nullable;

public abstract class LayerLightSectionStorage<M extends DataLayerStorageMap<M>> {
    private final LightLayer layer;
    protected final LightChunkGetter chunkSource;
    protected final Long2ByteMap sectionStates = new Long2ByteOpenHashMap();
    private final LongSet columnsWithSources = new LongOpenHashSet();
    protected volatile M visibleSectionData;
    protected final M updatingSectionData;
    protected final LongSet changedSections = new LongOpenHashSet();
    protected final LongSet sectionsAffectedByLightUpdates = new LongOpenHashSet();
    protected final Long2ObjectMap<DataLayer> queuedSections = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());
    private final LongSet columnsToRetainQueuedDataFor = new LongOpenHashSet();
    private final LongSet toRemove = new LongOpenHashSet();
    protected volatile boolean hasInconsistencies;

    protected LayerLightSectionStorage(LightLayer layer, LightChunkGetter chunkSource, M initialMap) {
        this.layer = layer;
        this.chunkSource = chunkSource;
        this.updatingSectionData = initialMap;
        this.visibleSectionData = initialMap.copy();
        this.visibleSectionData.disableCache();
        this.sectionStates.defaultReturnValue((byte)0);
    }

    protected boolean storingLightForSection(long sectionNode) {
        return this.getDataLayer(sectionNode, true) != null;
    }

    protected @Nullable DataLayer getDataLayer(long sectionNode, boolean updating) {
        return this.getDataLayer(updating ? this.updatingSectionData : this.visibleSectionData, sectionNode);
    }

    protected @Nullable DataLayer getDataLayer(M sections, long sectionNode) {
        return sections.getLayer(sectionNode);
    }

    protected @Nullable DataLayer getDataLayerToWrite(long sectionNode) {
        DataLayer dataLayer = this.updatingSectionData.getLayer(sectionNode);
        if (dataLayer == null) {
            return null;
        }

        if (this.changedSections.add(sectionNode)) {
            dataLayer = dataLayer.copy();
            this.updatingSectionData.setLayer(sectionNode, dataLayer);
            this.updatingSectionData.clearCache();
        }

        return dataLayer;
    }

    public @Nullable DataLayer getDataLayerData(long sectionNode) {
        DataLayer layer = this.queuedSections.get(sectionNode);
        return layer != null ? layer : this.getDataLayer(sectionNode, false);
    }

    protected abstract int getLightValue(final long blockNode);

    protected int getStoredLevel(long blockNode) {
        long sectionNode = SectionPos.blockToSection(blockNode);
        DataLayer layer = this.getDataLayer(sectionNode, true);
        return layer.get(
            SectionPos.sectionRelative(BlockPos.getX(blockNode)),
            SectionPos.sectionRelative(BlockPos.getY(blockNode)),
            SectionPos.sectionRelative(BlockPos.getZ(blockNode))
        );
    }

    protected void setStoredLevel(long blockNode, int level) {
        long sectionNode = SectionPos.blockToSection(blockNode);
        DataLayer layer;
        if (this.changedSections.add(sectionNode)) {
            layer = this.updatingSectionData.copyDataLayer(sectionNode);
        } else {
            layer = this.getDataLayer(sectionNode, true);
        }

        layer.set(
            SectionPos.sectionRelative(BlockPos.getX(blockNode)),
            SectionPos.sectionRelative(BlockPos.getY(blockNode)),
            SectionPos.sectionRelative(BlockPos.getZ(blockNode)),
            level
        );
        SectionPos.aroundAndAtBlockPos(blockNode, this.sectionsAffectedByLightUpdates::add);
    }

    protected void markSectionAndNeighborsAsAffected(long sectionNode) {
        int x = SectionPos.x(sectionNode);
        int y = SectionPos.y(sectionNode);
        int z = SectionPos.z(sectionNode);

        for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetY = -1; offsetY <= 1; offsetY++) {
                    this.sectionsAffectedByLightUpdates.add(SectionPos.asLong(x + offsetX, y + offsetY, z + offsetZ));
                }
            }
        }
    }

    protected DataLayer createDataLayer(long sectionNode) {
        DataLayer queuedLayer = this.queuedSections.get(sectionNode);
        return queuedLayer != null ? queuedLayer : new DataLayer();
    }

    protected boolean hasInconsistencies() {
        return this.hasInconsistencies;
    }

    protected void markNewInconsistencies(LightEngine<M, ?> engine) {
        if (this.hasInconsistencies) {
            this.hasInconsistencies = false;

            for (long node : this.toRemove) {
                DataLayer queued = this.queuedSections.remove(node);
                DataLayer stored = this.updatingSectionData.removeLayer(node);
                if (this.columnsToRetainQueuedDataFor.contains(SectionPos.getZeroNode(node))) {
                    if (queued != null) {
                        this.queuedSections.put(node, queued);
                    } else if (stored != null) {
                        this.queuedSections.put(node, stored);
                    }
                }
            }

            this.updatingSectionData.clearCache();

            for (long node : this.toRemove) {
                this.onNodeRemoved(node);
                this.changedSections.add(node);
            }

            this.toRemove.clear();
            ObjectIterator<Entry<DataLayer>> iterator = Long2ObjectMaps.fastIterator(this.queuedSections);

            while (iterator.hasNext()) {
                Entry<DataLayer> entry = iterator.next();
                long sectionNode = entry.getLongKey();
                if (this.storingLightForSection(sectionNode)) {
                    DataLayer data = entry.getValue();
                    if (this.updatingSectionData.getLayer(sectionNode) != data) {
                        this.updatingSectionData.setLayer(sectionNode, data);
                        this.changedSections.add(sectionNode);
                    }

                    iterator.remove();
                }
            }

            this.updatingSectionData.clearCache();
        }
    }

    protected void onNodeAdded(long sectionNode) {
    }

    protected void onNodeRemoved(long sectionNode) {
    }

    protected void setLightEnabled(long zeroNode, boolean enable) {
        if (enable) {
            this.columnsWithSources.add(zeroNode);
        } else {
            this.columnsWithSources.remove(zeroNode);
        }
    }

    protected boolean lightOnInSection(long sectionNode) {
        long zeroNode = SectionPos.getZeroNode(sectionNode);
        return this.columnsWithSources.contains(zeroNode);
    }

    protected boolean lightOnInColumn(long sectionZeroNode) {
        return this.columnsWithSources.contains(sectionZeroNode);
    }

    public void retainData(long zeroNode, boolean retain) {
        if (retain) {
            this.columnsToRetainQueuedDataFor.add(zeroNode);
        } else {
            this.columnsToRetainQueuedDataFor.remove(zeroNode);
        }
    }

    protected void queueSectionData(long sectionNode, @Nullable DataLayer data) {
        if (data != null) {
            this.queuedSections.put(sectionNode, data);
            this.hasInconsistencies = true;
        } else {
            this.queuedSections.remove(sectionNode);
        }
    }

    protected void updateSectionStatus(long sectionNode, boolean sectionEmpty) {
        byte state = this.sectionStates.get(sectionNode);
        byte newState = LayerLightSectionStorage.SectionState.hasData(state, !sectionEmpty);
        if (state != newState) {
            this.putSectionState(sectionNode, newState);
            int neighborIncrement = sectionEmpty ? -1 : 1;

            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetY = -1; offsetY <= 1; offsetY++) {
                    for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                        if (offsetX != 0 || offsetY != 0 || offsetZ != 0) {
                            long neighborNode = SectionPos.offset(sectionNode, offsetX, offsetY, offsetZ);
                            byte neighborState = this.sectionStates.get(neighborNode);
                            this.putSectionState(
                                neighborNode,
                                LayerLightSectionStorage.SectionState.neighborCount(
                                    neighborState, LayerLightSectionStorage.SectionState.neighborCount(neighborState) + neighborIncrement
                                )
                            );
                        }
                    }
                }
            }
        }
    }

    protected void putSectionState(long sectionNode, byte state) {
        if (state != 0) {
            if (this.sectionStates.put(sectionNode, state) == 0) {
                this.initializeSection(sectionNode);
            }
        } else if (this.sectionStates.remove(sectionNode) != 0) {
            this.removeSection(sectionNode);
        }
    }

    private void initializeSection(long sectionNode) {
        if (!this.toRemove.remove(sectionNode)) {
            this.updatingSectionData.setLayer(sectionNode, this.createDataLayer(sectionNode));
            this.changedSections.add(sectionNode);
            this.onNodeAdded(sectionNode);
            this.markSectionAndNeighborsAsAffected(sectionNode);
            this.hasInconsistencies = true;
        }
    }

    private void removeSection(long sectionNode) {
        this.toRemove.add(sectionNode);
        this.hasInconsistencies = true;
    }

    protected void swapSectionMap() {
        if (!this.changedSections.isEmpty()) {
            M copy = this.updatingSectionData.copy();
            copy.disableCache();
            this.visibleSectionData = copy;
            this.changedSections.clear();
        }

        if (!this.sectionsAffectedByLightUpdates.isEmpty()) {
            LongIterator iterator = this.sectionsAffectedByLightUpdates.iterator();

            while (iterator.hasNext()) {
                long sectionNode = iterator.nextLong();
                this.chunkSource.onLightUpdate(this.layer, SectionPos.of(sectionNode));
            }

            this.sectionsAffectedByLightUpdates.clear();
        }
    }

    public LayerLightSectionStorage.SectionType getDebugSectionType(long sectionNode) {
        return LayerLightSectionStorage.SectionState.type(this.sectionStates.get(sectionNode));
    }

    protected static class SectionState {
        public static final byte EMPTY = 0;
        private static final int MIN_NEIGHBORS = 0;
        private static final int MAX_NEIGHBORS = 26;
        private static final byte HAS_DATA_BIT = 32;
        private static final byte NEIGHBOR_COUNT_BITS = 31;

        public static byte hasData(byte state, boolean hasData) {
            return (byte)(hasData ? state | 32 : state & -33);
        }

        public static byte neighborCount(byte state, int neighborCount) {
            if (neighborCount >= 0 && neighborCount <= 26) {
                return (byte)(state & -32 | neighborCount & 31);
            } else {
                throw new IllegalArgumentException("Neighbor count was not within range [0; 26]");
            }
        }

        public static boolean hasData(byte state) {
            return (state & 32) != 0;
        }

        public static int neighborCount(byte state) {
            return state & 31;
        }

        public static LayerLightSectionStorage.SectionType type(byte state) {
            if (state == 0) {
                return LayerLightSectionStorage.SectionType.EMPTY;
            } else {
                return hasData(state) ? LayerLightSectionStorage.SectionType.LIGHT_AND_DATA : LayerLightSectionStorage.SectionType.LIGHT_ONLY;
            }
        }
    }

    public enum SectionType {
        EMPTY("2"),
        LIGHT_ONLY("1"),
        LIGHT_AND_DATA("0");

        private final String display;

        SectionType(String display) {
            this.display = display;
        }

        public String display() {
            return this.display;
        }
    }
}
