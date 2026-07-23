package net.minecraft.data;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Util;
import org.slf4j.Logger;

public interface DataProvider {
    /**
     * Neo: Allows changing the indentation width used by {@link #saveStable}.
     */
    java.util.concurrent.atomic.AtomicInteger INDENT_WIDTH = new java.util.concurrent.atomic.AtomicInteger(2);

    ToIntFunction<String> FIXED_ORDER_FIELDS = Util.make(new Object2IntOpenHashMap<>(), m -> {
        // Neo: conditions go first
        m.put("neoforge:conditions", -1);
        m.put("neoforge:definition_type", 0);
        m.put("neoforge:ingredient_type", 0);
        m.put("type", 0);
        m.put("parent", 1);
        m.defaultReturnValue(2);
    });
    Comparator<String> KEY_COMPARATOR = Comparator.comparingInt(FIXED_ORDER_FIELDS).thenComparing(e -> (String)e);
    Logger LOGGER = LogUtils.getLogger();

    CompletableFuture<?> run(CachedOutput cache);

    String getName();

    static <T> CompletableFuture<?> saveAll(CachedOutput cache, Codec<T> codec, PackOutput.PathProvider pathProvider, Map<Identifier, T> entries) {
        return saveAll(cache, codec, pathProvider::json, entries);
    }

    static <T, E> CompletableFuture<?> saveAll(CachedOutput cache, Codec<E> codec, Function<T, Path> pathGetter, Map<T, E> contents) {
        return saveAll(cache, e -> codec.encodeStart(JsonOps.INSTANCE, (E)e).getOrThrow(), pathGetter, contents);
    }

    static <T, E> CompletableFuture<?> saveAll(CachedOutput cache, Function<E, JsonElement> serializer, Function<T, Path> pathGetter, Map<T, E> contents) {
        return CompletableFuture.allOf(contents.entrySet().stream().map(entry -> {
            Path path = pathGetter.apply(entry.getKey());
            JsonElement json = serializer.apply(entry.getValue());
            return saveStable(cache, json, path);
        }).toArray(CompletableFuture[]::new));
    }

    static <T> CompletableFuture<?> saveStable(CachedOutput cache, HolderLookup.Provider registries, Codec<T> codec, T value, Path path) {
        RegistryOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
        return saveStable(cache, ops, codec, value, path);
    }

    static <T> CompletableFuture<?> saveStable(CachedOutput cache, Codec<T> codec, T value, Path path) {
        return saveStable(cache, JsonOps.INSTANCE, codec, value, path);
    }

    private static <T> CompletableFuture<?> saveStable(CachedOutput cache, DynamicOps<JsonElement> ops, Codec<T> codec, T value, Path path) {
        JsonElement json = codec.encodeStart(ops, value).getOrThrow();
        return saveStable(cache, json, path);
    }

    static CompletableFuture<?> saveStable(CachedOutput cache, JsonElement root, Path path) {
        return CompletableFuture.runAsync(() -> {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                HashingOutputStream hashedBytes = new HashingOutputStream(Hashing.sha1(), bytes);

                try (JsonWriter jsonWriter = new JsonWriter(new OutputStreamWriter(hashedBytes, StandardCharsets.UTF_8))) {
                    jsonWriter.setSerializeNulls(false);
                    jsonWriter.setIndent(" ".repeat(java.lang.Math.max(0, INDENT_WIDTH.get()))); // Neo: Allow changing the indent width without needing to mixin this lambda.
                    GsonHelper.writeValue(jsonWriter, root, KEY_COMPARATOR);
                }

                cache.writeIfNeeded(path, bytes.toByteArray(), hashedBytes.hash());
            } catch (IOException e) {
                LOGGER.error("Failed to save file to {}", path, e);
            }
        }, Util.backgroundExecutor().forName("saveStable"));
    }

    @FunctionalInterface
    interface Factory<T extends DataProvider> {
        T create(PackOutput output);
    }
}
