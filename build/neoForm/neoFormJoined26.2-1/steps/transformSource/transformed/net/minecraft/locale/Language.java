package net.minecraft.locale;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.StringDecomposer;
import org.slf4j.Logger;

public abstract class Language {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final Pattern UNSUPPORTED_FORMAT_PATTERN = Pattern.compile("%(\\d+\\$)?[\\d.]*[df]");
    public static final String DEFAULT = "en_us";
    public static final Language DEFAULT_INSTANCE = loadDefault();
    private static volatile Language instance = DEFAULT_INSTANCE;

    private static Language loadDefault() {
        DeprecatedTranslationsInfo deprecatedInfo = DeprecatedTranslationsInfo.loadFromDefaultResource();
        Map<String, String> loadedData = new HashMap<>();
        BiConsumer<String, String> output = loadedData::put;
        Map<String, net.minecraft.network.chat.Component> componentMap = new java.util.HashMap<>();
        parseTranslations(output, componentMap::put, "/assets/minecraft/lang/en_us.json");
        deprecatedInfo.applyToMap(loadedData);
        final Map<String, String> storage = loadedData; // Neo keep the map mutable to make LanguageHook work
        net.neoforged.neoforge.server.LanguageHook.captureLanguageMap(loadedData, componentMap);
        return new Language() {
            @Override
            public String getOrDefault(String elementId, String defaultValue) {
                return storage.getOrDefault(elementId, defaultValue);
            }

            @Override
            public boolean has(String elementId) {
                return storage.containsKey(elementId);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return false;
            }

            @Override
            public FormattedCharSequence getVisualOrder(FormattedText logicalOrderText) {
                return output -> logicalOrderText.visit(
                        (style, contents) -> StringDecomposer.iterateFormatted(contents, style, output) ? Optional.empty() : FormattedText.STOP_ITERATION,
                        Style.EMPTY
                    )
                    .isPresent();
            }

            @Override
            public Map<String, String> getLanguageData() {
                return loadedData;
            }

            @Override
            public net.minecraft.network.chat.@org.jspecify.annotations.Nullable Component getComponent(String key) {
                return componentMap.get(key);
            }
        };
    }

    @Deprecated
    private static void parseTranslations(BiConsumer<String, String> output, String path) {
        parseTranslations(output, (key, value) -> {}, path);
    }

    private static void parseTranslations(BiConsumer<String, String> output, BiConsumer<String, net.minecraft.network.chat.Component> componentConsumer, String path) {
        try (InputStream stream = Language.class.getResourceAsStream(path)) {
            loadFromJson(stream, output, componentConsumer);
        } catch (IOException | JsonParseException e) {
            LOGGER.error("Couldn't read strings from {}", path, e);
        }
    }

    public static void loadFromJson(InputStream stream, BiConsumer<String, String> output) {
        loadFromJson(stream, output, (key, value) -> {});
    }

    public static void loadFromJson(InputStream stream, BiConsumer<String, String> output, BiConsumer<String, net.minecraft.network.chat.Component> componentConsumer) {
        JsonObject entries = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);

        for (Entry<String, JsonElement> entry : entries.entrySet()) {
            if (entry.getValue().isJsonArray() || entry.getValue().isJsonObject()) {
                var component = net.minecraft.network.chat.ComponentSerialization.CODEC
                    .parse(com.mojang.serialization.JsonOps.INSTANCE, entry.getValue())
                    .getOrThrow(msg -> new com.google.gson.JsonParseException("Error parsing translation for " + entry.getKey() + ": " + msg));

                output.accept(entry.getKey(), component.getString());
                componentConsumer.accept(entry.getKey(), component);

                continue;
            }

            String text = UNSUPPORTED_FORMAT_PATTERN.matcher(GsonHelper.convertToString(entry.getValue(), entry.getKey())).replaceAll("%$1s");
            output.accept(entry.getKey(), text);
        }
    }

    public static Language getInstance() {
        return instance;
    }

    public static void inject(Language language) {
        instance = language;
    }

    // Neo: All helpers methods below are injected by Neo to ease modder's usage of Language
    public Map<String, String> getLanguageData() { return com.google.common.collect.ImmutableMap.of(); }

    public net.minecraft.network.chat.@org.jspecify.annotations.Nullable Component getComponent(String key) {
        return null;
    }

    public String getOrDefault(String elementId) {
        return this.getOrDefault(elementId, elementId);
    }

    public abstract String getOrDefault(final String elementId, final String defaultValue);

    public abstract boolean has(final String elementId);

    public abstract boolean isDefaultRightToLeft();

    public abstract FormattedCharSequence getVisualOrder(final FormattedText logicalOrderText);

    public List<FormattedCharSequence> getVisualOrder(List<FormattedText> lines) {
        return lines.stream().map(this::getVisualOrder).collect(ImmutableList.toImmutableList());
    }
}
