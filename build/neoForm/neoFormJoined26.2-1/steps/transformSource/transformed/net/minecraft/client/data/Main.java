package net.minecraft.client.data;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraft.SharedConstants;
import net.minecraft.SuppressForbidden;
import net.minecraft.client.ClientBootstrap;
import net.minecraft.client.data.models.EquipmentAssetProvider;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.WaypointStyleProvider;
import net.minecraft.data.DataGenerator;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class Main {
    @SuppressForbidden(reason = "System.out needed before bootstrap")
    public static void main(String[] args) throws IOException {
        SharedConstants.tryDetectVersion();
        OptionParser parser = new OptionParser();
        OptionSpec<Void> helpOption = parser.accepts("help", "Show the help menu").forHelp();
        OptionSpec<Void> clientOption = parser.accepts("client", "Include client generators");
        OptionSpec<Void> allOption = parser.accepts("all", "Include all generators");
        OptionSpec<String> outputOption = parser.accepts("output", "Output folder").withRequiredArg().defaultsTo("generated");
        OptionSpec<String> existing = parser.accepts("existing", "Existing resource packs that generated resources can reference").withRequiredArg();
        OptionSpec<String> mod = parser.accepts("mod", "A modid to dump").withRequiredArg().withValuesSeparatedBy(",");
        OptionSpec<Void> flat = parser.accepts("flat", "Do not append modid prefix to output directory when generating for multiple mods");
        OptionSpec<String> assetIndex = parser.accepts("assetIndex").withRequiredArg();
        OptionSpec<java.io.File> assetsDir = parser.accepts("assetsDir").withRequiredArg().ofType(java.io.File.class);
        OptionSpec<Void> validateSpec = parser.accepts("validate", "Validate inputs");
        OptionSpec<Void> uncachedSpec = parser.accepts("uncached", "Disables cached data generation");
        OptionSet optionSet = parser.parse(args);
        if (!optionSet.has(helpOption) && optionSet.hasOptions()) {
            Path output = Paths.get(outputOption.value(optionSet));
            boolean allOptions = optionSet.has(allOption);
            boolean client = allOptions || optionSet.has(clientOption);
            java.util.Collection<Path> existingPacks = optionSet.valuesOf(existing).stream().map(Paths::get).toList();
            java.util.Set<String> mods = new java.util.HashSet<>(optionSet.valuesOf(mod));
            boolean isFlat = mods.isEmpty() || optionSet.has(flat);
            boolean validate = optionSet.has(validateSpec);
            boolean uncached = optionSet.has(uncachedSpec);
            DataGenerator generator = uncached ? new DataGenerator.Uncached(isFlat ? output : output.resolve("minecraft")) : new DataGenerator.Cached(isFlat ? output : output.resolve("minecraft"), SharedConstants.getCurrentVersion(), true);
            if (mods.contains("minecraft") || mods.isEmpty()) {
            addClientProviders(generator, client);
            Util.shutdownExecutors();
            }
            java.io.File assetsDirValue = optionSet.valueOf(assetsDir);
            String assetIndexValue = optionSet.valueOf(assetIndex);
            net.neoforged.neoforge.data.loading.DatagenModLoader.begin(mods, output, java.util.List.of(), existingPacks, false, false, validate, isFlat, uncached, () -> {
                ClientBootstrap.bootstrap();
                net.neoforged.neoforge.client.ClientHooks.initClientRegistries();
            }, net.neoforged.neoforge.data.event.GatherDataEvent.Client::new, generator, packConsumer -> {
                if (assetsDirValue != null && assetIndexValue != null)
                    packConsumer.accept(net.minecraft.client.resources.ClientPackSource.createVanillaPackSource(net.minecraft.client.resources.IndexedAssetSource.createIndexFs(assetsDirValue.toPath(), assetIndexValue)));
            });
        } else {
            parser.printHelpOn(System.out);
        }
    }

    public static void addClientProviders(DataGenerator generator, boolean client) {
        DataGenerator.PackGenerator clientVanillaPack = generator.getVanillaPack(client);
        clientVanillaPack.addProvider(ModelProvider::new);
        clientVanillaPack.addProvider(EquipmentAssetProvider::new);
        clientVanillaPack.addProvider(WaypointStyleProvider::new);
        clientVanillaPack.addProvider(AtlasProvider::new);
    }
}
