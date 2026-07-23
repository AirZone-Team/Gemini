package net.minecraft.server.packs;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class FilePackResources extends AbstractPackResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final FilePackResources.SharedZipFileAccess zipFileAccess;
    private final String prefix;

    public FilePackResources(PackLocationInfo location, FilePackResources.SharedZipFileAccess zipFileAccess, String prefix) {
        super(location);
        this.zipFileAccess = zipFileAccess;
        this.prefix = prefix;
    }

    private static String getPathFromLocation(PackType type, Identifier location) {
        return String.format(Locale.ROOT, "%s/%s/%s", type.getDirectory(), location.getNamespace(), location.getPath());
    }

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
        return this.getResource(String.join("/", path));
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        return this.getResource(getPathFromLocation(type, location));
    }

    private String addPrefix(String path) {
        return this.prefix.isEmpty() ? path : this.prefix + "/" + path;
    }

    private @Nullable IoSupplier<InputStream> getResource(String path) {
        ZipFile zipFile = this.zipFileAccess.getOrCreateZipFile();
        if (zipFile == null) {
            return null;
        }

        ZipEntry entry = zipFile.getEntry(this.addPrefix(path));
        return entry == null ? null : IoSupplier.create(zipFile, entry);
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        ZipFile zipFile = this.zipFileAccess.getOrCreateZipFile();
        if (zipFile == null) {
            return Set.of();
        }

        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        Set<String> namespaces = Sets.newHashSet();
        String typePrefix = this.addPrefix(type.getDirectory() + "/");

        while (entries.hasMoreElements()) {
            ZipEntry zipEntry = entries.nextElement();
            String name = zipEntry.getName();
            String namespace = extractNamespace(typePrefix, name);
            if (!namespace.isEmpty()) {
                if (Identifier.isValidNamespace(namespace)) {
                    namespaces.add(namespace);
                } else {
                    LOGGER.warn("Non {} character in namespace {} in pack {}, ignoring", "[a-z0-9_.-]", namespace, this.zipFileAccess.file);
                }
            }
        }

        return namespaces;
    }

    @VisibleForTesting
    public static String extractNamespace(String prefix, String name) {
        if (!name.startsWith(prefix)) {
            return "";
        }

        int prefixLength = prefix.length();
        int firstPart = name.indexOf(47, prefixLength);
        return firstPart == -1 ? name.substring(prefixLength) : name.substring(prefixLength, firstPart);
    }

    @Override
    public void close() {
        this.zipFileAccess.close();
    }

    @Override
    public void listResources(PackType type, String namespace, String directory, PackResources.ResourceOutput output) {
        ZipFile zipFile = this.zipFileAccess.getOrCreateZipFile();
        if (zipFile != null) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            String root = this.addPrefix(type.getDirectory() + "/" + namespace + "/");
            String prefix = root + directory + "/";

            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                if (!zipEntry.isDirectory()) {
                    String name = zipEntry.getName();
                    if (name.startsWith(prefix)) {
                        String path = name.substring(root.length());
                        Identifier id = Identifier.tryBuild(namespace, path);
                        if (id != null) {
                            output.accept(id, IoSupplier.create(zipFile, zipEntry));
                        } else {
                            LOGGER.warn("Invalid path in datapack: {}:{}, ignoring", namespace, path);
                        }
                    }
                }
            }
        }
    }

    public static class FileResourcesSupplier implements Pack.ResourcesSupplier {
        private final File content;

        public FileResourcesSupplier(Path content) {
            this(content.toFile());
        }

        public FileResourcesSupplier(File content) {
            this.content = content;
        }

        @Override
        public PackResources openPrimary(PackLocationInfo location) {
            FilePackResources.SharedZipFileAccess fileAccess = new FilePackResources.SharedZipFileAccess(this.content);
            return new FilePackResources(location, fileAccess, "");
        }

        @Override
        public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
            FilePackResources.SharedZipFileAccess fileAccess = new FilePackResources.SharedZipFileAccess(this.content);
            PackResources primary = new FilePackResources(location, fileAccess, "");
            List<String> overlays = metadata.overlays();
            if (overlays.isEmpty()) {
                return primary;
            }

            List<PackResources> overlayResources = new ArrayList<>(overlays.size());

            for (String overlay : overlays) {
                overlayResources.add(new FilePackResources(location, fileAccess, overlay));
            }

            return new CompositePackResources(primary, overlayResources);
        }
    }

    public static class SharedZipFileAccess implements AutoCloseable {
        private final File file;
        private @Nullable ZipFile zipFile;
        private boolean failedToLoad;

        public SharedZipFileAccess(File file) {
            this.file = file;
        }

        private @Nullable ZipFile getOrCreateZipFile() {
            if (this.failedToLoad) {
                return null;
            }

            if (this.zipFile == null) {
                try {
                    this.zipFile = new ZipFile(this.file);
                } catch (IOException e) {
                    FilePackResources.LOGGER.error("Failed to open pack {}", this.file, e);
                    this.failedToLoad = true;
                    return null;
                }
            }

            return this.zipFile;
        }

        @Override
        public void close() {
            if (this.zipFile != null) {
                IOUtils.closeQuietly(this.zipFile);
                this.zipFile = null;
            }
        }

        @Override
        protected void finalize() throws Throwable {
            this.close();
            super.finalize();
        }
    }
}
