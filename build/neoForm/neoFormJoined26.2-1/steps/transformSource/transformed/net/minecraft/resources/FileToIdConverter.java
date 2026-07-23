package net.minecraft.resources;

import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public record FileToIdConverter(String prefix, String extension) {
    public static FileToIdConverter json(String prefix) {
        return new FileToIdConverter(prefix, ".json");
    }

    public static FileToIdConverter registry(ResourceKey<? extends Registry<?>> registry) {
        return json(Registries.elementsDirPath(registry));
    }

    public Identifier idToFile(Identifier id) {
        return id.withPath(this.prefix + "/" + id.getPath() + this.extension);
    }

    public Identifier fileToId(Identifier file) {
        String path = file.getPath();
        return file.withPath(path.substring(this.prefix.length() + 1, path.length() - this.extension.length()));
    }

    public boolean extensionMatches(Identifier id) {
        return id.getPath().endsWith(this.extension);
    }

    public Map<Identifier, Resource> listMatchingResources(ResourceManager manager) {
        return manager.listResources(this.prefix, this::extensionMatches);
    }

    public Map<Identifier, List<Resource>> listMatchingResourceStacks(ResourceManager manager) {
        return manager.listResourceStacks(this.prefix, this::extensionMatches);
    }

    /**
     * List all resources under the given namespace which match this converter
     *
     * @param manager   The resource manager to collect the resources from
     * @param namespace The namespace to search under
     * @return All resources from the given namespace which match this converter
     */
    public Map<Identifier, Resource> listMatchingResourcesFromNamespace(ResourceManager manager, String namespace) {
        return manager.listResources(this.prefix, path -> path.getNamespace().equals(namespace) && this.extensionMatches(path));
    }
    /**
     * List all resource stacks under the given namespace which match this converter
     *
     * @param manager   The resource manager to collect the resources from
     * @param namespace The namespace to search under
     * @return All resource stacks from the given namespace which match this converter
     */
    public Map<Identifier, List<Resource>> listMatchingResourceStacksFromNamespace(ResourceManager manager, String namespace) {
        return manager.listResourceStacks(this.prefix, path -> path.getNamespace().equals(namespace) && this.extensionMatches(path));
    }
}
