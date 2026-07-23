package net.minecraft.world.attribute;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.timeline.Timeline;
import org.jspecify.annotations.Nullable;

public class EnvironmentAttributeSystem implements EnvironmentAttributeReader {
    private final Map<EnvironmentAttribute<?>, EnvironmentAttributeSystem.ValueSampler<?>> attributeSamplers = new Reference2ObjectOpenHashMap<>();

    private EnvironmentAttributeSystem(Map<EnvironmentAttribute<?>, List<EnvironmentAttributeLayer<?>>> layersByAttribute) {
        layersByAttribute.forEach(
            (attribute, layers) -> this.attributeSamplers
                .put(
                    (EnvironmentAttribute<?>)attribute,
                    this.bakeLayerSampler((EnvironmentAttribute<?>)attribute, (List<? extends EnvironmentAttributeLayer<?>>)layers)
                )
        );
    }

    private <Value> EnvironmentAttributeSystem.ValueSampler<Value> bakeLayerSampler(
        EnvironmentAttribute<Value> attribute, List<? extends EnvironmentAttributeLayer<?>> untypedLayers
    ) {
        List<EnvironmentAttributeLayer<Value>> layers = new ArrayList<>((Collection<? extends EnvironmentAttributeLayer<Value>>)untypedLayers);
        Value constantBaseValue = attribute.defaultValue();

        while (!layers.isEmpty()) {
            if (!(layers.getFirst() instanceof EnvironmentAttributeLayer.Constant<Value> constantLayer)) {
                break;
            }

            constantBaseValue = constantLayer.applyConstant(constantBaseValue);
            layers.removeFirst();
        }

        boolean isAffectedByPosition = layers.stream().anyMatch(layer -> layer instanceof EnvironmentAttributeLayer.Positional);
        return new EnvironmentAttributeSystem.ValueSampler<>(attribute, constantBaseValue, List.copyOf(layers), isAffectedByPosition);
    }

    public static EnvironmentAttributeSystem.Builder builder() {
        return new EnvironmentAttributeSystem.Builder();
    }

    private static void addDefaultLayers(EnvironmentAttributeSystem.Builder builder, Level level) {
        RegistryAccess registries = level.registryAccess();
        BiomeManager biomeManager = level.getBiomeManager();
        ClockManager clockManager = level.clockManager();
        addDimensionLayer(builder, level.dimensionType());
        addBiomeLayer(builder, registries.lookupOrThrow(Registries.BIOME), biomeManager);
        level.dimensionType().timelines().forEach(timeline -> builder.addTimelineLayer((Holder<Timeline>)timeline, clockManager));
        if (level.canHaveWeather()) {
            WeatherAttributes.addBuiltinLayers(builder, WeatherAttributes.WeatherAccess.from(level));
        }
    }

    private static void addDimensionLayer(EnvironmentAttributeSystem.Builder builder, DimensionType dimensionType) {
        builder.addConstantLayer(dimensionType.attributes());
    }

    private static void addBiomeLayer(EnvironmentAttributeSystem.Builder builder, HolderLookup<Biome> biomes, BiomeManager biomeManager) {
        Stream<EnvironmentAttribute<?>> attributesProvidedByBiomes = biomes.listElements()
            .flatMap(biome -> biome.value().getAttributes().keySet().stream())
            .distinct();
        attributesProvidedByBiomes.forEach(attribute -> addBiomeLayerForAttribute(builder, (EnvironmentAttribute<?>)attribute, biomeManager));
    }

    private static <Value> void addBiomeLayerForAttribute(
        EnvironmentAttributeSystem.Builder builder, EnvironmentAttribute<Value> attribute, BiomeManager biomeManager
    ) {
        builder.addPositionalLayer(attribute, (baseValue, pos, biomeWeights) -> {
            if (biomeWeights != null && attribute.isSpatiallyInterpolated()) {
                return biomeWeights.applyAttributeLayer(attribute, baseValue);
            }

            Holder<Biome> biome = biomeManager.getNoiseBiomeAtPosition(pos.x, pos.y, pos.z);
            return biome.value().getAttributes().applyModifier(attribute, baseValue);
        });
    }

