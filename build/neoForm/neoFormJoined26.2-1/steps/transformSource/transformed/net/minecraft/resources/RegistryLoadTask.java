package net.minecraft.resources;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Lifecycle;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.ConcurrentHolderGetter;
import net.minecraft.nbt.Tag;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StrictJsonParser;

public abstract class RegistryLoadTask<T> {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    // Neo: Used as a marker to skip registration elements that were disabled due to conditions
    private static final Exception SKIPPED_ELEMENT_MARKER = new Exception();
    private final Object registryWriteLock = new Object();
    protected final RegistryDataLoader.RegistryData<T> data;
    private final WritableRegistry<T> registry;
    protected final ConcurrentHolderGetter<T> concurrentRegistrationGetter;
    protected final Map<ResourceKey<?>, Exception> loadingErrors;
    private volatile boolean elementsRegistered;

    protected RegistryLoadTask(RegistryDataLoader.RegistryData<T> data, Lifecycle lifecycle, Map<ResourceKey<?>, Exception> loadingErrors) {
        var registryBuilder = new net.neoforged.neoforge.registries.RegistryBuilder<>(data.key());
        data.registryBuilderConsumer().accept(registryBuilder);

        this.data = data;
        this.registry = (WritableRegistry<T>) registryBuilder.disableRegistrationCheck().create();
        this.loadingErrors = loadingErrors;
        this.concurrentRegistrationGetter = new ConcurrentHolderGetter<>(this.registryWriteLock, this.registry.createRegistrationLookup());
    }

    protected ResourceKey<? extends Registry<T>> registryKey() {
        return this.registry.key();
    }

    protected Registry<T> readOnlyRegistry() {
        if (!this.elementsRegistered) {
            throw new IllegalStateException("Elements not registered");
        } else {
            return this.registry;
        }
    }

    public abstract CompletableFuture<?> load(RegistryOps.RegistryInfoLookup context, Executor executor);

    public RegistryOps.RegistryInfo<?> createRegistryInfo() {
        return new RegistryOps.RegistryInfo<>(this.registry, this.concurrentRegistrationGetter, this.registry.registryLifecycle());
    }

    protected void registerElements(Stream<RegistryLoadTask.PendingRegistration<T>> elements) {
        synchronized (this.registryWriteLock) {
            elements.forEach(
                element -> element.value
                    .ifLeft(value -> this.registry.register(element.key, (T)value, element.registrationInfo))
                    .ifRight(error -> {
                        if (error == SKIPPED_ELEMENT_MARKER) {
                            LOGGER.debug("Skipping loading registry entry {} as its conditions were not met", element.key);
                        } else {
                            this.loadingErrors.put(element.key, error);
                        }
                    })
            );
            this.elementsRegistered = true;
        }
    }

    protected void registerTags(Map<TagKey<T>, List<Holder<T>>> pendingTags) {
        synchronized (this.registryWriteLock) {
            this.registry.bindTags(pendingTags);
        }
    }

    public boolean freezeRegistry(Map<ResourceKey<?>, Exception> loadingErrors) {
        try {
            this.registry.freeze();
            return true;
        } catch (Exception e) {
            loadingErrors.put(this.registry.key(), e);
            return false;
        }
    }

    public Optional<Registry<T>> validateRegistry(Map<ResourceKey<?>, Exception> loadingErrors) {
        Map<ResourceKey<?>, Exception> registryErrors = new HashMap<>();
        this.data.validator().validate(this.registry, registryErrors);
        if (registryErrors.isEmpty()) {
            return Optional.of(this.registry);
        }

        loadingErrors.putAll(registryErrors);
        return Optional.empty();
    }

    protected record PendingRegistration<T>(ResourceKey<T> key, Either<T, Exception> value, RegistrationInfo registrationInfo) {
        public static <T> Either<T, Exception> loadFromResource(
            Decoder<T> elementDecoder, RegistryOps<JsonElement> ops, ResourceKey<T> elementKey, Resource thunk
        ) {
            try (Reader reader = thunk.openAsReader()) {
                JsonElement json = StrictJsonParser.parse(reader);

                // Neo: Allow a condition to skip the entire element, in which case we return a special marker error to skip it in the loader. Look for uses of SKIPPED_ELEMENT_MARKER
                var conditionalDecoder = net.neoforged.neoforge.common.conditions.ConditionalOps.createConditionalCodec(net.neoforged.neoforge.common.util.NeoForgeExtraCodecs.decodeOnly(elementDecoder));
                var candidate = conditionalDecoder.parse(ops, json).getOrThrow();
                if (candidate.isPresent()) {
                    return Either.left(candidate.get());
                } else {
                    return Either.right(SKIPPED_ELEMENT_MARKER);
                }
            } catch (Exception e) {
                return Either.right(
                    new IllegalStateException(String.format(Locale.ROOT, "Failed to parse %s from pack %s", elementKey.identifier(), thunk.sourcePackId()), e)
                );
            }
        }

        public static <T> Either<T, Exception> findAndLoadFromResource(
            Decoder<T> elementDecoder, RegistryOps<JsonElement> ops, ResourceKey<T> elementKey, FileToIdConverter converter, ResourceProvider resourceProvider
        ) {
            Identifier resourceId = converter.idToFile(elementKey.identifier());
            return resourceProvider.getResource(resourceId)
                .map(resource -> loadFromResource(elementDecoder, ops, elementKey, resource))
                .orElseGet(
                    () -> Either.right(
                        new IllegalStateException(String.format(Locale.ROOT, "Failed to find resource %s for element %s", resourceId, elementKey.identifier()))
                    )
                );
        }

        public static <T> Either<T, Exception> loadFromNetwork(Decoder<T> elementDecoder, RegistryOps<Tag> ops, ResourceKey<T> elementKey, Tag contents) {
            try {
                DataResult<T> parseResult = elementDecoder.parse(ops, contents);
                return Either.left(parseResult.getOrThrow());
            } catch (Exception e) {
                return Either.right(
                    new IllegalStateException(
                        String.format(Locale.ROOT, "Failed to parse value %s for key %s from server", contents, elementKey.identifier()), e
                    )
                );
            }
        }
    }
}
