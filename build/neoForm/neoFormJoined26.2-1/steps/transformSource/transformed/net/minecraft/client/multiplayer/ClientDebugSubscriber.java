package net.minecraft.client.multiplayer;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundDebugSubscriptionRequestPacket;
import net.minecraft.util.debug.DebugSubscription;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.util.debugchart.RemoteDebugSampleType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class ClientDebugSubscriber {
    private final ClientPacketListener connection;
    private final DebugScreenOverlay debugScreenOverlay;
    private Set<DebugSubscription<?>> remoteSubscriptions = Set.of();
    private final Map<DebugSubscription<?>, ClientDebugSubscriber.ValueMaps<?>> valuesBySubscription = new HashMap<>();

    public ClientDebugSubscriber(ClientPacketListener connection, DebugScreenOverlay debugScreenOverlay) {
        this.debugScreenOverlay = debugScreenOverlay;
        this.connection = connection;
    }

    private static void addFlag(Set<DebugSubscription<?>> output, DebugSubscription<?> subscription, boolean flag) {
        if (flag) {
            output.add(subscription);
        }
    }

    private Set<DebugSubscription<?>> requestedSubscriptions() {
        Set<DebugSubscription<?>> subscriptions = new ReferenceOpenHashSet<>();
        addFlag(subscriptions, RemoteDebugSampleType.TICK_TIME.subscription(), this.debugScreenOverlay.showFpsCharts());
        if (SharedConstants.DEBUG_ENABLED) {
            addFlag(subscriptions, DebugSubscriptions.BEES, SharedConstants.DEBUG_BEES);
            addFlag(subscriptions, DebugSubscriptions.BEE_HIVES, SharedConstants.DEBUG_BEES);
            addFlag(subscriptions, DebugSubscriptions.BRAINS, SharedConstants.DEBUG_BRAIN);
            addFlag(subscriptions, DebugSubscriptions.BREEZES, SharedConstants.DEBUG_BREEZE_MOB);
            addFlag(subscriptions, DebugSubscriptions.ENTITY_BLOCK_INTERSECTIONS, SharedConstants.DEBUG_ENTITY_BLOCK_INTERSECTION);
            addFlag(subscriptions, DebugSubscriptions.ENTITY_PATHS, SharedConstants.DEBUG_PATHFINDING);
            addFlag(subscriptions, DebugSubscriptions.GAME_EVENTS, SharedConstants.DEBUG_GAME_EVENT_LISTENERS);
            addFlag(subscriptions, DebugSubscriptions.GAME_EVENT_LISTENERS, SharedConstants.DEBUG_GAME_EVENT_LISTENERS);
            addFlag(subscriptions, DebugSubscriptions.GOAL_SELECTORS, SharedConstants.DEBUG_GOAL_SELECTOR || SharedConstants.DEBUG_BEES);
            addFlag(subscriptions, DebugSubscriptions.NEIGHBOR_UPDATES, SharedConstants.DEBUG_NEIGHBORSUPDATE);
            addFlag(subscriptions, DebugSubscriptions.POIS, SharedConstants.DEBUG_POI);
            addFlag(subscriptions, DebugSubscriptions.RAIDS, SharedConstants.DEBUG_RAIDS);
            addFlag(subscriptions, DebugSubscriptions.REDSTONE_WIRE_ORIENTATIONS, SharedConstants.DEBUG_EXPERIMENTAL_REDSTONEWIRE_UPDATE_ORDER);
            addFlag(subscriptions, DebugSubscriptions.STRUCTURES, SharedConstants.DEBUG_STRUCTURES);
            addFlag(subscriptions, DebugSubscriptions.VILLAGE_SECTIONS, SharedConstants.DEBUG_VILLAGE_SECTIONS);
        }

        // Neo: Allow requesting custom subscriptions
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.AddDebugSubscriptionFlagsEvent((subscription, flag) -> addFlag(subscriptions, subscription, flag)));
        return subscriptions;
    }

    public void clear() {
        this.remoteSubscriptions = Set.of();
        this.dropLevel();
    }

    public void tick(long gameTime) {
        Set<DebugSubscription<?>> newSubscriptions = this.requestedSubscriptions();
        if (!newSubscriptions.equals(this.remoteSubscriptions)) {
            this.remoteSubscriptions = newSubscriptions;
            this.onSubscriptionsChanged(newSubscriptions);
        }

        this.valuesBySubscription.forEach((subscription, valueMaps) -> {
            if (subscription.expireAfterTicks() != 0) {
                valueMaps.purgeExpired(gameTime);
            }
        });
    }

    private void onSubscriptionsChanged(Set<DebugSubscription<?>> newSubscriptions) {
        this.valuesBySubscription.keySet().retainAll(newSubscriptions);
        this.initializeSubscriptions(newSubscriptions);
        this.connection.send(new ServerboundDebugSubscriptionRequestPacket(newSubscriptions));
    }

    private void initializeSubscriptions(Set<DebugSubscription<?>> newSubscriptions) {
        for (DebugSubscription<?> subscription : newSubscriptions) {
            this.valuesBySubscription.computeIfAbsent(subscription, s -> new ClientDebugSubscriber.ValueMaps());
        }
    }

    private <V> ClientDebugSubscriber.@Nullable ValueMaps<V> getValueMaps(DebugSubscription<V> subscription) {
        return (ClientDebugSubscriber.ValueMaps<V>)this.valuesBySubscription.get(subscription);
    }

    private <K, V> ClientDebugSubscriber.@Nullable ValueMap<K, V> getValueMap(
        DebugSubscription<V> subscription, ClientDebugSubscriber.ValueMapType<K, V> mapType
    ) {
        ClientDebugSubscriber.ValueMaps<V> maps = this.getValueMaps(subscription);
        return maps != null ? mapType.get(maps) : null;
    }

    private <K, V> @Nullable V getValue(DebugSubscription<V> subscription, K key, ClientDebugSubscriber.ValueMapType<K, V> type) {
        ClientDebugSubscriber.ValueMap<K, V> values = this.getValueMap(subscription, type);
        return values != null ? values.getValue(key) : null;
    }

    public DebugValueAccess createDebugValueAccess(Level level) {
        return new DebugValueAccess() {
            @Override
            public <T> void forEachChunk(DebugSubscription<T> subscription, BiConsumer<ChunkPos, T> consumer) {
                ClientDebugSubscriber.this.forEachValue(subscription, ClientDebugSubscriber.chunks(), consumer);
            }

            @Override
            public <T> @Nullable T getChunkValue(DebugSubscription<T> subscription, ChunkPos chunkPos) {
                return ClientDebugSubscriber.this.getValue(subscription, chunkPos, ClientDebugSubscriber.chunks());
            }

            @Override
            public <T> void forEachBlock(DebugSubscription<T> subscription, BiConsumer<BlockPos, T> consumer) {
                ClientDebugSubscriber.this.forEachValue(subscription, ClientDebugSubscriber.blocks(), consumer);
            }

            @Override
            public <T> @Nullable T getBlockValue(DebugSubscription<T> subscription, BlockPos blockPos) {
                return ClientDebugSubscriber.this.getValue(subscription, blockPos, ClientDebugSubscriber.blocks());
            }

            @Override
            public <T> void forEachEntity(DebugSubscription<T> subscription, BiConsumer<Entity, T> consumer) {
                ClientDebugSubscriber.this.forEachValue(subscription, ClientDebugSubscriber.entities(), (entityId, value) -> {
                    Entity entity = level.getEntity(entityId);
                    if (entity != null) {
                        consumer.accept(entity, value);
                    }
                });
            }

            @Override
            public <T> @Nullable T getEntityValue(DebugSubscription<T> subscription, Entity entity) {
                return ClientDebugSubscriber.this.getValue(subscription, entity.getUUID(), ClientDebugSubscriber.entities());
            }

            @Override
            public <T> void forEachEvent(DebugSubscription<T> subscription, DebugValueAccess.EventVisitor<T> visitor) {
                ClientDebugSubscriber.ValueMaps<T> values = ClientDebugSubscriber.this.getValueMaps(subscription);
                if (values != null) {
                    long gameTime = level.getGameTime();

                    for (ClientDebugSubscriber.ValueWrapper<T> event : values.events) {
                        int remainingTicks = (int)(event.expiresAfterTime() - gameTime);
                        int totalLifetime = subscription.expireAfterTicks();
                        visitor.accept(event.value(), remainingTicks, totalLifetime);
                    }
                }
            }
        };
    }

    public <T> void updateChunk(long gameTime, ChunkPos chunkPos, DebugSubscription.Update<T> update) {
        this.updateMap(gameTime, chunkPos, update, chunks());
    }

    public <T> void updateBlock(long gameTime, BlockPos blockPos, DebugSubscription.Update<T> update) {
        this.updateMap(gameTime, blockPos, update, blocks());
    }

    public <T> void updateEntity(long gameTime, Entity entity, DebugSubscription.Update<T> update) {
        this.updateMap(gameTime, entity.getUUID(), update, entities());
    }

    public <T> void pushEvent(long gameTime, DebugSubscription.Event<T> event) {
        ClientDebugSubscriber.ValueMaps<T> values = this.getValueMaps(event.subscription());
        if (values != null) {
            values.events.add(new ClientDebugSubscriber.ValueWrapper<>(event.value(), gameTime + event.subscription().expireAfterTicks()));
        }
    }

    private <K, V> void updateMap(long gameTime, K key, DebugSubscription.Update<V> update, ClientDebugSubscriber.ValueMapType<K, V> type) {
        ClientDebugSubscriber.ValueMap<K, V> values = this.getValueMap(update.subscription(), type);
        if (values != null) {
            values.apply(gameTime, key, update);
        }
    }

    private <K, V> void forEachValue(DebugSubscription<V> subscription, ClientDebugSubscriber.ValueMapType<K, V> type, BiConsumer<K, V> consumer) {
        ClientDebugSubscriber.ValueMap<K, V> values = this.getValueMap(subscription, type);
        if (values != null) {
            values.forEach(consumer);
        }
    }

    public void dropLevel() {
        this.valuesBySubscription.clear();
        this.initializeSubscriptions(this.remoteSubscriptions);
    }

    public void dropChunk(ChunkPos chunkPos) {
        if (!this.valuesBySubscription.isEmpty()) {
            for (ClientDebugSubscriber.ValueMaps<?> values : this.valuesBySubscription.values()) {
                values.dropChunkAndBlocks(chunkPos);
            }
        }
    }

    public void dropEntity(Entity entity) {
        if (!this.valuesBySubscription.isEmpty()) {
            for (ClientDebugSubscriber.ValueMaps<?> values : this.valuesBySubscription.values()) {
                values.entityValues.removeKey(entity.getUUID());
            }
        }
    }

    private static <T> ClientDebugSubscriber.ValueMapType<UUID, T> entities() {
        return v -> v.entityValues;
    }

    private static <T> ClientDebugSubscriber.ValueMapType<BlockPos, T> blocks() {
        return v -> v.blockValues;
    }

    private static <T> ClientDebugSubscriber.ValueMapType<ChunkPos, T> chunks() {
        return v -> v.chunkValues;
    }

    private static class ValueMap<K, V> {
        private final Map<K, ClientDebugSubscriber.ValueWrapper<V>> values = new HashMap<>();

        public void removeValues(Predicate<ClientDebugSubscriber.ValueWrapper<V>> predicate) {
            this.values.values().removeIf(predicate);
        }

        public void removeKey(K key) {
            this.values.remove(key);
        }

        public void removeKeys(Predicate<K> predicate) {
            this.values.keySet().removeIf(predicate);
        }

        public @Nullable V getValue(K key) {
            ClientDebugSubscriber.ValueWrapper<V> result = this.values.get(key);
            return result != null ? result.value() : null;
        }

        public void apply(long gameTime, K key, DebugSubscription.Update<V> update) {
            if (update.value().isPresent()) {
                this.values.put(key, new ClientDebugSubscriber.ValueWrapper<>(update.value().get(), gameTime + update.subscription().expireAfterTicks()));
            } else {
                this.values.remove(key);
            }
        }

        public void forEach(BiConsumer<K, V> output) {
            this.values.forEach((k, v) -> output.accept((K)k, v.value()));
        }
    }

    @FunctionalInterface
    private interface ValueMapType<K, V> {
        ClientDebugSubscriber.ValueMap<K, V> get(ClientDebugSubscriber.ValueMaps<V> maps);
    }

    private static class ValueMaps<V> {
        private final ClientDebugSubscriber.ValueMap<ChunkPos, V> chunkValues = new ClientDebugSubscriber.ValueMap<>();
        private final ClientDebugSubscriber.ValueMap<BlockPos, V> blockValues = new ClientDebugSubscriber.ValueMap<>();
        private final ClientDebugSubscriber.ValueMap<UUID, V> entityValues = new ClientDebugSubscriber.ValueMap<>();
        private final List<ClientDebugSubscriber.ValueWrapper<V>> events = new ArrayList<>();

        public void purgeExpired(long gameTime) {
            Predicate<ClientDebugSubscriber.ValueWrapper<V>> expiredPredicate = v -> v.hasExpired(gameTime);
            this.chunkValues.removeValues(expiredPredicate);
            this.blockValues.removeValues(expiredPredicate);
            this.entityValues.removeValues(expiredPredicate);
            this.events.removeIf(expiredPredicate);
        }

        public void dropChunkAndBlocks(ChunkPos chunkPos) {
            this.chunkValues.removeKey(chunkPos);
            this.blockValues.removeKeys(chunkPos::contains);
        }
    }

    private record ValueWrapper<T>(T value, long expiresAfterTime) {
        private static final long NO_EXPIRY = -1L;

        public boolean hasExpired(long gameTime) {
            return this.expiresAfterTime == -1L ? false : gameTime >= this.expiresAfterTime;
        }
    }
}