    public void invalidateTickCache() {
        this.attributeSamplers.values().forEach(EnvironmentAttributeSystem.ValueSampler::invalidateTickCache);
    }

    private <Value> EnvironmentAttributeSystem.@Nullable ValueSampler<Value> getValueSampler(EnvironmentAttribute<Value> attribute) {
        return (EnvironmentAttributeSystem.ValueSampler<Value>)this.attributeSamplers.get(attribute);
    }

    @Override
    public <Value> Value getDimensionValue(EnvironmentAttribute<Value> attribute) {
        if (SharedConstants.IS_RUNNING_IN_IDE && attribute.isPositional()) {
            throw new IllegalStateException("Position must always be provided for positional attribute " + attribute);
        }

        EnvironmentAttributeSystem.ValueSampler<Value> sampler = this.getValueSampler(attribute);
        return sampler == null ? attribute.defaultValue() : sampler.getDimensionValue();
    }

    @Override
    public <Value> Value getValue(EnvironmentAttribute<Value> attribute, Vec3 pos, @Nullable SpatialAttributeInterpolator biomeInterpolator) {
        EnvironmentAttributeSystem.ValueSampler<Value> sampler = this.getValueSampler(attribute);
        return sampler == null ? attribute.defaultValue() : sampler.getValue(pos, biomeInterpolator);
    }

    @VisibleForTesting
    <Value> Value getConstantBaseValue(EnvironmentAttribute<Value> attribute) {
        EnvironmentAttributeSystem.ValueSampler<Value> sampler = this.getValueSampler(attribute);
        return sampler != null ? sampler.baseValue : attribute.defaultValue();
    }

    @VisibleForTesting
    boolean isAffectedByPosition(EnvironmentAttribute<?> attribute) {
        EnvironmentAttributeSystem.ValueSampler<?> sampler = this.getValueSampler(attribute);
        return sampler != null && sampler.isAffectedByPosition;
    }

    public static class Builder {
        private final Map<EnvironmentAttribute<?>, List<EnvironmentAttributeLayer<?>>> layersByAttribute = new HashMap<>();

        private Builder() {
        }

        public EnvironmentAttributeSystem.Builder addDefaultLayers(Level level) {
            EnvironmentAttributeSystem.addDefaultLayers(this, level);
            return this;
        }

        public EnvironmentAttributeSystem.Builder addConstantLayer(EnvironmentAttributeMap attributeMap) {
            for (EnvironmentAttribute<?> attribute : attributeMap.keySet()) {
                this.addConstantEntry(attribute, attributeMap);
            }

            return this;
        }

        private <Value> EnvironmentAttributeSystem.Builder addConstantEntry(EnvironmentAttribute<Value> attribute, EnvironmentAttributeMap attributeMap) {
            EnvironmentAttributeMap.Entry<Value, ?> entry = attributeMap.get(attribute);
            if (entry == null) {
                throw new IllegalArgumentException("Missing attribute " + attribute);
            } else {
                return this.addConstantLayer(attribute, entry::applyModifier);
            }
        }

        public <Value> EnvironmentAttributeSystem.Builder addConstantLayer(
            EnvironmentAttribute<Value> attribute, EnvironmentAttributeLayer.Constant<Value> layer
        ) {
            return this.addLayer(attribute, layer);
        }

        public <Value> EnvironmentAttributeSystem.Builder addTimeBasedLayer(
            EnvironmentAttribute<Value> attribute, EnvironmentAttributeLayer.TimeBased<Value> layer
        ) {
            return this.addLayer(attribute, layer);
        }

        public <Value> EnvironmentAttributeSystem.Builder addPositionalLayer(
            EnvironmentAttribute<Value> attribute, EnvironmentAttributeLayer.Positional<Value> layer
        ) {
            return this.addLayer(attribute, layer);
        }

