package net.minecraft.world.level.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

import net.neoforged.fml.common.asm.enumextension.IExtensibleEnum;

public record BiomeSpecialEffects(
    int waterColor,
    Optional<Integer> foliageColorOverride,
    Optional<Integer> dryFoliageColorOverride,
    Optional<Integer> grassColorOverride,
    BiomeSpecialEffects.GrassColorModifier grassColorModifier
) {
    public static final Codec<BiomeSpecialEffects> CODEC = RecordCodecBuilder.create(
        i -> i.group(
                ExtraCodecs.STRING_RGB_COLOR.fieldOf("water_color").forGetter(BiomeSpecialEffects::waterColor),
                ExtraCodecs.STRING_RGB_COLOR.optionalFieldOf("foliage_color").forGetter(BiomeSpecialEffects::foliageColorOverride),
                ExtraCodecs.STRING_RGB_COLOR.optionalFieldOf("dry_foliage_color").forGetter(BiomeSpecialEffects::dryFoliageColorOverride),
                ExtraCodecs.STRING_RGB_COLOR.optionalFieldOf("grass_color").forGetter(BiomeSpecialEffects::grassColorOverride),
                BiomeSpecialEffects.GrassColorModifier.CODEC
                    .optionalFieldOf("grass_color_modifier", BiomeSpecialEffects.GrassColorModifier.NONE)
                    .forGetter(BiomeSpecialEffects::grassColorModifier)
            )
            .apply(i, BiomeSpecialEffects::new)
    );

    public static class Builder {
        protected OptionalInt waterColor = OptionalInt.empty();
        protected Optional<Integer> foliageColorOverride = Optional.empty();
        protected Optional<Integer> dryFoliageColorOverride = Optional.empty();
        protected Optional<Integer> grassColorOverride = Optional.empty();
        protected BiomeSpecialEffects.GrassColorModifier grassColorModifier = BiomeSpecialEffects.GrassColorModifier.NONE;

        public BiomeSpecialEffects.Builder waterColor(int waterColor) {
            this.waterColor = OptionalInt.of(waterColor);
            return this;
        }

        public BiomeSpecialEffects.Builder foliageColorOverride(int foliageColor) {
            this.foliageColorOverride = Optional.of(foliageColor);
            return this;
        }

        public BiomeSpecialEffects.Builder dryFoliageColorOverride(int dryFoliageColor) {
            this.dryFoliageColorOverride = Optional.of(dryFoliageColor);
            return this;
        }

        public BiomeSpecialEffects.Builder grassColorOverride(int grassColor) {
            this.grassColorOverride = Optional.of(grassColor);
            return this;
        }

        public BiomeSpecialEffects.Builder grassColorModifier(BiomeSpecialEffects.GrassColorModifier grassModifier) {
            this.grassColorModifier = grassModifier;
            return this;
        }

        public BiomeSpecialEffects build() {
            return new BiomeSpecialEffects(
                this.waterColor.orElseThrow(() -> new IllegalStateException("Missing 'water' color.")),
                this.foliageColorOverride,
                this.dryFoliageColorOverride,
                this.grassColorOverride,
                this.grassColorModifier
            );
        }
    }

    @net.neoforged.fml.common.asm.enumextension.NamedEnum
    @net.neoforged.fml.common.asm.enumextension.NetworkedEnum(net.neoforged.fml.common.asm.enumextension.NetworkedEnum.NetworkCheck.CLIENTBOUND)
    public enum GrassColorModifier implements StringRepresentable, IExtensibleEnum {
        NONE("none") {
            @Override
            public int modifyColor(double x, double z, int baseColor) {
                return baseColor;
            }
        },
        DARK_FOREST("dark_forest") {
            @Override
            public int modifyColor(double x, double z, int baseColor) {
                return ARGB.opaque((baseColor & 16711422) + 2634762 >> 1);
            }
        },
        SWAMP("swamp") {
            @Override
            public int modifyColor(double x, double z, int baseColor) {
                double groundValue = Biome.BIOME_INFO_NOISE.getValue(x * 0.0225, z * 0.0225, false);
                return groundValue < -0.1 ? -11766212 : -9801671;
            }
        };

        private final String name;
        private final ColorModifier delegate;
        public static final Codec<BiomeSpecialEffects.GrassColorModifier> CODEC = StringRepresentable.fromEnum(BiomeSpecialEffects.GrassColorModifier::values);

        public int modifyColor(double x, double z, int baseColor) {
            return delegate.modifyGrassColor(x, z, baseColor);
        }

        @net.neoforged.fml.common.asm.enumextension.ReservedConstructor
        GrassColorModifier(String name) {
            this.name = name;
            this.delegate = null;
        }

        GrassColorModifier(String name, ColorModifier delegate) {
            this.name = name;
            this.delegate = delegate;
        }

        public String getName() {
            return this.name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public static net.neoforged.fml.common.asm.enumextension.ExtensionInfo getExtensionInfo() {
            return net.neoforged.fml.common.asm.enumextension.ExtensionInfo.nonExtended(BiomeSpecialEffects.GrassColorModifier.class);
        }

        @FunctionalInterface
        public interface ColorModifier {
            int modifyGrassColor(double x, double z, int color);
        }
    }
}
