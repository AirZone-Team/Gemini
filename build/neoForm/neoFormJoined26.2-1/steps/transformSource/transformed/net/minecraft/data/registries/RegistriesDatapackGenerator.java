package net.minecraft.data.registries;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.JsonOps;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;

/**
 * @deprecated Forge: Use {@link net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider} instead
 */
@Deprecated
public class RegistriesDatapackGenerator implements DataProvider {
    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> registries;
    private final java.util.function.Predicate<String> namespacePredicate;
    private final java.util.Map<ResourceKey<?>, java.util.List<net.neoforged.neoforge.common.conditions.ICondition>> conditions;

    @Deprecated
    public RegistriesDatapackGenerator(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        this(output, registries, null, java.util.Map.of());
    }

    public RegistriesDatapackGenerator(PackOutput output, CompletableFuture<HolderLookup.Provider> registries, java.util.@org.jspecify.annotations.Nullable Set<String> modIds) {
        this(output, registries, modIds, java.util.Map.of());
    }

    public RegistriesDatapackGenerator(PackOutput output, CompletableFuture<HolderLookup.Provider> registries, java.util.@org.jspecify.annotations.Nullable Set<String> modIds, java.util.Map<ResourceKey<?>, java.util.List<net.neoforged.neoforge.common.conditions.ICondition>> conditions) {
        this.namespacePredicate = modIds == null ? namespace -> true : modIds::contains;
        this.registries = registries;
        this.output = output;
        this.conditions = conditions;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return this.registries
            .thenCompose(
                access -> {
                    DynamicOps<JsonElement> registryOps = access.createSerializationContext(JsonOps.INSTANCE);
                    return CompletableFuture.allOf(
                            net.neoforged.neoforge.registries.DataPackRegistriesHooks.getDataPackRegistriesWithDimensions()
                            .flatMap(v -> this.dumpRegistryCap(cache, access, registryOps, (RegistryDataLoader.RegistryData<?>)v).stream())
                            .toArray(CompletableFuture[]::new)
                    );
                }
            );
    }

    private <T> Optional<CompletableFuture<?>> dumpRegistryCap(
        CachedOutput cache, HolderLookup.Provider registries, DynamicOps<JsonElement> writeOps, RegistryDataLoader.RegistryData<T> v
    ) {
        ResourceKey<? extends Registry<T>> registryKey = v.key();
        var conditionalCodec = net.neoforged.neoforge.common.conditions.ConditionalOps.createConditionalCodecWithConditions(v.elementCodec());
        return registries.lookup(registryKey)
            .map(
                registry -> {
                    PackOutput.PathProvider pathProvider = this.output.createRegistryElementsPathProvider(registryKey);
                    return CompletableFuture.allOf(
                        registry.listElements()
                            .filter(holder -> this.namespacePredicate.test(holder.key().identifier().getNamespace()))
                            .map(e -> (CompletableFuture<Object>) dumpValue(pathProvider.json(e.key().identifier()), cache, writeOps, conditionalCodec, Optional.of(new net.neoforged.neoforge.common.conditions.WithConditions<>(conditions.getOrDefault(e.key(), java.util.List.of()), e.value()))))
                            .toArray(CompletableFuture[]::new)
                    );
                }
            );
    }

    private static <E> CompletableFuture<?> dumpValue(Path path, CachedOutput cache, DynamicOps<JsonElement> ops, Encoder<java.util.Optional<net.neoforged.neoforge.common.conditions.WithConditions<E>>> codec, java.util.Optional<net.neoforged.neoforge.common.conditions.WithConditions<E>> value) {
        return codec.encodeStart(ops, value)
            .mapOrElse(
                result -> DataProvider.saveStable(cache, result, path),
                error -> CompletableFuture.failedFuture(new IllegalStateException("Couldn't generate file '" + path + "': " + error.message()))
            );
    }

    @Override
    public String getName() {
        return "Registries";
    }
}
