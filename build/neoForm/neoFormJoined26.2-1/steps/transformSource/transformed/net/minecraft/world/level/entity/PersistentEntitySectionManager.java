package net.minecraft.world.level.entity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

public class PersistentEntitySectionManager<T extends EntityAccess> implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Set<UUID> knownUuids = Sets.newHashSet();
    private final LevelCallback<T> callbacks;
    private final EntityPersistentStorage<T> permanentStorage;
    private final EntityLookup<T> visibleEntityStorage;
    private final EntitySectionStorage<T> sectionStorage;
    private final LevelEntityGetter<T> entityGetter;
    private final Long2ObjectMap<Visibility> chunkVisibility = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<PersistentEntitySectionManager.ChunkLoadStatus> chunkLoadStatuses = new Long2ObjectOpenHashMap<>();
    private final LongSet chunksToUnload = new LongOpenHashSet();
    private final Queue<ChunkEntities<T>> loadingInbox = Queues.newConcurrentLinkedQueue();

    public PersistentEntitySectionManager(Class<T> entityClass, LevelCallback<T> callbacks, EntityPersistentStorage<T> permanentStorage) {
        this.visibleEntityStorage = new EntityLookup<>();
        this.sectionStorage = new EntitySectionStorage<>(entityClass, this.chunkVisibility);
        this.chunkVisibility.defaultReturnValue(Visibility.HIDDEN);
        this.chunkLoadStatuses.defaultReturnValue(PersistentEntitySectionManager.ChunkLoadStatus.FRESH);
        this.callbacks = callbacks;
        this.permanentStorage = permanentStorage;
        this.entityGetter = new LevelEntityGetterAdapter<>(this.visibleEntityStorage, this.sectionStorage);
    }

    private void removeSectionIfEmpty(long sectionPos, EntitySection<T> section) {
        if (section.isEmpty()) {
            this.sectionStorage.remove(sectionPos);
        }
    }

    private boolean addEntityUuid(T entity) {
        if (!this.knownUuids.add(entity.getUUID())) {
            LOGGER.warn("UUID of added entity already exists: {}", entity);
            return false;
        } else {
            return true;
        }
    }

    public boolean addNewEntity(T entity) {
        return this.addEntity(entity, false);
    }

    private boolean addEntity(T entity, boolean loaded) {
        if (entity instanceof Entity actualEntity && net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(actualEntity, actualEntity.level(), loaded)).isCanceled())
            return false;
        return addEntityWithoutEvent(entity, loaded);
    }

    public boolean addNewEntityWithoutEvent(T entity) {
        return addEntityWithoutEvent(entity, false);
    }

    private boolean addEntityWithoutEvent(T entity, boolean loaded) {
        if (!this.addEntityUuid(entity)) {
            return false;
        }

        long sectionKey = SectionPos.asLong(entity.blockPosition());
        EntitySection<T> entitySection = this.sectionStorage.getOrCreateSection(sectionKey);
        entitySection.add(entity);
        entity.setLevelCallback(new PersistentEntitySectionManager.Callback(entity, sectionKey, entitySection));
        if (!loaded) {
            this.callbacks.onCreated(entity);
        }

        Visibility status = getEffectiveStatus(entity, entitySection.getStatus());
        if (status.isAccessible()) {
            this.startTracking(entity);
        }

        if (status.isTicking()) {
            this.startTicking(entity);
        }

        return true;
    }

    private static <T extends EntityAccess> Visibility getEffectiveStatus(T entity, Visibility status) {
        return entity.isAlwaysTicking() ? Visibility.TICKING : status;
    }

    public boolean isTicking(ChunkPos pos) {
        return this.chunkVisibility.get(pos.pack()).isTicking();
    }

    public void addLegacyChunkEntities(Stream<T> entities) {
        entities.forEach(e -> {
            this.addEntity(e, true);
            if (e instanceof Entity entity) entity.onAddedToLevel();
        });
    }

    public void addWorldGenChunkEntities(Stream<T> entities) {
        entities.forEach(e -> {
            this.addEntity(e, false);
            if (e instanceof Entity entity) entity.onAddedToLevel();
        });
    }

    private void startTicking(T entity) {
        this.callbacks.onTickingStart(entity);
    }

    private void stopTicking(T entity) {
        this.callbacks.onTickingEnd(entity);
    }

    private void startTracking(T entity) {
        this.visibleEntityStorage.add(entity);
        this.callbacks.onTrackingStart(entity);
    }

    private void stopTracking(T entity) {
        this.callbacks.onTrackingEnd(entity);
        this.visibleEntityStorage.remove(entity);
    }

    public void updateChunkStatus(ChunkPos pos, FullChunkStatus fullChunkStatus) {
        Visibility chunkStatus = Visibility.fromFullChunkStatus(fullChunkStatus);
        this.updateChunkStatus(pos, chunkStatus);
    }

    public void updateChunkStatus(ChunkPos pos, Visibility chunkStatus) {
        long chunkPosKey = pos.pack();
        if (chunkStatus == Visibility.HIDDEN) {
            this.chunkVisibility.remove(chunkPosKey);
            this.chunksToUnload.add(chunkPosKey);
        } else {
            this.chunkVisibility.put(chunkPosKey, chunkStatus);
            this.chunksToUnload.remove(chunkPosKey);
            this.ensureChunkQueuedForLoad(chunkPosKey);
        }

        this.sectionStorage.getExistingSectionsInChunk(chunkPosKey).forEach(section -> {
            Visibility previousStatus = section.updateChunkStatus(chunkStatus);
            boolean wasAccessible = previousStatus.isAccessible();
            boolean isAccessible = chunkStatus.isAccessible();
            boolean wasTicking = previousStatus.isTicking();
            boolean isTicking = chunkStatus.isTicking();
            if (wasTicking && !isTicking) {
                section.getEntities().filter(e -> !e.isAlwaysTicking()).forEach(this::stopTicking);
            }

            if (wasAccessible && !isAccessible) {
                section.getEntities().filter(e -> !e.isAlwaysTicking()).forEach(this::stopTracking);
            } else if (!wasAccessible && isAccessible) {
                section.getEntities().filter(e -> !e.isAlwaysTicking()).forEach(this::startTracking);
            }

            if (!wasTicking && isTicking) {
                section.getEntities().filter(e -> !e.isAlwaysTicking()).forEach(this::startTicking);
            }
        });
    }

    private void ensureChunkQueuedForLoad(long chunkPos) {
        PersistentEntitySectionManager.ChunkLoadStatus chunkLoadStatus = this.chunkLoadStatuses.get(chunkPos);
        if (chunkLoadStatus == PersistentEntitySectionManager.ChunkLoadStatus.FRESH) {
            this.requestChunkLoad(chunkPos);
        }
    }

    private boolean storeChunkSections(long chunkPos, Consumer<T> savedEntityVisitor) {
        PersistentEntitySectionManager.ChunkLoadStatus chunkLoadStatus = this.chunkLoadStatuses.get(chunkPos);
        if (chunkLoadStatus == PersistentEntitySectionManager.ChunkLoadStatus.PENDING) {
            return false;
        }

        List<T> rootEntitiesToSave = this.sectionStorage
            .getExistingSectionsInChunk(chunkPos)
            .flatMap(section -> section.getEntities().filter(EntityAccess::shouldBeSaved))
            .collect(Collectors.toList());
        if (rootEntitiesToSave.isEmpty()) {
            if (chunkLoadStatus == PersistentEntitySectionManager.ChunkLoadStatus.LOADED) {
                this.permanentStorage.storeEntities(new ChunkEntities<>(ChunkPos.unpack(chunkPos), ImmutableList.of()));
            }

            return true;
        } else if (chunkLoadStatus == PersistentEntitySectionManager.ChunkLoadStatus.FRESH) {
            this.requestChunkLoad(chunkPos);
            return false;
        } else {
            this.permanentStorage.storeEntities(new ChunkEntities<>(ChunkPos.unpack(chunkPos), rootEntitiesToSave));
            rootEntitiesToSave.forEach(savedEntityVisitor);
            return true;
        }
    }

    private void requestChunkLoad(long chunkKey) {
        this.chunkLoadStatuses.put(chunkKey, PersistentEntitySectionManager.ChunkLoadStatus.PENDING);
        ChunkPos pos = ChunkPos.unpack(chunkKey);
        this.permanentStorage.loadEntities(pos).thenAccept(this.loadingInbox::add).exceptionally(t -> {
            LOGGER.error("Failed to read chunk {}", pos, t);
            return null;
        });
    }

    private boolean processChunkUnload(long chunkKey) {
        boolean storeSuccessful = this.storeChunkSections(chunkKey, entity -> entity.getPassengersAndSelf().forEach(this::unloadEntity));
        if (!storeSuccessful) {
            return false;
        }

        this.chunkLoadStatuses.remove(chunkKey);
        return true;
    }

    private void unloadEntity(EntityAccess e) {
        e.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        e.setLevelCallback(EntityInLevelCallback.NULL);
    }

    private void processUnloads() {
        this.chunksToUnload.removeIf(chunkKey -> this.chunkVisibility.get(chunkKey) != Visibility.HIDDEN ? true : this.processChunkUnload(chunkKey));
    }

    public void processPendingLoads() {
        ChunkEntities<T> loadedChunk;
        while ((loadedChunk = this.loadingInbox.poll()) != null) {
            loadedChunk.getEntities().forEach(e -> {
                this.addEntity(e, true);
                if (e instanceof Entity entity) entity.onAddedToLevel();
            });
            this.chunkLoadStatuses.put(loadedChunk.getPos().pack(), PersistentEntitySectionManager.ChunkLoadStatus.LOADED);
        }
    }

    public void tick() {
        this.processPendingLoads();
        this.processUnloads();
    }

    private LongSet getAllChunksToSave() {
        LongSet result = this.sectionStorage.getAllChunksWithExistingSections();

        for (Entry<PersistentEntitySectionManager.ChunkLoadStatus> entry : Long2ObjectMaps.fastIterable(this.chunkLoadStatuses)) {
            if (entry.getValue() == PersistentEntitySectionManager.ChunkLoadStatus.LOADED) {
                result.add(entry.getLongKey());
            }
        }

        return result;
    }

    public void autoSave() {
        this.getAllChunksToSave().forEach(chunkKey -> {
            boolean shouldUnload = this.chunkVisibility.get(chunkKey) == Visibility.HIDDEN;
            if (shouldUnload) {
                this.processChunkUnload(chunkKey);
            } else {
                this.storeChunkSections(chunkKey, e -> {});
            }
        });
    }

    public void saveAll() {
        LongSet chunksToSave = this.getAllChunksToSave();

        while (!chunksToSave.isEmpty()) {
            this.permanentStorage.flush(false);
            this.processPendingLoads();
            chunksToSave.removeIf(chunkKey -> {
                boolean shouldUnload = this.chunkVisibility.get(chunkKey) == Visibility.HIDDEN;
                return shouldUnload ? this.processChunkUnload(chunkKey) : this.storeChunkSections(chunkKey, e -> {});
            });
        }

        this.permanentStorage.flush(true);
    }

    @Override
    public void close() throws IOException {
        this.saveAll();
        this.permanentStorage.close();
    }

    public boolean isLoaded(UUID uuid) {
        return this.knownUuids.contains(uuid);
    }

    public LevelEntityGetter<T> getEntityGetter() {
        return this.entityGetter;
    }

    public boolean canPositionTick(BlockPos pos) {
        return this.chunkVisibility.get(ChunkPos.pack(pos)).isTicking();
    }

    public boolean canPositionTick(ChunkPos pos) {
        return this.chunkVisibility.get(pos.pack()).isTicking();
    }

    public boolean areEntitiesLoaded(long chunkKey) {
        return this.chunkLoadStatuses.get(chunkKey) == PersistentEntitySectionManager.ChunkLoadStatus.LOADED;
    }

    public void dumpSections(Writer output) throws IOException {
        CsvOutput csvOutput = CsvOutput.builder()
            .addColumn("x")
            .addColumn("y")
            .addColumn("z")
            .addColumn("visibility")
            .addColumn("load_status")
            .addColumn("entity_count")
            .build(output);
        this.sectionStorage
            .getAllChunksWithExistingSections()
            .forEach(
                chunkKey -> {
                    PersistentEntitySectionManager.ChunkLoadStatus loadStatus = this.chunkLoadStatuses.get(chunkKey);
                    this.sectionStorage
                        .getExistingSectionPositionsInChunk(chunkKey)
                        .forEach(
                            sectionKey -> {
                                EntitySection<T> section = this.sectionStorage.getSection(sectionKey);
                                if (section != null) {
                                    try {
                                        csvOutput.writeRow(
                                            SectionPos.x(sectionKey),
                                            SectionPos.y(sectionKey),
                                            SectionPos.z(sectionKey),
                                            section.getStatus(),
                                            loadStatus,
                                            section.size()
                                        );
                                    } catch (IOException e) {
                                        throw new UncheckedIOException(e);
                                    }
                                }
                            }
                        );
                }
            );
    }

    @VisibleForDebug
    public String gatherStats() {
        return this.knownUuids.size()
            + ","
            + this.visibleEntityStorage.count()
            + ","
            + this.sectionStorage.count()
            + ","
            + this.chunkLoadStatuses.size()
            + ","
            + this.chunkVisibility.size()
            + ","
            + this.loadingInbox.size()
            + ","
            + this.chunksToUnload.size();
    }

    @VisibleForDebug
    public int count() {
        return this.visibleEntityStorage.count();
    }

    private class Callback implements EntityInLevelCallback {
        private final T entity;
        @org.jspecify.annotations.Nullable
        private final Entity realEntity;
        private long currentSectionKey;
        private EntitySection<T> currentSection;

        private Callback(T entity, long currentSectionKey, EntitySection<T> currentSection) {
            this.entity = entity;
            this.realEntity = entity instanceof Entity ? (Entity) entity : null;
            this.currentSectionKey = currentSectionKey;
            this.currentSection = currentSection;
        }

        @Override
        public void onMove() {
            BlockPos pos = this.entity.blockPosition();
            long newSectionPos = SectionPos.asLong(pos);
            if (newSectionPos != this.currentSectionKey) {
                Visibility previousStatus = this.currentSection.getStatus();
                if (!this.currentSection.remove(this.entity)) {
                    PersistentEntitySectionManager.LOGGER
                        .warn("Entity {} wasn't found in section {} (moving to {})", this.entity, SectionPos.of(this.currentSectionKey), newSectionPos);
                }

                PersistentEntitySectionManager.this.removeSectionIfEmpty(this.currentSectionKey, this.currentSection);
                EntitySection<T> newSection = PersistentEntitySectionManager.this.sectionStorage.getOrCreateSection(newSectionPos);
                newSection.add(this.entity);
                long oldSectionKey = currentSectionKey;
                this.currentSection = newSection;
                this.currentSectionKey = newSectionPos;
                this.updateStatus(previousStatus, newSection.getStatus());
                if (this.realEntity != null) net.neoforged.neoforge.common.CommonHooks.onEntityEnterSection(this.realEntity, oldSectionKey, newSectionPos);
            }
        }

        private void updateStatus(Visibility previousStatus, Visibility newStatus) {
            Visibility effectivePreviousStatus = PersistentEntitySectionManager.getEffectiveStatus(this.entity, previousStatus);
            Visibility effectiveNewStatus = PersistentEntitySectionManager.getEffectiveStatus(this.entity, newStatus);
            if (effectivePreviousStatus == effectiveNewStatus) {
                if (effectiveNewStatus.isAccessible()) {
                    PersistentEntitySectionManager.this.callbacks.onSectionChange(this.entity);
                }
            } else {
                boolean wasAccessible = effectivePreviousStatus.isAccessible();
                boolean isAccessible = effectiveNewStatus.isAccessible();
                if (wasAccessible && !isAccessible) {
                    PersistentEntitySectionManager.this.stopTracking(this.entity);
                } else if (!wasAccessible && isAccessible) {
                    PersistentEntitySectionManager.this.startTracking(this.entity);
                }

                boolean wasTicking = effectivePreviousStatus.isTicking();
                boolean isTicking = effectiveNewStatus.isTicking();
                if (wasTicking && !isTicking) {
                    PersistentEntitySectionManager.this.stopTicking(this.entity);
                } else if (!wasTicking && isTicking) {
                    PersistentEntitySectionManager.this.startTicking(this.entity);
                }

                if (isAccessible) {
                    PersistentEntitySectionManager.this.callbacks.onSectionChange(this.entity);
                }
            }
        }

        @Override
        public void onRemove(Entity.RemovalReason reason) {
            if (!this.currentSection.remove(this.entity)) {
                PersistentEntitySectionManager.LOGGER
                    .warn("Entity {} wasn't found in section {} (destroying due to {})", this.entity, SectionPos.of(this.currentSectionKey), reason);
            }

            Visibility status = PersistentEntitySectionManager.getEffectiveStatus(this.entity, this.currentSection.getStatus());
            if (status.isTicking()) {
                PersistentEntitySectionManager.this.stopTicking(this.entity);
            }

            if (status.isAccessible()) {
                PersistentEntitySectionManager.this.stopTracking(this.entity);
            }

            if (reason.shouldDestroy()) {
                PersistentEntitySectionManager.this.callbacks.onDestroyed(this.entity);
            }

            PersistentEntitySectionManager.this.knownUuids.remove(this.entity.getUUID());
            this.entity.setLevelCallback(NULL);
            PersistentEntitySectionManager.this.removeSectionIfEmpty(this.currentSectionKey, this.currentSection);
        }
    }

    private enum ChunkLoadStatus {
        FRESH,
        PENDING,
        LOADED;
    }
}
