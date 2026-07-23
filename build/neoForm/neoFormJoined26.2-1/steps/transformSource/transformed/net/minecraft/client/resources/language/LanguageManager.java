package net.minecraft.client.resources.language;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.client.resources.metadata.language.LanguageMetadataSection;
import net.minecraft.locale.Language;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class LanguageManager implements ResourceManagerReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final LanguageInfo DEFAULT_LANGUAGE = new LanguageInfo("US", "English", false);
    private Map<String, LanguageInfo> languages = ImmutableMap.of("en_us", DEFAULT_LANGUAGE);
    private String currentCode;
    private final Consumer<ClientLanguage> reloadCallback;

    public LanguageManager(String languageCode, Consumer<ClientLanguage> reloadCallback) {
        setSelected(languageCode);
        this.reloadCallback = reloadCallback;
    }

    private static Map<String, LanguageInfo> extractLanguages(Stream<PackResources> resourcePacks) {
        Map<String, LanguageInfo> result = Maps.newHashMap();
        resourcePacks.forEach(resourcePack -> {
            try {
                LanguageMetadataSection languageMetadataSection = resourcePack.getMetadataSection(LanguageMetadataSection.TYPE);
                if (languageMetadataSection != null) {
                    languageMetadataSection.languages().forEach(result::putIfAbsent);
                }
            } catch (Exception e) {
                LOGGER.warn("Unable to parse language metadata section of resourcepack: {}", resourcePack.packId(), e);
            }
        });
        return ImmutableMap.copyOf(result);
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        this.languages = extractLanguages(resourceManager.listPacks());
        List<String> languageStack = new ArrayList<>(2);
        boolean defaultRightToLeft = DEFAULT_LANGUAGE.bidirectional();
        languageStack.add("en_us");
        if (!this.currentCode.equals("en_us")) {
            LanguageInfo currentLanguage = this.languages.get(this.currentCode);
            if (currentLanguage != null) {
                languageStack.add(this.currentCode);
                defaultRightToLeft = currentLanguage.bidirectional();
            }
        }

        ClientLanguage locale = ClientLanguage.loadFrom(resourceManager, languageStack, defaultRightToLeft);
        Language.inject(locale);
        this.reloadCallback.accept(locale);
    }

    private java.util.Locale javaLocale; // Forge: add locale information for modders
    public java.util.Locale getJavaLocale() { return javaLocale; }
    public void setSelected(String code) {
        this.currentCode = code;
        final String[] langSplit = code.split("_", 2);
        this.javaLocale = langSplit.length == 1 ? new java.util.Locale(langSplit[0]) : new java.util.Locale(langSplit[0], langSplit[1]);
    }

    public String getSelected() {
        return this.currentCode;
    }

    public SortedMap<String, LanguageInfo> getLanguages() {
        return new TreeMap<>(this.languages);
    }

    public @Nullable LanguageInfo getLanguage(String code) {
        return this.languages.get(code);
    }
}
