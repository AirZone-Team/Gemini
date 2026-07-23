package net.minecraft.client.resources.language;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.locale.DeprecatedTranslationsInfo;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class ClientLanguage extends Language {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<String, String> storage;
    private final Map<String, net.minecraft.network.chat.Component> componentStorage;
    private final boolean defaultRightToLeft;

    @Deprecated
    private ClientLanguage(Map<String, String> storage, boolean defaultRightToLeft) {
        this(storage, defaultRightToLeft, Map.of());
    }

    private ClientLanguage(Map<String, String> storage, boolean defaultRightToLeft, Map<String, net.minecraft.network.chat.Component> componentStorage) {
        this.storage = storage;
        this.defaultRightToLeft = defaultRightToLeft;
        this.componentStorage = componentStorage;
    }

    public static ClientLanguage loadFrom(ResourceManager resourceManager, List<String> languageStack, boolean defaultRightToLeft) {
        Map<String, String> translations = new HashMap<>();
        Map<String, net.minecraft.network.chat.Component> componentMap = new HashMap<>();

        for (String languageCode : languageStack) {
            String path = String.format(Locale.ROOT, "lang/%s.json", languageCode);
            translations.putAll(net.neoforged.fml.i18n.I18nManager.loadTranslations(languageCode));

            for (String namespace : resourceManager.getNamespaces()) {
                try {
                    Identifier location = Identifier.fromNamespaceAndPath(namespace, path);
                    appendFrom(languageCode, resourceManager.getResourceStack(location), translations, componentMap);
                } catch (Exception e) {
                    LOGGER.warn("Skipped language file: {}:{} ({})", namespace, path, e.toString());
                }
            }
        }

        DeprecatedTranslationsInfo.loadFromDefaultResource().applyToMap(translations);
        return new ClientLanguage(Map.copyOf(translations), defaultRightToLeft, Map.copyOf(componentMap));
    }

    @Deprecated
    private static void appendFrom(String languageCode, List<Resource> resources, Map<String, String> translations) {
        appendFrom(languageCode, resources, translations, new java.util.HashMap<>());
    }

    private static void appendFrom(String languageCode, List<Resource> resources, Map<String, String> translations, Map<String, net.minecraft.network.chat.Component> componentMap) {
        for (Resource resource : resources) {
            try (InputStream inputStream = resource.open()) {
                Language.loadFromJson(inputStream, translations::put, componentMap::put);
            } catch (IOException e) {
                LOGGER.warn("Failed to load translations for {} from pack {}", languageCode, resource.sourcePackId(), e);
            }
        }
    }

    @Override
    public String getOrDefault(String key, String defaultValue) {
        return this.storage.getOrDefault(key, defaultValue);
    }

    @Override
    public boolean has(String key) {
        return this.storage.containsKey(key);
    }

    @Override
    public boolean isDefaultRightToLeft() {
        return this.defaultRightToLeft;
    }

    @Override
    public FormattedCharSequence getVisualOrder(FormattedText logicalOrderText) {
        return FormattedBidiReorder.reorder(logicalOrderText, this.defaultRightToLeft);
    }

    @Override
    public Map<String, String> getLanguageData() {
        return storage;
    }

    @Override
    public net.minecraft.network.chat.@org.jspecify.annotations.Nullable Component getComponent(String key) {
        return componentStorage.get(key);
    }
}
