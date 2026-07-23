package net.minecraft.data.tags;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.WorldPresetTags;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

public class WorldPresetTagsProvider extends TagsProvider<WorldPreset> {
    /** @deprecated Forge: Use the {@linkplain #WorldPresetTagsProvider(PackOutput, CompletableFuture, String) mod id variant} */
    @Deprecated
    public WorldPresetTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, Registries.WORLD_PRESET, lookupProvider);
    }
    public WorldPresetTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, String modId) {
        super(output, Registries.WORLD_PRESET, lookupProvider, modId);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        this.tag(WorldPresetTags.NORMAL)
            .add(WorldPresets.NORMAL)
            .add(WorldPresets.FLAT)
            .add(WorldPresets.LARGE_BIOMES)
            .add(WorldPresets.AMPLIFIED)
            .add(WorldPresets.SINGLE_BIOME_SURFACE);
        this.tag(WorldPresetTags.EXTENDED).addTag(WorldPresetTags.NORMAL).add(WorldPresets.DEBUG);
    }
}
