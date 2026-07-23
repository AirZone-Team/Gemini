package net.minecraft.world.entity;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.players.OldUsersConverter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.UUIDLookup;
import net.minecraft.world.level.entity.UniquelyIdentifyable;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

public final class EntityReference<StoredEntityType extends UniquelyIdentifyable> {
    private static final Codec<? extends EntityReference<?>> CODEC = UUIDUtil.CODEC.xmap(EntityReference::new, EntityReference::getUUID);
    private static final StreamCodec<ByteBuf, ? extends EntityReference<?>> STREAM_CODEC = UUIDUtil.STREAM_CODEC
        .map(EntityReference::new, EntityReference::getUUID);
    private Either<UUID, StoredEntityType> entity;

    public static <Type extends UniquelyIdentifyable> Codec<EntityReference<Type>> codec() {
        return (Codec<EntityReference<Type>>)CODEC;
    }

    public static <Type extends UniquelyIdentifyable> StreamCodec<ByteBuf, EntityReference<Type>> streamCodec() {
        return (StreamCodec<ByteBuf, EntityReference<Type>>)STREAM_CODEC;
    }

    private EntityReference(StoredEntityType entity) {
        this.entity = Either.right(entity);
    }

    private EntityReference(UUID uuid) {
        this.entity = Either.left(uuid);
    }

    @Contract("!null -> !null; null -> null")
    public static <T extends UniquelyIdentifyable> @Nullable EntityReference<T> of(@Nullable T entity) {
        return entity != null ? new EntityReference<>(entity) : null;
    }

    public static <T extends UniquelyIdentifyable> EntityReference<T> of(UUID uuid) {
        return new EntityReference<>(uuid);
    }

    public UUID getUUID() {
        return this.entity.map(uuid -> (UUID)uuid, UniquelyIdentifyable::getUUID);
    }

    public @Nullable StoredEntityType getEntity(UUIDLookup<? extends UniquelyIdentifyable> lookup, Class<StoredEntityType> clazz) {
        Optional<StoredEntityType> stored = this.entity.right();
        if (stored.isPresent()) {
            StoredEntityType storedEntity = stored.get();
            if (!storedEntity.isRemoved()) {
                return storedEntity;
            }

            this.entity = Either.left(storedEntity.getUUID());
        }

        Optional<UUID> uuid = this.entity.left();
        if (uuid.isPresent()) {
            StoredEntityType resolved = this.resolve(lookup.lookup(uuid.get()), clazz);
            if (resolved != null && !resolved.isRemoved()) {
                this.entity = Either.right(resolved);
                return resolved;
            }
        }

        return null;
    }

    public @Nullable StoredEntityType getEntity(Level level, Class<StoredEntityType> clazz) {
        return Player.class.isAssignableFrom(clazz)
            ? this.getEntity(level::getPlayerInAnyDimension, clazz)
            : this.getEntity(level::getEntityInAnyDimension, clazz);
    }

    private @Nullable StoredEntityType resolve(@Nullable UniquelyIdentifyable entity, Class<StoredEntityType> clazz) {
        return entity != null && clazz.isAssignableFrom(entity.getClass()) ? clazz.cast(entity) : null;
    }

    public boolean matches(StoredEntityType entity) {
        return this.getUUID().equals(entity.getUUID());
    }

    public void store(ValueOutput output, String key) {
        output.store(key, UUIDUtil.CODEC, this.getUUID());
    }

    public static void store(@Nullable EntityReference<?> reference, ValueOutput output, String key) {
        if (reference != null) {
            reference.store(output, key);
        }
    }

    public static <StoredEntityType extends UniquelyIdentifyable> @Nullable StoredEntityType get(
        @Nullable EntityReference<StoredEntityType> reference, Level level, Class<StoredEntityType> clazz
    ) {
        return reference != null ? reference.getEntity(level, clazz) : null;
    }

    public static @Nullable Entity getEntity(@Nullable EntityReference<Entity> reference, Level level) {
        return get(reference, level, Entity.class);
    }

    public static @Nullable LivingEntity getLivingEntity(@Nullable EntityReference<LivingEntity> reference, Level level) {
        return get(reference, level, LivingEntity.class);
    }

    public static @Nullable Player getPlayer(@Nullable EntityReference<Player> reference, Level level) {
        return get(reference, level, Player.class);
    }

    public static <StoredEntityType extends UniquelyIdentifyable> @Nullable EntityReference<StoredEntityType> read(ValueInput input, String key) {
        return input.read(key, EntityReference.<StoredEntityType>codec()).orElse(null);
    }

    public static <StoredEntityType extends UniquelyIdentifyable> @Nullable EntityReference<StoredEntityType> readWithOldOwnerConversion(
        ValueInput input, String key, Level level
    ) {
        Optional<UUID> uuid = input.read(key, UUIDUtil.CODEC);
        return uuid.isPresent()
            ? of(uuid.get())
            : input.getString(key)
                .map(oldName -> OldUsersConverter.convertMobOwnerIfNecessary(level.getServer(), oldName))
                .map(EntityReference<StoredEntityType>::new)
                .orElse(null);
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this ? true : obj instanceof EntityReference<?> reference && this.getUUID().equals(reference.getUUID());
    }

    @Override
    public int hashCode() {
        return this.getUUID().hashCode();
    }
}
