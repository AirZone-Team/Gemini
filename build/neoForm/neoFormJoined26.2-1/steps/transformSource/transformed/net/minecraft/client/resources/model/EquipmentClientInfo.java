package net.minecraft.client.resources.model;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import net.neoforged.fml.common.asm.enumextension.IExtensibleEnum;

@OnlyIn(Dist.CLIENT)
public record EquipmentClientInfo(Map<EquipmentClientInfo.LayerType, List<EquipmentClientInfo.Layer>> layers) {
    private static final Codec<List<EquipmentClientInfo.Layer>> LAYER_LIST_CODEC = ExtraCodecs.nonEmptyList(EquipmentClientInfo.Layer.CODEC.listOf());
    public static final Codec<EquipmentClientInfo> CODEC = RecordCodecBuilder.create(
        i -> i.group(
                ExtraCodecs.nonEmptyMap(Codec.unboundedMap(EquipmentClientInfo.LayerType.CODEC, LAYER_LIST_CODEC))
                    .fieldOf("layers")
                    .forGetter(EquipmentClientInfo::layers)
            )
            .apply(i, EquipmentClientInfo::new)
    );

    public static EquipmentClientInfo.Builder builder() {
        return new EquipmentClientInfo.Builder();
    }

    public List<EquipmentClientInfo.Layer> getLayers(EquipmentClientInfo.LayerType type) {
        return this.layers.getOrDefault(type, List.of());
    }

    public static class Builder {
        private final Map<EquipmentClientInfo.LayerType, List<EquipmentClientInfo.Layer>> layersByType = new EnumMap<>(EquipmentClientInfo.LayerType.class);

        private Builder() {
        }

        public EquipmentClientInfo.Builder addHumanoidLayers(Identifier textureId) {
            return this.addHumanoidLayers(textureId, false);
        }

        public EquipmentClientInfo.Builder addHumanoidLayers(Identifier textureId, boolean dyeable) {
            this.addLayers(EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, EquipmentClientInfo.Layer.leatherDyeable(textureId, dyeable));
            this.addMainHumanoidLayer(textureId, dyeable);
            return this;
        }

        public EquipmentClientInfo.Builder addMainHumanoidLayer(Identifier textureId, boolean dyeable) {
            this.addLayers(EquipmentClientInfo.LayerType.HUMANOID, EquipmentClientInfo.Layer.leatherDyeable(textureId, dyeable));
            this.addLayers(EquipmentClientInfo.LayerType.HUMANOID_BABY, EquipmentClientInfo.Layer.leatherDyeable(textureId, dyeable));
            return this;
        }

        public EquipmentClientInfo.Builder addLayers(EquipmentClientInfo.LayerType type, EquipmentClientInfo.Layer... layers) {
            Collections.addAll(this.layersByType.computeIfAbsent(type, t -> new ArrayList<>()), layers);
            return this;
        }

        public EquipmentClientInfo build() {
            return new EquipmentClientInfo(
                this.layersByType.entrySet().stream().collect(ImmutableMap.toImmutableMap(Entry::getKey, entry -> List.copyOf(entry.getValue())))
            );
        }
    }

    public record Dyeable(Optional<Integer> colorWhenUndyed) {
        public static final Codec<EquipmentClientInfo.Dyeable> CODEC = RecordCodecBuilder.create(
            i -> i.group(ExtraCodecs.RGB_COLOR_CODEC.optionalFieldOf("color_when_undyed").forGetter(EquipmentClientInfo.Dyeable::colorWhenUndyed))
                .apply(i, EquipmentClientInfo.Dyeable::new)
        );
    }

    public record Layer(Identifier textureId, Optional<EquipmentClientInfo.Dyeable> dyeable, boolean usePlayerTexture) {
        public static final Codec<EquipmentClientInfo.Layer> CODEC = RecordCodecBuilder.create(
            i -> i.group(
                    Identifier.CODEC.fieldOf("texture").forGetter(EquipmentClientInfo.Layer::textureId),
                    EquipmentClientInfo.Dyeable.CODEC.optionalFieldOf("dyeable").forGetter(EquipmentClientInfo.Layer::dyeable),
                    Codec.BOOL.optionalFieldOf("use_player_texture", false).forGetter(EquipmentClientInfo.Layer::usePlayerTexture)
                )
                .apply(i, EquipmentClientInfo.Layer::new)
        );

        public Layer(Identifier textureId) {
            this(textureId, Optional.empty(), false);
        }

        public static EquipmentClientInfo.Layer leatherDyeable(Identifier textureId, boolean dyeable) {
            return new EquipmentClientInfo.Layer(
                textureId, dyeable ? Optional.of(new EquipmentClientInfo.Dyeable(Optional.of(-6265536))) : Optional.empty(), false
            );
        }

        public static EquipmentClientInfo.Layer onlyIfDyed(Identifier textureId, boolean dyeable) {
            return new EquipmentClientInfo.Layer(textureId, dyeable ? Optional.of(new EquipmentClientInfo.Dyeable(Optional.empty())) : Optional.empty(), false);
        }

        public Identifier getTextureLocation(EquipmentClientInfo.LayerType type) {
            return this.textureId.withPath(path -> "textures/entity/equipment/" + type.getSerializedName() + "/" + path + ".png");
        }
    }

    @net.neoforged.fml.common.asm.enumextension.NamedEnum
    public enum LayerType implements StringRepresentable, IExtensibleEnum {
        HUMANOID("humanoid"),
        HUMANOID_LEGGINGS("humanoid_leggings"),
        HUMANOID_BABY("humanoid_baby"),
        WINGS("wings"),
        WOLF_BODY("wolf_body"),
        HORSE_BODY("horse_body"),
        LLAMA_BODY("llama_body"),
        PIG_SADDLE("pig_saddle"),
        STRIDER_SADDLE("strider_saddle"),
        CAMEL_SADDLE("camel_saddle"),
        CAMEL_HUSK_SADDLE("camel_husk_saddle"),
        HORSE_SADDLE("horse_saddle"),
        DONKEY_SADDLE("donkey_saddle"),
        MULE_SADDLE("mule_saddle"),
        ZOMBIE_HORSE_SADDLE("zombie_horse_saddle"),
        SKELETON_HORSE_SADDLE("skeleton_horse_saddle"),
        HAPPY_GHAST_BODY("happy_ghast_body"),
        NAUTILUS_SADDLE("nautilus_saddle"),
        NAUTILUS_BODY("nautilus_body");

        public static final Codec<EquipmentClientInfo.LayerType> CODEC = StringRepresentable.fromEnum(EquipmentClientInfo.LayerType::values);
        private final String id;

        LayerType(String id) {
            this.id = id.replaceAll(":", "/");
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }

        public String trimAssetPrefix() {
            return "trims/entity/" + this.id;
        }

        public static net.neoforged.fml.common.asm.enumextension.ExtensionInfo getExtensionInfo() {
            return net.neoforged.fml.common.asm.enumextension.ExtensionInfo.nonExtended(LayerType.class);
        }
    }
}
