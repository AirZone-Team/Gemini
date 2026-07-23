package net.minecraft.server;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.Validatable;
import net.minecraft.world.level.storage.loot.ValidationContextSource;
import org.slf4j.Logger;

public class ReloadableServerRegistries {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final RegistrationInfo DEFAULT_REGISTRATION_INFO = new RegistrationInfo(Optional.empty(), Lifecycle.experimental());

    public static CompletableFuture<ReloadableServerRegistries.LoadResult> reload(
        LayeredRegistryAccess<RegistryLayer> context, List<Registry.PendingTags<?>> updatedContextTags, ResourceManager manager, Executor executor
    ) {
        List<HolderLookup.RegistryLookup<?>> contextRegistriesWithTags = TagLoader.buildUpdatedLookups(
            context.getAccessForLoading(RegistryLayer.RELOADABLE), updatedContextTags
        );
        HolderLookup.Provider loadingContextWithTags = HolderLookup.Provider.create(contextRegistriesWithTags.stream());
        RegistryOps<JsonElement> ops = loadingContextWithTags.createSerializationContext(JsonOps.INSTANCE);
        List<CompletableFuture<WritableRegistry<?>>> registryLoads = LootDataType.values()
            .map(type -> scheduleRegistryLoad((LootDataType<?>)type, ops, manager, executor))
            .toList();
        CompletableFuture<List<WritableRegistry<?>>> sequence = Util.sequence(registryLoads);
        return sequence.thenApplyAsync(
            newlyLoadedRegistries -> createAndValidateFullContext(context, loadingContextWithTags, (List<WritableRegistry<?>>)newlyLoadedRegistries), executor
        );
    }

    private static <T extends Validatable> CompletableFuture<WritableRegistry<?>> scheduleRegistryLoad(
        LootDataType<T> type, RegistryOps<JsonElement> ops, ResourceManager manager, Executor taskExecutor
    ) {
        return CompletableFuture.supplyAsync(() -> {
            WritableRegistry<T> registry = new MappedRegistry<>(type.registryKey(), Lifecycle.experimental());
            Map<Identifier, T> elements = new HashMap<>();
            var provider = net.neoforged.neoforge.common.CommonHooks.extractLookupProvider(ops);
            Map<Identifier, Optional<T>> optionalMap = new HashMap<>();
            SimpleJsonResourceReloadListener.scanDirectoryWithOptionalValues(manager, type.registryKey(), ops, type.conditionalCodec(), optionalMap);
            optionalMap.forEach((rl, optionalEntry) -> {
                optionalEntry.ifPresent(entry -> type.idSetter().accept(entry, rl));
                T value = optionalEntry.orElse(type.defaultValue());
                if (value instanceof LootTable lootTable) value = (T) net.neoforged.neoforge.event.EventHooks.loadLootTable(provider, rl, lootTable);
                if (value != null)
                    elements.put(rl, value);
            });
            elements.forEach((id, element) -> registry.register(ResourceKey.create(type.registryKey(), id), (T)element, DEFAULT_REGISTRATION_INFO));
            TagLoader.loadTagsForRegistry(manager, registry);
            return registry;
        }, taskExecutor);
    }

    private static ReloadableServerRegistries.LoadResult createAndValidateFullContext(
        LayeredRegistryAccess<RegistryLayer> contextLayers, HolderLookup.Provider contextLookupWithUpdatedTags, List<WritableRegistry<?>> newRegistries
    ) {
        LayeredRegistryAccess<RegistryLayer> fullLayers = createUpdatedRegistries(contextLayers, newRegistries);
        HolderLookup.Provider fullLookupWithUpdatedTags = concatenateLookups(contextLookupWithUpdatedTags, fullLayers.getLayer(RegistryLayer.RELOADABLE));
        validateLootRegistries(fullLookupWithUpdatedTags);
        return new ReloadableServerRegistries.LoadResult(fullLayers, fullLookupWithUpdatedTags);
    }

    private static HolderLookup.Provider concatenateLookups(HolderLookup.Provider first, HolderLookup.Provider second) {
        return HolderLookup.Provider.create(Stream.concat(first.listRegistries(), second.listRegistries()));
    }

    private static void validateLootRegistries(HolderLookup.Provider fullContextWithNewTags) {
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        ValidationContextSource contextSource = new ValidationContextSource(problems, fullContextWithNewTags);
        LootDataType.values().forEach(lootDataType -> validateRegistry(contextSource, (LootDataType<?>)lootDataType, fullContextWithNewTags));
        problems.forEach((id, problem) -> LOGGER.warn("Found loot table element validation problem in {}: {}", id, problem.description()));
    }

    private static LayeredRegistryAccess<RegistryLayer> createUpdatedRegistries(
        LayeredRegistryAccess<RegistryLayer> context, List<WritableRegistry<?>> registries
    ) {
        return context.replaceFrom(RegistryLayer.RELOADABLE, new RegistryAccess.ImmutableRegistryAccess(registries).freeze());
    }

    private static <T extends Validatable> void validateRegistry(ValidationContextSource contextSource, LootDataType<T> type, HolderLookup.Provider registries) {
        HolderLookup<T> registry = registries.lookupOrThrow(type.registryKey());
        type.runValidation(contextSource, registry);
    }

    public static class Holder {
        private final HolderLookup.Provider registries;

        public Holder(HolderLookup.Provider registries) {
            this.registries = registries;
        }

        public HolderLookup.Provider lookup() {
            return this.registries;
        }

        public LootTable getLootTable(ResourceKey<LootTable> id) {
            return this.registries.lookup(Registries.LOOT_TABLE).flatMap(r -> r.get(id)).map(net.minecraft.core.Holder::value).orElse(LootTable.EMPTY);
        }
    }

    public record LoadResult(LayeredRegistryAccess<RegistryLayer> layers, HolderLookup.Provider lookupWithUpdatedTags) {
    }
}