        private <Value> EnvironmentAttributeSystem.Builder addLayer(EnvironmentAttribute<Value> attribute, EnvironmentAttributeLayer<Value> layer) {
            this.layersByAttribute.computeIfAbsent(attribute, t -> new ArrayList<>()).add(layer);
            return this;
        }

        public EnvironmentAttributeSystem.Builder addTimelineLayer(Holder<Timeline> timeline, ClockManager clockManager) {
            for (EnvironmentAttribute<?> attribute : timeline.value().attributes()) {
                this.addTimelineLayerForAttribute(timeline, attribute, clockManager);
            }

            return this;
        }

        private <Value> void addTimelineLayerForAttribute(Holder<Timeline> timeline, EnvironmentAttribute<Value> attribute, ClockManager clockManager) {
            this.addTimeBasedLayer(attribute, timeline.value().createTrackSampler(attribute, clockManager));
        }

        public EnvironmentAttributeSystem build() {
            return new EnvironmentAttributeSystem(this.layersByAttribute);
        }
    }

    private static class ValueSampler<Value> {
        private final EnvironmentAttribute<Value> attribute;
        private final Value baseValue;
        private final List<EnvironmentAttributeLayer<Value>> layers;
        private final boolean isAffectedByPosition;
        private @Nullable Value cachedTickValue;
        private int cacheTickId;

        private ValueSampler(
            EnvironmentAttribute<Value> attribute, Value baseValue, List<EnvironmentAttributeLayer<Value>> layers, boolean isAffectedByPosition
        ) {
            this.attribute = attribute;
            this.baseValue = baseValue;
            this.layers = layers;
            this.isAffectedByPosition = isAffectedByPosition;
        }

        public void invalidateTickCache() {
            this.cachedTickValue = null;
            this.cacheTickId++;
        }

        public Value getDimensionValue() {
            if (this.cachedTickValue != null) {
                return this.cachedTickValue;
            }

            Value result = this.computeValueNotPositional();
            this.cachedTickValue = result;
            return result;
        }

        public Value getValue(Vec3 pos, @Nullable SpatialAttributeInterpolator biomeInterpolator) {
            return !this.isAffectedByPosition ? this.getDimensionValue() : this.computeValuePositional(pos, biomeInterpolator);
        }

        private Value computeValuePositional(Vec3 pos, @Nullable SpatialAttributeInterpolator biomeInterpolator) {
            Value result = this.baseValue;

            for (EnvironmentAttributeLayer<Value> layer : this.layers) {
                result = (Value)(switch (layer) {
                    case EnvironmentAttributeLayer.Constant<Value> constantLayer -> constantLayer.applyConstant(result);
                    case EnvironmentAttributeLayer.TimeBased<Value> timeBasedLayer -> timeBasedLayer.applyTimeBased(result, this.cacheTickId);
                    case EnvironmentAttributeLayer.Positional<Value> positionalLayer -> positionalLayer.applyPositional(
                        result, Objects.requireNonNull(pos), biomeInterpolator
                    );
                    default -> throw new MatchException(null, null);
                });
            }

            return this.attribute.sanitizeValue(result);
        }

        private Value computeValueNotPositional() {
            Value result = this.baseValue;

            for (EnvironmentAttributeLayer<Value> layer : this.layers) {
                result = (Value)(switch (layer) {
                    case EnvironmentAttributeLayer.Constant<Value> constantLayer -> constantLayer.applyConstant(result);
                    case EnvironmentAttributeLayer.TimeBased<Value> timeBasedLayer -> timeBasedLayer.applyTimeBased(result, this.cacheTickId);
                    case EnvironmentAttributeLayer.Positional<Value> ignored -> result;
                    default -> throw new MatchException(null, null);
                });
            }

            return this.attribute.sanitizeValue(result);
        }
    }
}
