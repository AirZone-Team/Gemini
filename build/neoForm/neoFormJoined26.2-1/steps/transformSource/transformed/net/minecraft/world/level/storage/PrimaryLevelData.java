package net.minecraft.world.level.storage;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.CrashReportCategory;
import net.minecraft.SharedConstants;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class PrimaryLevelData implements ServerLevelData, WorldData {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String LEVEL_NAME = "LevelName";
    protected static final String OLD_PLAYER = "Player";
    protected static final String SINGLEPLAYER_UUID = "singleplayer_uuid";
    protected static final String OLD_WORLD_GEN_SETTINGS = "WorldGenSettings";
    private LevelSettings settings;
    private final PrimaryLevelData.SpecialWorldProperty specialWorldProperty;
    private final Lifecycle worldGenSettingsLifecycle;
    private LevelData.RespawnData respawnData;
    private long gameTime;
    private final @Nullable UUID singlePlayerUUID;
    private final int version;
    private boolean initialized;
    private final Set<String> knownServerBrands;
    private boolean wasModded;
    private final Set<String> removedFeatureFlags;
    private boolean confirmedExperimentalWarning = false;

    private PrimaryLevelData(
        @Nullable UUID singlePlayerUUID,
        boolean wasModded,
        LevelData.RespawnData respawnData,
        long gameTime,
        int version,
        boolean initialized,
        Set<String> knownServerBrands,
        Set<String> removedFeatureFlags,
        LevelSettings settings,
        PrimaryLevelData.SpecialWorldProperty specialWorldProperty,
        Lifecycle worldGenSettingsLifecycle
    ) {
        this.wasModded = wasModded;
        this.respawnData = respawnData;
        this.gameTime = gameTime;
        this.version = version;
        this.initialized = initialized;
        this.knownServerBrands = knownServerBrands;
        this.removedFeatureFlags = removedFeatureFlags;
        this.singlePlayerUUID = singlePlayerUUID;
        this.settings = settings;
        this.specialWorldProperty = specialWorldProperty;
        this.worldGenSettingsLifecycle = worldGenSettingsLifecycle;
    }

    public PrimaryLevelData(LevelSettings levelSettings, PrimaryLevelData.SpecialWorldProperty specialWorldProperty, Lifecycle lifecycle) {
        this(
            null,
            false,
            LevelData.RespawnData.DEFAULT,
            0L,
            19133,
            false,
            Sets.newLinkedHashSet(),
            new HashSet<>(),
            levelSettings.copy(),
            specialWorldProperty,
            lifecycle
        );
    }

    public static <T> PrimaryLevelData parse(
        Dynamic<T> input, LevelSettings settings, PrimaryLevelData.SpecialWorldProperty specialWorldProperty, Lifecycle worldGenSettingsLifecycle
    ) {
        long gameTime = input.get("Time").asLong(0L);
        LevelVersion levelVersion = LevelVersion.parse(input);
        var result = new PrimaryLevelData(
            input.get("singleplayer_uuid").flatMap(UUIDUtil.CODEC::parse).result().orElse(null),
            input.get("WasModded").asBoolean(false),
            input.get("spawn").read(LevelData.RespawnData.CODEC).result().orElse(LevelData.RespawnData.DEFAULT),
            gameTime,
            levelVersion.levelDataVersion(),
            input.get("initialized").asBoolean(true),
            input.get("ServerBrands").asStream().flatMap(b -> b.asString().result().stream()).collect(Collectors.toCollection(Sets::newLinkedHashSet)),
            // Neo: Append removed modded feature flags
            updateRemovedFeatureFlags(input.get("removed_features").asStream().flatMap(b -> b.asString().result().stream()), input.get("enabled_features").asStream().flatMap(features -> features.asString().result().stream())).collect(Collectors.toSet()),
            settings,
            specialWorldProperty,
            worldGenSettingsLifecycle
        ).withConfirmedWarning(worldGenSettingsLifecycle != Lifecycle.stable() && input.get("confirmedExperimentalSettings").asBoolean(false));
        // Neo:
        result.setDayTimeFraction(input.get("neoDayTimeFraction").asFloat(0f));
        result.setDayTimePerTick(input.get("neoDayTimePerTick").asFloat(-1f));
        return result;
    }

    @Override
    public CompoundTag createTag(@Nullable UUID singlePlayerUUID) {
        if (singlePlayerUUID == null) {
            singlePlayerUUID = this.singlePlayerUUID;
        }

        CompoundTag tag = new CompoundTag();
        this.setTagData(tag, singlePlayerUUID);
        return tag;
    }

    private void setTagData(CompoundTag tag, @Nullable UUID singlePlayerUUID) {
        tag.put("ServerBrands", stringCollectionToTag(this.knownServerBrands));
        tag.putBoolean("WasModded", this.wasModded);
        if (!this.removedFeatureFlags.isEmpty()) {
            tag.put("removed_features", stringCollectionToTag(this.removedFeatureFlags));
        }

        writeVersionTag(tag);
        NbtUtils.addCurrentDataVersion(tag);
        tag.putInt("GameType", this.settings.gameType().getId());
        tag.store("spawn", LevelData.RespawnData.CODEC, this.respawnData);
        tag.putLong("Time", this.gameTime);
        writeLastPlayed(tag);
        tag.putString("LevelName", this.settings.levelName());
        tag.putInt("version", 19133);
        tag.putBoolean("allowCommands", this.settings.allowCommands());
        tag.putBoolean("initialized", this.initialized);
        tag.store("difficulty_settings", LevelSettings.DifficultySettings.CODEC, this.settings.difficultySettings());
        if (singlePlayerUUID != null) {
            tag.storeNullable("singleplayer_uuid", UUIDUtil.CODEC, singlePlayerUUID);
        }

        tag.store(WorldDataConfiguration.MAP_CODEC, this.settings.dataConfiguration());

        // Neo:
        tag.putString("forgeLifecycle", net.neoforged.neoforge.common.CommonHooks.encodeLifecycle(this.settings.getLifecycle()));
        tag.putBoolean("confirmedExperimentalSettings", this.confirmedExperimentalWarning);
        tag.putFloat("neoDayTimeFraction", dayTimeFraction);
        tag.putFloat("neoDayTimePerTick", dayTimePerTick);
    }

    public static void writeLastPlayed(CompoundTag tag) {
        tag.putLong("LastPlayed", Util.getEpochMillis());
    }

    public static Dynamic<?> writeLastPlayed(Dynamic<?> tag) {
        return tag.set("LastPlayed", tag.createLong(Util.getEpochMillis()));
    }

    public static void writeVersionTag(CompoundTag tag) {
        CompoundTag worldVersion = new CompoundTag();
        worldVersion.putString("Name", SharedConstants.getCurrentVersion().name());
        worldVersion.putInt("Id", SharedConstants.getCurrentVersion().dataVersion().version());
        worldVersion.putBoolean("Snapshot", !SharedConstants.getCurrentVersion().stable());
        worldVersion.putString("Series", SharedConstants.getCurrentVersion().dataVersion().series());
        tag.put("Version", worldVersion);
    }

    public static Dynamic<?> writeVersionTag(Dynamic<?> tag) {
        Dynamic<?> worldVersion = tag.emptyMap()
            .set("Name", tag.createString(SharedConstants.getCurrentVersion().name()))
            .set("Id", tag.createInt(SharedConstants.getCurrentVersion().dataVersion().version()))
            .set("Snapshot", tag.createBoolean(!SharedConstants.getCurrentVersion().stable()))
            .set("Series", tag.createString(SharedConstants.getCurrentVersion().dataVersion().series()));
        return tag.set("Version", worldVersion);
    }

    private static ListTag stringCollectionToTag(Set<String> values) {
        ListTag result = new ListTag();
        values.stream().map(StringTag::valueOf).forEach(result::add);
        return result;
    }

    @Override
    public LevelData.RespawnData getRespawnData() {
        return this.respawnData;
    }

    @Override
    public long getGameTime() {
        return this.gameTime;
    }

    @Override
    public @Nullable UUID getSinglePlayerUUID() {
        return this.singlePlayerUUID;
    }

    @Override
    public void setGameTime(long time) {
        this.gameTime = time;
    }

    @Override
    public void setSpawn(LevelData.RespawnData respawnData) {
        this.respawnData = respawnData;
    }

    @Override
    public String getLevelName() {
        return this.settings.levelName();
    }

    @Override
    public int getVersion() {
        return this.version;
    }

    @Override
    public GameType getGameType() {
        return this.settings.gameType();
    }

    @Override
    public void setGameType(GameType gameType) {
        this.settings = this.settings.withGameType(gameType);
    }

    @Override
    public boolean isHardcore() {
        return this.settings.difficultySettings().hardcore();
    }

    @Override
    public boolean isAllowCommands() {
        return this.settings.allowCommands();
    }

    @Override
    public void setAllowCommands(boolean allowCommands) {
        this.settings = this.settings.withAllowCommands(allowCommands);
    }

    @Override
    public boolean isInitialized() {
        return this.initialized;
    }

    @Override
    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    @Override
    public Difficulty getDifficulty() {
        return this.settings.difficultySettings().difficulty();
    }

    @Override
    public void setDifficulty(Difficulty difficulty) {
        this.settings = this.settings.withDifficulty(difficulty);
    }

    @Override
    public boolean isDifficultyLocked() {
        return this.settings.difficultySettings().locked();
    }

    @Override
    public void setDifficultyLocked(boolean difficultyLocked) {
        this.settings = this.settings.withDifficultyLock(difficultyLocked);
    }

    @Override
    public void fillCrashReportCategory(CrashReportCategory category, LevelHeightAccessor levelHeightAccessor) {
        ServerLevelData.super.fillCrashReportCategory(category, levelHeightAccessor);
        WorldData.super.fillCrashReportCategory(category);
    }

    @Override
    public boolean isFlatWorld() {
        return this.specialWorldProperty == PrimaryLevelData.SpecialWorldProperty.FLAT;
    }

    @Override
    public boolean isDebugWorld() {
        return this.specialWorldProperty == PrimaryLevelData.SpecialWorldProperty.DEBUG;
    }

    @Override
    public Lifecycle worldGenSettingsLifecycle() {
        return this.worldGenSettingsLifecycle;
    }

    @Override
    public WorldDataConfiguration getDataConfiguration() {
        return this.settings.dataConfiguration();
    }

    @Override
    public void setDataConfiguration(WorldDataConfiguration dataConfiguration) {
        this.settings = this.settings.withDataConfiguration(dataConfiguration);
    }

    @Override
    public void setModdedInfo(String serverBrand, boolean isModded) {
        this.knownServerBrands.add(serverBrand);
        this.wasModded |= isModded;
    }

    @Override
    public boolean wasModded() {
        return this.wasModded;
    }

    @Override
    public Set<String> getKnownServerBrands() {
        return ImmutableSet.copyOf(this.knownServerBrands);
    }

    @Override
    public Set<String> getRemovedFeatureFlags() {
        return Set.copyOf(this.removedFeatureFlags);
    }

    @Override
    public ServerLevelData overworldData() {
        return this;
    }

    @Override
    public LevelSettings getLevelSettings() {
        return this.settings.copy();
    }

    public boolean hasConfirmedExperimentalWarning() {
        return this.confirmedExperimentalWarning;
    }

    public PrimaryLevelData withConfirmedWarning(boolean confirmedWarning) { // Builder-like to not patch ctor
        this.confirmedExperimentalWarning = confirmedWarning;
        return this;
    }

    @Deprecated
    public enum SpecialWorldProperty {
        NONE,
        FLAT,
        DEBUG;
    }

    // Neo: Variable day time code

    private float dayTimeFraction = 0.0f;
    private float dayTimePerTick = -1.0f;

    @Override
    public float getDayTimeFraction() {
        return dayTimeFraction;
    }

    @Override
    public float getDayTimePerTick() {
        return dayTimePerTick;
    }

    @Override
    public void setDayTimeFraction(float dayTimeFraction) {
        this.dayTimeFraction = dayTimeFraction;
    }

    @Override
    public void setDayTimePerTick(float dayTimePerTick) {
        this.dayTimePerTick = dayTimePerTick;
    }

    private static java.util.stream.Stream<String> updateRemovedFeatureFlags(java.util.stream.Stream<String> removedFeatures, java.util.stream.Stream<String> enabledFeatures) {
        var unknownFeatureFlags = new HashSet<net.minecraft.resources.Identifier>();
        // parses the incoming Stream<String> and spits out unknown flag names (Identifier)
        // we do not care about the returned FeatureFlagSet, only the flags which do not exist
        net.minecraft.world.flag.FeatureFlags.REGISTRY.fromNames(enabledFeatures.map(net.minecraft.resources.Identifier::parse).collect(Collectors.toSet()), unknownFeatureFlags::add);
        // concat the received removed flags with our new additions
        return java.util.stream.Stream.concat(removedFeatures, unknownFeatureFlags.stream()
                // we only want modded flags, mojang has datafixers for vanilla flags
                .filter(java.util.function.Predicate.not(name -> name.getNamespace().equals(net.minecraft.resources.Identifier.DEFAULT_NAMESPACE)))
                .map(net.minecraft.resources.Identifier::toString))
                // no duplicates should exist in this stream
                .distinct();
    }
}
