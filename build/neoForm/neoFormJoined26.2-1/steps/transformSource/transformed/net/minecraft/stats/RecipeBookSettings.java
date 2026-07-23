package net.minecraft.stats;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.function.UnaryOperator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.inventory.RecipeBookType;

public final class RecipeBookSettings {
    // Neo: We need RegistryFriendlyByteBuf to detect the connection type for Vanilla client/server compatibility
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, RecipeBookSettings> STREAM_CODEC = StreamCodec.composite(
        RecipeBookSettings.TypeSettings.STREAM_CODEC,
        o -> o.crafting,
        RecipeBookSettings.TypeSettings.STREAM_CODEC,
        o -> o.furnace,
        RecipeBookSettings.TypeSettings.STREAM_CODEC,
        o -> o.blastFurnace,
        RecipeBookSettings.TypeSettings.STREAM_CODEC,
        o -> o.smoker,
        // Neo: Also send settings for modded recipe book types
        net.neoforged.neoforge.common.CommonHooks.MODDED_RECIPE_BOOK_TYPES_SETTINGS_STREAM_CODEC,
        settings -> settings.moddedSettings,
        RecipeBookSettings::new
    );
    public static final MapCodec<RecipeBookSettings> MAP_CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(
                RecipeBookSettings.TypeSettings.CRAFTING_MAP_CODEC.forGetter(o -> o.crafting),
                RecipeBookSettings.TypeSettings.FURNACE_MAP_CODEC.forGetter(o -> o.furnace),
                RecipeBookSettings.TypeSettings.BLAST_FURNACE_MAP_CODEC.forGetter(o -> o.blastFurnace),
                RecipeBookSettings.TypeSettings.SMOKER_MAP_CODEC.forGetter(o -> o.smoker)
                , net.neoforged.neoforge.common.CommonHooks.makeModdedRecipeBookTypesSettingsCodec().forGetter(settings -> settings.moddedSettings)
            )
            .apply(i, RecipeBookSettings::new)
    );
    private RecipeBookSettings.TypeSettings crafting;
    private RecipeBookSettings.TypeSettings furnace;
    private RecipeBookSettings.TypeSettings blastFurnace;
    private RecipeBookSettings.TypeSettings smoker;
    private final java.util.Map<RecipeBookType, RecipeBookSettings.TypeSettings> moddedSettings;

    public RecipeBookSettings() {
        this(
            RecipeBookSettings.TypeSettings.DEFAULT,
            RecipeBookSettings.TypeSettings.DEFAULT,
            RecipeBookSettings.TypeSettings.DEFAULT,
            RecipeBookSettings.TypeSettings.DEFAULT
        );
    }

    private RecipeBookSettings(
        RecipeBookSettings.TypeSettings crafting,
        RecipeBookSettings.TypeSettings furnace,
        RecipeBookSettings.TypeSettings blastFurnace,
        RecipeBookSettings.TypeSettings smoker
    ) {
        this(crafting, furnace, blastFurnace, smoker, new java.util.EnumMap<>(RecipeBookType.class));
    }

    private RecipeBookSettings(
            RecipeBookSettings.TypeSettings crafting,
            RecipeBookSettings.TypeSettings furnace,
            RecipeBookSettings.TypeSettings blastFurnace,
            RecipeBookSettings.TypeSettings smoker,
            java.util.Map<RecipeBookType, RecipeBookSettings.TypeSettings> moddedSettings
    ) {
        this.crafting = crafting;
        this.furnace = furnace;
        this.blastFurnace = blastFurnace;
        this.smoker = smoker;
        this.moddedSettings = moddedSettings;
    }

    @VisibleForTesting
    public RecipeBookSettings.TypeSettings getSettings(RecipeBookType type) {
        return switch (type) {
            case CRAFTING -> this.crafting;
            case FURNACE -> this.furnace;
            case BLAST_FURNACE -> this.blastFurnace;
            case SMOKER -> this.smoker;
            default -> this.moddedSettings.getOrDefault(type, RecipeBookSettings.TypeSettings.DEFAULT);
        };
    }

    private void updateSettings(RecipeBookType recipeBookType, UnaryOperator<RecipeBookSettings.TypeSettings> operator) {
        switch (recipeBookType) {
            case CRAFTING:
                this.crafting = operator.apply(this.crafting);
                break;
            case FURNACE:
                this.furnace = operator.apply(this.furnace);
                break;
            case BLAST_FURNACE:
                this.blastFurnace = operator.apply(this.blastFurnace);
                break;
            case SMOKER:
                this.smoker = operator.apply(this.smoker);
                break;
            default:
                this.moddedSettings.put(recipeBookType, operator.apply(this.moddedSettings.getOrDefault(recipeBookType, RecipeBookSettings.TypeSettings.DEFAULT)));
        }
    }

    public boolean isOpen(RecipeBookType type) {
        return this.getSettings(type).open;
    }

    public void setOpen(RecipeBookType type, boolean open) {
        this.updateSettings(type, s -> s.setOpen(open));
    }

    public boolean isFiltering(RecipeBookType type) {
        return this.getSettings(type).filtering;
    }

    public void setFiltering(RecipeBookType type, boolean filtering) {
        this.updateSettings(type, s -> s.setFiltering(filtering));
    }

    public RecipeBookSettings copy() {
        return new RecipeBookSettings(this.crafting, this.furnace, this.blastFurnace, this.smoker, new java.util.EnumMap<>(this.moddedSettings));
    }

    public void replaceFrom(RecipeBookSettings other) {
        this.crafting = other.crafting;
        this.furnace = other.furnace;
        this.blastFurnace = other.blastFurnace;
        this.smoker = other.smoker;
        this.moddedSettings.clear();
        this.moddedSettings.putAll(other.moddedSettings);
    }

    public record TypeSettings(boolean open, boolean filtering) {
        public static final RecipeBookSettings.TypeSettings DEFAULT = new RecipeBookSettings.TypeSettings(false, false);
        public static final MapCodec<RecipeBookSettings.TypeSettings> CRAFTING_MAP_CODEC = codec("isGuiOpen", "isFilteringCraftable");
        public static final MapCodec<RecipeBookSettings.TypeSettings> FURNACE_MAP_CODEC = codec("isFurnaceGuiOpen", "isFurnaceFilteringCraftable");
        public static final MapCodec<RecipeBookSettings.TypeSettings> BLAST_FURNACE_MAP_CODEC = codec(
            "isBlastingFurnaceGuiOpen", "isBlastingFurnaceFilteringCraftable"
        );
        public static final MapCodec<RecipeBookSettings.TypeSettings> SMOKER_MAP_CODEC = codec("isSmokerGuiOpen", "isSmokerFilteringCraftable");
        public static final StreamCodec<ByteBuf, RecipeBookSettings.TypeSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            RecipeBookSettings.TypeSettings::open,
            ByteBufCodecs.BOOL,
            RecipeBookSettings.TypeSettings::filtering,
            RecipeBookSettings.TypeSettings::new
        );

        @Override
        public String toString() {
            return "[open=" + this.open + ", filtering=" + this.filtering + "]";
        }

        public RecipeBookSettings.TypeSettings setOpen(boolean open) {
            return new RecipeBookSettings.TypeSettings(open, this.filtering);
        }

        public RecipeBookSettings.TypeSettings setFiltering(boolean filtering) {
            return new RecipeBookSettings.TypeSettings(this.open, filtering);
        }

        public static MapCodec<RecipeBookSettings.TypeSettings> codec(String openFieldName, String filteringFieldName) {
            return RecordCodecBuilder.mapCodec(
                i -> i.group(
                        Codec.BOOL.optionalFieldOf(openFieldName, false).forGetter(RecipeBookSettings.TypeSettings::open),
                        Codec.BOOL.optionalFieldOf(filteringFieldName, false).forGetter(RecipeBookSettings.TypeSettings::filtering)
                    )
                    .apply(i, RecipeBookSettings.TypeSettings::new)
            );
        }
    }
}
