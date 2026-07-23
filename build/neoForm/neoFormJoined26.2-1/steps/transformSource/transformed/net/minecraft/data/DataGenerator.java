package net.minecraft.data;

import com.google.common.base.Stopwatch;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.minecraft.WorldVersion;
import net.minecraft.server.Bootstrap;
import org.slf4j.Logger;

public abstract class DataGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();
    protected final PackOutput vanillaPackOutput;
    protected final Set<String> allProviderIds = new HashSet<>();
    protected final Map<String, DataProvider> providersToRun = new LinkedHashMap<>();
    private final Map<String, DataProvider> providersView = java.util.Collections.unmodifiableMap(this.providersToRun);

    public DataGenerator(Path output) {
        this.vanillaPackOutput = new PackOutput(output);
    }

    public abstract void run() throws IOException;

    public DataGenerator.PackGenerator getVanillaPack(boolean toRun) {
        return new DataGenerator.PackGenerator(toRun, "vanilla", this.vanillaPackOutput);
    }

    public DataGenerator.PackGenerator getBuiltinDatapack(boolean toRun, String packId) {
        Path packOutputDir = this.vanillaPackOutput.getOutputFolder(PackOutput.Target.DATA_PACK).resolve("minecraft").resolve("datapacks").resolve(packId);
        return new DataGenerator.PackGenerator(toRun, packId, new PackOutput(packOutputDir));
    }

    public PackGenerator getBuiltinDatapack(boolean run, String namespace, String path) {
        var packPath = vanillaPackOutput.getOutputFolder(PackOutput.Target.DATA_PACK).resolve(namespace).resolve("datapacks").resolve(path);
        return new PackGenerator(run, namespace + '_' + path, new PackOutput(packPath));
    }

    public Map<String, DataProvider> getProvidersView() {
         return this.providersView;
    }

    public PackOutput getPackOutput() {
        return this.vanillaPackOutput;
    }

    public PackOutput getPackOutput(String path) {
        return new PackOutput(vanillaPackOutput.getOutputFolder().resolve(path));
    }

    public PackGenerator getPackGenerator(boolean run, String providerPrefix, String path) {
        return new PackGenerator(run, providerPrefix, getPackOutput(path));
    }

    public <T extends DataProvider> T addProvider(boolean run, DataProvider.Factory<T> factory) {
        return addProvider(run, factory.create(this.vanillaPackOutput));
    }

    public <T extends DataProvider> T addProvider(boolean run, T provider) {
        String id = provider.getName();

        if (!DataGenerator.this.allProviderIds.add(id))
            throw new IllegalStateException("Duplicate provider: " + id);

        if (run)
            DataGenerator.this.providersToRun.put(id, provider);

        return provider;
    }

    public void merge(DataGenerator other) {
        other.providersToRun.forEach((id, provider) -> {
            if(!allProviderIds.add(id))
                throw new IllegalStateException("Duplicate provider: " + id);

            providersToRun.put(id, provider);
        });

        other.providersToRun.clear();
        other.allProviderIds.clear();
    }

    static {
        Bootstrap.bootStrap();
    }

    public static class Cached extends DataGenerator {
        private final Path rootOutputFolder;
        private final WorldVersion version;
        private final boolean alwaysGenerate;

        public Cached(Path output, WorldVersion version, boolean alwaysGenerate) {
            super(output);
            this.rootOutputFolder = output;
            this.alwaysGenerate = alwaysGenerate;
            this.version = version;
        }

        @Override
        public void run() throws IOException {
            HashCache cache = new HashCache(this.rootOutputFolder, this.allProviderIds, this.version);
            Stopwatch totalTime = Stopwatch.createStarted();
            Stopwatch stopwatch = Stopwatch.createUnstarted();
            this.providersToRun.forEach((providerId, provider) -> {
                if (!this.alwaysGenerate && !cache.shouldRunInThisVersion(providerId)) {
                    DataGenerator.LOGGER.debug("Generator {} already run for version {}", providerId, this.version.name());
                } else {
                    DataGenerator.LOGGER.info("Starting provider: {}", providerId);
                    net.neoforged.fml.loading.progress.StartupNotificationManager.addModMessage("Generating: " + providerId);
                    stopwatch.start();
                    cache.applyUpdate(cache.generateUpdate(providerId, provider::run).join());
                    stopwatch.stop();
                    DataGenerator.LOGGER.info("{} finished after {} ms", providerId, stopwatch.elapsed(TimeUnit.MILLISECONDS));
                    stopwatch.reset();
                }
            });
            DataGenerator.LOGGER.info("All providers took: {} ms", totalTime.elapsed(TimeUnit.MILLISECONDS));
            cache.purgeStaleAndWrite();
        }
    }

    public class PackGenerator {
        private final boolean toRun;
        private final String providerPrefix;
        private final PackOutput output;

        private PackGenerator(boolean toRun, String providerPrefix, PackOutput output) {
            this.toRun = toRun;
            this.providerPrefix = providerPrefix;
            this.output = output;
        }

        public <T extends DataProvider> T addProvider(DataProvider.Factory<T> factory) {
            T provider = factory.create(this.output);
            String providerId = this.providerPrefix + "/" + provider.getName();
            if (!DataGenerator.this.allProviderIds.add(providerId)) {
                throw new IllegalStateException("Duplicate provider: " + providerId);
            }

            if (this.toRun) {
                DataGenerator.this.providersToRun.put(providerId, provider);
            }

            return provider;
        }
    }

    public static class Uncached extends DataGenerator {
        public Uncached(Path output) {
            super(output);
        }

        @Override
        public void run() throws IOException {
            Stopwatch totalTime = Stopwatch.createStarted();
            Stopwatch stopwatch = Stopwatch.createUnstarted();
            this.providersToRun.forEach((providerId, provider) -> {
                DataGenerator.LOGGER.info("Starting uncached provider: {}", providerId);
                net.neoforged.fml.loading.progress.StartupNotificationManager.addModMessage("Generating: " + providerId);
                stopwatch.start();
                provider.run(CachedOutput.NO_CACHE).join();
                stopwatch.stop();
                DataGenerator.LOGGER.info("{} finished after {} ms", providerId, stopwatch.elapsed(TimeUnit.MILLISECONDS));
                stopwatch.reset();
            });
            DataGenerator.LOGGER.info("All providers took: {} ms", totalTime.elapsed(TimeUnit.MILLISECONDS));
        }
    }
}
