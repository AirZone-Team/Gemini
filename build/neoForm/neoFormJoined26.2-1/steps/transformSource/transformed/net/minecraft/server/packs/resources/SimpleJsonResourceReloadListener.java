package net.minecraft.server.packs.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

public abstract class SimpleJsonResourceReloadListener<T> extends SimplePreparableReloadListener<Map<Identifier, T>> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final DynamicOps<JsonElement> ops;
    private final Codec<T> codec;
    private final FileToIdConverter lister;

    protected SimpleJsonResourceReloadListener(HolderLookup.Provider registries, Codec<T> codec, ResourceKey<? extends Registry<T>> registryKey) {
        this(registries.createSerializationContext(JsonOps.INSTANCE), codec, FileToIdConverter.registry(registryKey));
    }

    protected SimpleJsonResourceReloadListener(Codec<T> codec, FileToIdConverter lister) {
        this(JsonOps.INSTANCE, codec, lister);
    }

    private SimpleJsonResourceReloadListener(DynamicOps<JsonElement> ops, Codec<T> codec, FileToIdConverter lister) {
        this.ops = ops;
        this.codec = codec;
        this.lister = lister;
    }

    protected Map<Identifier, T> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<Identifier, T> result = new HashMap<>();
        // Neo: add condition context
        scanDirectory(manager, this.lister, this.makeConditionalOps(this.ops), this.codec, result);
        return result;
    }

    public static <T> void scanDirectory(
        ResourceManager manager, ResourceKey<? extends Registry<T>> registryKey, DynamicOps<JsonElement> ops, Codec<T> codec, Map<Identifier, T> result
    ) {
        scanDirectory(manager, FileToIdConverter.registry(registryKey), ops, codec, result);
    }

    public static <T> void scanDirectoryWithOptionalValues(
            ResourceManager manager,
            ResourceKey<? extends Registry<T>> registryKey,
            DynamicOps<JsonElement> ops,
            Codec<java.util.Optional<T>> codec,
            Map<Identifier, java.util.Optional<T>> result
    ) {
        scanDirectory(manager, FileToIdConverter.registry(registryKey), ops, codec, result);
    }

    public static <T> void scanDirectory(
        ResourceManager manager, FileToIdConverter lister, DynamicOps<JsonElement> ops, Codec<T> codec, Map<Identifier, T> result
    ) {
        var conditionalCodec = net.neoforged.neoforge.common.conditions.ConditionalOps.createConditionalCodec(codec);
        for (Entry<Identifier, Resource> entry : lister.listMatchingResources(manager).entrySet()) {
            Identifier location = entry.getKey();
            Identifier id = lister.fileToId(location);

            try (Reader reader = entry.getValue().openAsReader()) {
                conditionalCodec.parse(ops, com.google.gson.JsonParser.parseReader(reader)).ifSuccess(parsed -> {
                    if (parsed.isEmpty()) {
                        LOGGER.debug("Skipping loading data file '{}' from '{}' as its conditions were not met", id, location);
                    } else if (result.putIfAbsent(id, parsed.get()) != null) {
                        throw new IllegalStateException("Duplicate data file ignored with ID " + id);
                    }
                }).ifError(error -> LOGGER.error("Couldn't parse data file '{}' from '{}': {}", id, location, error));
            } catch (JsonParseException | IllegalArgumentException | IOException e) {
                LOGGER.error("Couldn't parse data file '{}' from '{}'", id, location, e);
            }
        }
    }

    /// Neo: Overload of [#scanDirectory(ResourceManager, FileToIdConverter, DynamicOps, Codec, Map)] with support
    /// for modifying the raw JSON in bulk before it is deserialized.
    ///
    /// Implemented as a copy to ensure resources not using the modification support still use the shorter path
    /// without the intermediate storage.
    public static <T> void scanDirectoryWithModifier(
        ResourceManager manager, FileToIdConverter lister, DynamicOps<JsonElement> ops, Codec<T> codec, Map<Identifier, T> result, java.util.function.Consumer<Map<Identifier, JsonElement>> jsonConsumer
    ) {
        Map<Identifier, JsonElement> jsons = new HashMap<>();
        for (Entry<Identifier, Resource> entry : lister.listMatchingResources(manager).entrySet()) {
            Identifier location = entry.getKey();
            Identifier id = lister.fileToId(location);

            try (Reader reader = entry.getValue().openAsReader()) {
                jsons.put(id, com.google.gson.JsonParser.parseReader(reader));
            } catch (IllegalArgumentException | IOException | JsonParseException var14) {
                LOGGER.error("Couldn't parse data file '{}' from '{}'", id, location, var14);
            }
        }

        jsonConsumer.accept(jsons);

        var conditionalCodec = net.neoforged.neoforge.common.conditions.ConditionalOps.createConditionalCodec(codec);
        for (Entry<Identifier, JsonElement> entry : jsons.entrySet()) {
            Identifier id = entry.getKey();
            JsonElement json = entry.getValue();

            conditionalCodec.parse(ops, json).ifSuccess(parsed -> {
                if (parsed.isEmpty()) {
                    LOGGER.debug("Skipping loading data file '{}' as its conditions were not met", id);
                } else if (result.putIfAbsent(id, parsed.get()) != null) {
                    throw new IllegalStateException("Duplicate data file ignored with ID " + id);
                }
            }).ifError(error -> LOGGER.error("Couldn't parse data file '{}': {}", id, error));
        }
    }

    protected Identifier getPreparedPath(Identifier rl) {
        return this.lister.idToFile(rl);
    }
}
