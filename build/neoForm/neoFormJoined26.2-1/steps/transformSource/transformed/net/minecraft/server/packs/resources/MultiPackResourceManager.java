package net.minecraft.server.packs.resources;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class MultiPackResourceManager implements CloseableResourceManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<String, FallbackResourceManager> namespacedManagers;
    private final List<PackResources> packs;

    public MultiPackResourceManager(PackType type, List<PackResources> packs) {
        this.packs = List.copyOf(packs);
        Map<String, FallbackResourceManager> namespacedManagers = new HashMap<>();
        List<String> namespaces = packs.stream().flatMap(p -> p.getNamespaces(type).stream()).distinct().toList();

        for (PackResources pack : packs) {
            ResourceFilterSection filterSection = this.getPackFilterSection(pack);
            Set<String> providedNamespaces = pack.getNamespaces(type);
            // Neo: Fix MC-271761 by properly coupling namespace and path checks
            Predicate<Identifier> pathFilter = filterSection != null ? filterSection::isFiltered : null;

            for (String namespace : namespaces) {
                boolean packContainsNamespace = providedNamespaces.contains(namespace);
                boolean filterMatchesNamespace = filterSection != null && filterSection.isNamespaceFiltered(namespace);
                if (packContainsNamespace || filterMatchesNamespace) {
                    FallbackResourceManager fallbackResourceManager = namespacedManagers.get(namespace);
                    if (fallbackResourceManager == null) {
                        fallbackResourceManager = new FallbackResourceManager(type, namespace);
                        namespacedManagers.put(namespace, fallbackResourceManager);
                    }

                    if (packContainsNamespace && filterMatchesNamespace) {
                        fallbackResourceManager.push(pack, pathFilter);
                    } else if (packContainsNamespace) {
                        fallbackResourceManager.push(pack);
                    } else {
                        fallbackResourceManager.pushFilterOnly(pack.packId(), pathFilter);
                    }
                }
            }
        }

        this.namespacedManagers = namespacedManagers;
    }

    private @Nullable ResourceFilterSection getPackFilterSection(PackResources pack) {
        try {
            return pack.getMetadataSection(ResourceFilterSection.TYPE);
        } catch (Exception e) {
            if (!net.neoforged.neoforge.data.loading.DatagenModLoader.isRunningDataGen())// Neo: Only warn about malformed pack filters outside of datagen in case modders are replacing variables with gradle
            LOGGER.error("Failed to get filter section from pack {}", pack.packId());
            return null;
        }
    }

    @Override
    public Set<String> getNamespaces() {
        return this.namespacedManagers.keySet();
    }

    @Override
    public Optional<Resource> getResource(Identifier location) {
        ResourceManager pack = this.namespacedManagers.get(location.getNamespace());
        return pack != null ? pack.getResource(location) : Optional.empty();
    }

    @Override
    public List<Resource> getResourceStack(Identifier location) {
        ResourceManager pack = this.namespacedManagers.get(location.getNamespace());
        return pack != null ? pack.getResourceStack(location) : List.of();
    }

    @Override
    public Map<Identifier, Resource> listResources(String directory, Predicate<Identifier> filter) {
        checkTrailingDirectoryPath(directory);
        Map<Identifier, Resource> result = new TreeMap<>();

        for (FallbackResourceManager manager : this.namespacedManagers.values()) {
            result.putAll(manager.listResources(directory, filter));
        }

        return result;
    }

    @Override
    public Map<Identifier, List<Resource>> listResourceStacks(String directory, Predicate<Identifier> filter) {
        checkTrailingDirectoryPath(directory);
        Map<Identifier, List<Resource>> result = new TreeMap<>();

        for (FallbackResourceManager manager : this.namespacedManagers.values()) {
            result.putAll(manager.listResourceStacks(directory, filter));
        }

        return result;
    }

    private static void checkTrailingDirectoryPath(String directory) {
        if (directory.endsWith("/")) {
            throw new IllegalArgumentException("Trailing slash in path " + directory);
        }
    }

    @Override
    public Stream<PackResources> listPacks() {
        return this.packs.stream();
    }

    @Override
    public void close() {
        this.packs.forEach(PackResources::close);
    }
}
