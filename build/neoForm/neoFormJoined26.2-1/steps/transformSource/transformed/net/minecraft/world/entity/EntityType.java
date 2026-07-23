package net.minecraft.world.entity;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.DependantName;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class EntityType<T extends Entity> implements EntityTypeTest<Entity, T>, FeatureElement {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Holder.Reference<EntityType<?>> builtInRegistryHolder = BuiltInRegistries.ENTITY_TYPE.createIntrusiveHolder(this);
    public static final Codec<EntityType<?>> CODEC = BuiltInRegistries.ENTITY_TYPE.byNameCodec();
    public static final StreamCodec<RegistryFriendlyByteBuf, EntityType<?>> STREAM_CODEC = ByteBufCodecs.registry(Registries.ENTITY_TYPE);
    private final EntityType.EntityFactory<T> factory;
    private final MobCategory category;
    private final TagKey<Block> immuneTo;
    private final boolean serialize;
    private final boolean summon;
    private final boolean fireImmune;
    private final boolean canSpawnFarFromPlayer;
    private final int clientTrackingRange;
    private final int updateInterval;
    private final String descriptionId;
    private @Nullable Component description;
    private final Optional<ResourceKey<LootTable>> lootTable;
    private final EntityDimensions dimensions;
    private final float spawnDimensionsScale;
    private final FeatureFlagSet requiredFeatures;
    private final boolean allowedInPeaceful;

    private final java.util.function.Predicate<EntityType<?>> trackDeltasSupplier;
    private final java.util.function.ToIntFunction<EntityType<?>> trackingRangeSupplier;
    private final java.util.function.ToIntFunction<EntityType<?>> updateIntervalSupplier;
    private final boolean onlyOpCanSetNbt;

    public static Identifier getKey(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type);
    }

    public EntityType(
        EntityType.EntityFactory<T> factory,
        MobCategory category,
        boolean serialize,
        boolean summon,
        boolean fireImmune,
        boolean canSpawnFarFromPlayer,
        TagKey<Block> immuneTo,
        EntityDimensions dimensions,
        float spawnDimensionsScale,
        int clientTrackingRange,
        int updateInterval,
        String descriptionId,
        Optional<ResourceKey<LootTable>> lootTable,
        FeatureFlagSet requiredFeatures,
        boolean allowedInPeaceful
    ) {
        this(factory, category, serialize, summon, fireImmune, canSpawnFarFromPlayer, immuneTo, dimensions, spawnDimensionsScale, clientTrackingRange, updateInterval, descriptionId, lootTable, requiredFeatures, allowedInPeaceful, EntityType::defaultTrackDeltasSupplier, EntityType::defaultTrackingRangeSupplier, EntityType::defaultUpdateIntervalSupplier, false);
    }

    public EntityType(
            EntityType.EntityFactory<T> factory,
            MobCategory category,
            boolean serialize,
            boolean summon,
            boolean fireImmune,
            boolean canSpawnFarFromPlayer,
            TagKey<Block> immuneTo,
            EntityDimensions dimensions,
            float spawnDimensionsScale,
            int clientTrackingRange,
            int updateInterval,
            String descriptionId,
            Optional<ResourceKey<LootTable>> lootTable,
            FeatureFlagSet requiredFeatures,
            boolean allowedInPeaceful,
            final java.util.function.Predicate<EntityType<?>> trackDeltasSupplier,
            final java.util.function.ToIntFunction<EntityType<?>> trackingRangeSupplier,
            final java.util.function.ToIntFunction<EntityType<?>> updateIntervalSupplier,
            boolean onlyOpCanSetNbt
    ) {
        this.factory = factory;
        this.category = category;
        this.canSpawnFarFromPlayer = canSpawnFarFromPlayer;
        this.serialize = serialize;
        this.summon = summon;
        this.fireImmune = fireImmune;
        this.immuneTo = immuneTo;
        this.dimensions = dimensions;
        this.spawnDimensionsScale = spawnDimensionsScale;
        this.clientTrackingRange = clientTrackingRange;
        this.updateInterval = updateInterval;
        this.descriptionId = descriptionId;
        this.lootTable = lootTable;
        this.requiredFeatures = requiredFeatures;
        this.allowedInPeaceful = allowedInPeaceful;
        this.trackDeltasSupplier = trackDeltasSupplier;
        this.trackingRangeSupplier = trackingRangeSupplier;
        this.updateIntervalSupplier = updateIntervalSupplier;
        this.onlyOpCanSetNbt = onlyOpCanSetNbt;
    }

    public @Nullable T spawn(
        ServerLevel level,
        @Nullable ItemStack itemStack,
        @Nullable LivingEntity user,
        BlockPos spawnPos,
        EntitySpawnReason spawnReason,
        boolean tryMoveDown,
        boolean movedUp
    ) {
        PostSpawnProcessor<T> postSpawnConfig;
        if (itemStack != null) {
            postSpawnConfig = createDefaultStackConfig(level, itemStack, user);
        } else {
            postSpawnConfig = PostSpawnProcessor.nop();
        }

        return this.spawn(level, postSpawnConfig, spawnPos, spawnReason, tryMoveDown, movedUp);
    }

    public static <T extends Entity> PostSpawnProcessor<T> createDefaultStackConfig(Level level, ItemStack itemStack, @Nullable LivingEntity user) {
        return appendDefaultStackConfig(PostSpawnProcessor.nop(), level, itemStack, user);
    }

    public static <T extends Entity> PostSpawnProcessor<T> appendDefaultStackConfig(
        PostSpawnProcessor<T> initialConfig, Level level, ItemStack itemStack, @Nullable LivingEntity user
    ) {
        return appendCustomEntityStackConfig(appendComponentsConfig(initialConfig, itemStack), level, itemStack, user);
    }

    public static <T extends Entity> PostSpawnProcessor<T> appendComponentsConfig(PostSpawnProcessor<T> initialConfig, ItemStack itemStack) {
        return initialConfig.andThen(entity -> entity.applyComponentsFromItemStack(itemStack));
    }

    public static <T extends Entity> PostSpawnProcessor<T> appendCustomEntityStackConfig(
        PostSpawnProcessor<T> initialConfig, Level level, ItemStack itemStack, @Nullable LivingEntity user
    ) {
        TypedEntityData<EntityType<?>> entityData = itemStack.get(DataComponents.ENTITY_DATA);
        return entityData != null ? initialConfig.andThen(entity -> updateCustomEntityTag(level, user, entity, entityData)) : initialConfig;
    }

    public @Nullable T spawn(ServerLevel level, BlockPos spawnPos, EntitySpawnReason spawnReason) {
        return this.spawn(level, null, spawnPos, spawnReason, false, false);
    }

    public @Nullable T spawn(
        ServerLevel level,
        @Nullable PostSpawnProcessor<T> postSpawnConfig,
        BlockPos spawnPos,
        EntitySpawnReason spawnReason,
        boolean tryMoveDown,
        boolean movedUp
    ) {
        T entity = this.create(level, postSpawnConfig, spawnPos, spawnReason, tryMoveDown, movedUp);
        if (entity != null) {
            level.addFreshEntityWithPassengers(entity);
            if (entity instanceof Mob mob) {
                mob.playAmbientSound();
            }
        }

        return entity;
    }

    public @Nullable T create(
        ServerLevel level,
        @Nullable PostSpawnProcessor<T> postSpawnConfig,
        BlockPos spawnPos,
        EntitySpawnReason spawnReason,
        boolean tryMoveDown,
        boolean movedUp
    ) {
        T entity = this.create(level, spawnReason);
        if (entity == null) {
            return null;
        }

        double yOff;
        if (tryMoveDown) {
            entity.setPos(spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5);
            yOff = getYOffset(level, spawnPos, movedUp, entity.getBoundingBox());
        } else {
            yOff = 0.0;
        }

        entity.snapTo(spawnPos.getX() + 0.5, spawnPos.getY() + yOff, spawnPos.getZ() + 0.5, Mth.wrapDegrees(level.getRandom().nextFloat() * 360.0F), 0.0F);
        if (entity instanceof Mob mob) {
            mob.yHeadRot = mob.getYRot();
            mob.yBodyRot = mob.getYRot();
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), spawnReason, null);
            if (mob.isSpawnCancelled()) {
                // Neo: Discard mob, spawn was cancelled
                mob.discard();
                // return null, mob was killed, context should be lost
                // fixes llamas for wandering trader spawning if wandering trader was cancelled
                return null;
            }
        }

        if (postSpawnConfig != null) {
            postSpawnConfig.apply(entity);
        }

        return entity;
    }

    protected static double getYOffset(LevelReader level, BlockPos spawnPos, boolean movedUp, AABB entityBox) {
        AABB aabb = new AABB(spawnPos);
        if (movedUp) {
            aabb = aabb.expandTowards(0.0, -1.0, 0.0);
        }

        Iterable<VoxelShape> shapes = level.getCollisions(null, aabb);
        return 1.0 + Shapes.collide(Direction.Axis.Y, entityBox, shapes, movedUp ? -2.0 : -1.0);
    }

    public static void updateCustomEntityTag(Level level, @Nullable LivingEntity user, @Nullable Entity entity, TypedEntityData<EntityType<?>> entityData) {
        MinecraftServer server = level.getServer();
        if (server != null && entity != null) {
            if (entity.getType() == entityData.type()) {
                if (level.isClientSide()
                    || !entity.getType().onlyOpCanSetNbt()
                    || user instanceof Player player && server.getPlayerList().isOp(player.nameAndId())) {
                    entityData.loadInto(entity);
                }
            }
        }
    }

    public boolean canSerialize() {
        return this.serialize;
    }

    public boolean canSummon() {
        return this.summon;
    }

    public boolean fireImmune() {
        return this.fireImmune;
    }

    public boolean canSpawnFarFromPlayer() {
        return this.canSpawnFarFromPlayer;
    }

    public MobCategory getCategory() {
        return this.category;
    }

    public String getDescriptionId() {
        return this.descriptionId;
    }

    public Component getDescription() {
        if (this.description == null) {
            this.description = Component.translatable(this.getDescriptionId());
        }

        return this.description;
    }

    @Override
    public String toString() {
        return this.getDescriptionId();
    }

    public String toShortString() {
        int dot = this.getDescriptionId().lastIndexOf(46);
        return dot == -1 ? this.getDescriptionId() : this.getDescriptionId().substring(dot + 1);
    }

    public Optional<ResourceKey<LootTable>> getDefaultLootTable() {
        return this.lootTable;
    }

    public float getWidth() {
        return this.dimensions.width();
    }

    public float getHeight() {
        return this.dimensions.height();
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.requiredFeatures;
    }

    public boolean canSpawn(Level level) {
        return !this.isEnabled(level.enabledFeatures()) ? false : this.isAllowedInPeaceful() || level.getDifficulty() != Difficulty.PEACEFUL;
    }

    public @Nullable T create(Level level, EntitySpawnReason reason) {
        return this.create(level, new EntitySpawnRequest(reason, false));
    }

    public @Nullable T create(Level level, EntitySpawnRequest request) {
        return !request.ignoreChecks() && !this.canSpawn(level) ? null : this.factory.create(this, level);
    }

    public static Optional<Entity> create(ValueInput input, Level level, EntitySpawnRequest request) {
        return Util.ifElse(
            by(input).map(type -> type.create(level, request)),
            entity -> entity.load(input),
            () -> LOGGER.warn("Skipping Entity with id {}", input.getStringOr("id", "[invalid]"))
        );
    }

    public static Optional<Entity> create(EntityType<?> type, ValueInput input, Level level, EntitySpawnReason reason) {
        Optional<Entity> entity = Optional.ofNullable(type.create(level, reason));
        entity.ifPresent(e -> e.load(input));
        return entity;
    }

    public AABB getSpawnAABB(double x, double y, double z) {
        float halfWidth = this.spawnDimensionsScale * this.getWidth() / 2.0F;
        float height = this.spawnDimensionsScale * this.getHeight();
        return new AABB(x - halfWidth, y, z - halfWidth, x + halfWidth, y + height, z + halfWidth);
    }

    public boolean isBlockDangerous(BlockState state) {
        if (state.is(this.immuneTo)) {
            return false;
        } else {
            return !this.fireImmune && NodeEvaluator.isBurningBlock(state)
                ? true
                : state.is(Blocks.WITHER_ROSE) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.CACTUS) || state.is(Blocks.POWDER_SNOW);
        }
    }

    public EntityDimensions getDimensions() {
        return this.dimensions;
    }

    public static Optional<EntityType<?>> by(ValueInput input) {
        return input.read("id", CODEC);
    }

    public static @Nullable Entity loadEntityRecursive(CompoundTag tag, Level level, EntitySpawnRequest request, EntityProcessor postLoad) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(LOGGER)) {
            return loadEntityRecursive(TagValueInput.create(reporter, level.registryAccess(), tag), level, request, postLoad);
        }
    }

    public static @Nullable Entity loadEntityRecursive(EntityType<?> type, CompoundTag tag, Level level, EntitySpawnReason reason, EntityProcessor postLoad) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(LOGGER)) {
            return loadEntityRecursive(type, TagValueInput.create(reporter, level.registryAccess(), tag), level, reason, postLoad);
        }
    }

    public static @Nullable Entity loadEntityRecursive(ValueInput input, Level level, EntitySpawnReason reason, EntityProcessor postLoad) {
        return loadEntityRecursive(input, level, new EntitySpawnRequest(reason, false), postLoad);
    }

    public static @Nullable Entity loadEntityRecursive(ValueInput input, Level level, EntitySpawnRequest request, EntityProcessor postLoad) {
        return loadStaticEntity(input, level, request)
            .map(postLoad::process)
            .map(entity -> loadPassengersRecursive(entity, input, level, request, postLoad))
            .orElse(null);
    }

    public static @Nullable Entity loadEntityRecursive(EntityType<?> type, ValueInput input, Level level, EntitySpawnReason reason, EntityProcessor postLoad) {
        return loadStaticEntity(type, input, level, reason)
            .map(postLoad::process)
            .map(entity -> loadPassengersRecursive(entity, input, level, new EntitySpawnRequest(reason, false), postLoad))
            .orElse(null);
    }

    private static Entity loadPassengersRecursive(Entity entity, ValueInput input, Level level, EntitySpawnRequest request, EntityProcessor postLoad) {
        for (ValueInput passengerTag : input.childrenListOrEmpty("Passengers")) {
            Entity passenger = loadEntityRecursive(passengerTag, level, request, postLoad);
            if (passenger != null) {
                passenger.startRiding(entity, true, false);
            }
        }

        return entity;
    }

    public static Stream<Entity> loadEntitiesRecursive(ValueInput.ValueInputList entities, Level level, EntitySpawnReason reason) {
        return entities.stream().mapMulti((tag, output) -> loadEntityRecursive(tag, level, reason, entity -> {
            output.accept(entity);
            return entity;
        }));
    }

    private static Optional<Entity> loadStaticEntity(ValueInput input, Level level, EntitySpawnRequest request) {
        try {
            return create(input, level, request);
        } catch (RuntimeException e) {
            LOGGER.warn("Exception loading entity: ", e);
            return Optional.empty();
        }
    }

    private static Optional<Entity> loadStaticEntity(EntityType<?> type, ValueInput input, Level level, EntitySpawnReason reason) {
        try {
            return create(type, input, level, reason);
        } catch (RuntimeException e) {
            LOGGER.warn("Exception loading entity: ", e);
            return Optional.empty();
        }
    }

    public int clientTrackingRange() {
        return trackingRangeSupplier.applyAsInt(this);
    }
    private int defaultTrackingRangeSupplier() {
        return this.clientTrackingRange;
    }

    public int updateInterval() {
        return updateIntervalSupplier.applyAsInt(this);
    }
    private int defaultUpdateIntervalSupplier() {
        return this.updateInterval;
    }

    public boolean trackDeltas() {
        return this.trackDeltasSupplier.test(this);
    }
    private boolean defaultTrackDeltasSupplier() {
        return this != EntityTypes.PLAYER
            && this != EntityTypes.LLAMA_SPIT
            && this != EntityTypes.WITHER
            && this != EntityTypes.BAT
            && this != EntityTypes.ITEM_FRAME
            && this != EntityTypes.GLOW_ITEM_FRAME
            && this != EntityTypes.LEASH_KNOT
            && this != EntityTypes.PAINTING
            && this != EntityTypes.END_CRYSTAL
            && this != EntityTypes.EVOKER_FANGS;
    }

    public @Nullable T tryCast(Entity entity) {
        return (T)(entity.getType() == this ? entity : null);
    }

    @Override
    public Class<? extends Entity> getBaseClass() {
        return Entity.class;
    }

    @Deprecated
    public Holder.Reference<EntityType<?>> builtInRegistryHolder() {
        return this.builtInRegistryHolder;
    }

    public boolean isAllowedInPeaceful() {
        return this.allowedInPeaceful;
    }

    public boolean onlyOpCanSetNbt() {
        if (onlyOpCanSetNbt) return true;
        return EntityTypes.OP_ONLY_CUSTOM_DATA.contains(this);
    }

    public Stream<net.minecraft.tags.TagKey<EntityType<?>>> getTags() {return this.builtInRegistryHolder().tags();}

    public static class Builder<T extends Entity> {
        private final EntityType.EntityFactory<T> factory;
        private final MobCategory category;
        private TagKey<Block> immuneTo = BlockTags.DEFAULT_IMMUNE_TO;
        private boolean serialize = true;
        private boolean summon = true;
        private boolean fireImmune;
        private boolean canSpawnFarFromPlayer;
        private int clientTrackingRange = 5;
        private int updateInterval = 3;
        private EntityDimensions dimensions = EntityDimensions.scalable(0.6F, 1.8F);
        private float spawnDimensionsScale = 1.0F;
        private EntityAttachments.Builder attachments = EntityAttachments.builder();
        private FeatureFlagSet requiredFeatures = FeatureFlags.VANILLA_SET;
        private DependantName<EntityType<?>, Optional<ResourceKey<LootTable>>> lootTable = id -> Optional.of(
            ResourceKey.create(Registries.LOOT_TABLE, id.identifier().withPrefix("entities/"))
        );
        private final DependantName<EntityType<?>, String> descriptionId = id -> Util.makeDescriptionId("entity", id.identifier());
        private boolean allowedInPeaceful = true;
        private java.util.function.Predicate<EntityType<?>> velocityUpdateSupplier = EntityType::defaultTrackDeltasSupplier;
        private java.util.function.ToIntFunction<EntityType<?>> trackingRangeSupplier = EntityType::defaultTrackingRangeSupplier;
        private java.util.function.ToIntFunction<EntityType<?>> updateIntervalSupplier = EntityType::defaultUpdateIntervalSupplier;
        private boolean onlyOpCanSetNbt = false;

        private Builder(EntityType.EntityFactory<T> factory, MobCategory category) {
            this.factory = factory;
            this.category = category;
            this.canSpawnFarFromPlayer = category == MobCategory.CREATURE || category == MobCategory.MISC;
        }

        public static <T extends Entity> EntityType.Builder<T> of(EntityType.EntityFactory<T> factory, MobCategory category) {
            return new EntityType.Builder<>(factory, category);
        }

        public static <T extends Entity> EntityType.Builder<T> createNothing(MobCategory category) {
            return new EntityType.Builder<>((t, l) -> null, category);
        }

        public EntityType.Builder<T> sized(float width, float height) {
            this.dimensions = EntityDimensions.scalable(width, height);
            return this;
        }

        public EntityType.Builder<T> spawnDimensionsScale(float scale) {
            this.spawnDimensionsScale = scale;
            return this;
        }

        public EntityType.Builder<T> eyeHeight(float eyeHeight) {
            this.dimensions = this.dimensions.withEyeHeight(eyeHeight);
            return this;
        }

        public EntityType.Builder<T> passengerAttachments(float... offsetYs) {
            for (float offsetY : offsetYs) {
                this.attachments = this.attachments.attach(EntityAttachment.PASSENGER, 0.0F, offsetY, 0.0F);
            }

            return this;
        }

        public EntityType.Builder<T> passengerAttachments(Vec3... points) {
            for (Vec3 point : points) {
                this.attachments = this.attachments.attach(EntityAttachment.PASSENGER, point);
            }

            return this;
        }

        public EntityType.Builder<T> vehicleAttachment(Vec3 point) {
            return this.attach(EntityAttachment.VEHICLE, point);
        }

        public EntityType.Builder<T> ridingOffset(float ridingOffset) {
            return this.attach(EntityAttachment.VEHICLE, 0.0F, -ridingOffset, 0.0F);
        }

        public EntityType.Builder<T> nameTagOffset(float nameTagOffset) {
            return this.attach(EntityAttachment.NAME_TAG, 0.0F, nameTagOffset, 0.0F);
        }

        public EntityType.Builder<T> attach(EntityAttachment attachment, float x, float y, float z) {
            this.attachments = this.attachments.attach(attachment, x, y, z);
            return this;
        }

        public EntityType.Builder<T> attach(EntityAttachment attachment, Vec3 point) {
            this.attachments = this.attachments.attach(attachment, point);
            return this;
        }

        public EntityType.Builder<T> noSummon() {
            this.summon = false;
            return this;
        }

        public EntityType.Builder<T> noSave() {
            this.serialize = false;
            return this;
        }

        public EntityType.Builder<T> fireImmune() {
            this.fireImmune = true;
            return this;
        }

        public EntityType.Builder<T> immuneTo(TagKey<Block> tag) {
            this.immuneTo = tag;
            return this;
        }

        public EntityType.Builder<T> canSpawnFarFromPlayer() {
            this.canSpawnFarFromPlayer = true;
            return this;
        }

        public EntityType.Builder<T> clientTrackingRange(int clientChunkRange) {
            this.clientTrackingRange = clientChunkRange;
            return this;
        }

        public EntityType.Builder<T> updateInterval(int updateInterval) {
            this.updateInterval = updateInterval;
            return this;
        }

        public EntityType.Builder<T> requiredFeatures(FeatureFlag... flags) {
            this.requiredFeatures = FeatureFlags.REGISTRY.subset(flags);
            return this;
        }

        public EntityType.Builder<T> noLootTable() {
            this.lootTable = DependantName.fixed(Optional.empty());
            return this;
        }

        public EntityType.Builder<T> notInPeaceful() {
            this.allowedInPeaceful = false;
            return this;
        }

        public EntityType.Builder<T> setUpdateInterval(int interval) {
            this.updateIntervalSupplier = t->interval;
            return this;
        }

        public EntityType.Builder<T> setTrackingRange(int range) {
            this.trackingRangeSupplier = t->range;
            return this;
        }

        public EntityType.Builder<T> setShouldReceiveVelocityUpdates(boolean value) {
            this.velocityUpdateSupplier = t->value;
            return this;
        }

        public EntityType.Builder<T> setOnlyOpCanSetNbt(boolean onlyOpCanSetNbt) {
            this.onlyOpCanSetNbt = onlyOpCanSetNbt;
            return this;
        }

        public EntityType<T> build(ResourceKey<EntityType<?>> name) {
            if (this.serialize) {
                Util.fetchChoiceType(References.ENTITY_TREE, name.identifier().toString());
            }

            return new EntityType<>(
                this.factory,
                this.category,
                this.serialize,
                this.summon,
                this.fireImmune,
                this.canSpawnFarFromPlayer,
                this.immuneTo,
                this.dimensions.withAttachments(this.attachments),
                this.spawnDimensionsScale,
                this.clientTrackingRange,
                this.updateInterval,
                this.descriptionId.get(name),
                this.lootTable.get(name),
                this.requiredFeatures,
                this.allowedInPeaceful,
                this.velocityUpdateSupplier,
                this.trackingRangeSupplier,
                this.updateIntervalSupplier,
                this.onlyOpCanSetNbt
            );
        }
    }

    @FunctionalInterface
    public interface EntityFactory<T extends Entity> {
        @Nullable T create(final EntityType<T> entityType, final Level level);
    }
}
