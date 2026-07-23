package net.minecraft.client.renderer.item;

import com.mojang.math.Transformation;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.client.multiplayer.CacheSlot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.client.renderer.item.properties.conditional.ItemModelPropertyTest;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.util.RegistryContextSwapper;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class ConditionalItemModel implements ItemModel {
    private final ItemModelPropertyTest property;
    private final ItemModel onTrue;
    private final ItemModel onFalse;

    public ConditionalItemModel(ItemModelPropertyTest property, ItemModel onTrue, ItemModel onFalse) {
        this.property = property;
        this.onTrue = onTrue;
        this.onFalse = onFalse;
    }

    @Override
    public void update(
        ItemStackRenderState output,
        ItemStack item,
        ItemModelResolver resolver,
        ItemDisplayContext displayContext,
        @Nullable ClientLevel level,
        @Nullable ItemOwner owner,
        int seed
    ) {
        output.appendModelIdentityElement(this);
        (this.property.get(item, level, owner == null ? null : owner.asLivingEntity(), seed, displayContext) ? this.onTrue : this.onFalse)
            .update(output, item, resolver, displayContext, level, owner, seed);
    }

    public record Unbaked(Optional<Transformation> transformation, ConditionalItemModelProperty property, ItemModel.Unbaked onTrue, ItemModel.Unbaked onFalse)
        implements ItemModel.Unbaked {
        public static final MapCodec<ConditionalItemModel.Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
            i -> i.group(
                    Transformation.EXTENDED_CODEC.optionalFieldOf("transformation").forGetter(ConditionalItemModel.Unbaked::transformation),
                    ConditionalItemModelProperties.MAP_CODEC.forGetter(ConditionalItemModel.Unbaked::property),
                    ItemModels.CODEC.fieldOf("on_true").forGetter(ConditionalItemModel.Unbaked::onTrue),
                    ItemModels.CODEC.fieldOf("on_false").forGetter(ConditionalItemModel.Unbaked::onFalse)
                )
                .apply(i, ConditionalItemModel.Unbaked::new)
        );

        @Override
        public MapCodec<ConditionalItemModel.Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
            Matrix4fc childTransform = Transformation.compose(transformation, this.transformation);
            return new ConditionalItemModel(
                this.adaptProperty(this.property, context.contextSwapper()),
                this.onTrue.bake(context, childTransform),
                this.onFalse.bake(context, childTransform)
            );
        }

        private ItemModelPropertyTest adaptProperty(ConditionalItemModelProperty originalProperty, @Nullable RegistryContextSwapper contextSwapper) {
            if (contextSwapper == null) {
                return originalProperty;
            }

            CacheSlot<ClientLevel, ItemModelPropertyTest> remappedModelCache = new CacheSlot<>(
                context -> swapContext(originalProperty, contextSwapper, context)
            );
            return (itemStack, level, owner, seed, displayContext) -> {
                ItemModelPropertyTest property = level == null ? originalProperty : remappedModelCache.compute(level);
                return property.get(itemStack, level, owner, seed, displayContext);
            };
        }

        private static <T extends ConditionalItemModelProperty> T swapContext(T originalProperty, RegistryContextSwapper contextSwapper, ClientLevel context) {
            return (T)contextSwapper.swapTo((com.mojang.serialization.Codec<T>)originalProperty.type().codec(), originalProperty, context.registryAccess()).result().orElse(originalProperty);
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            this.onTrue.resolveDependencies(resolver);
            this.onFalse.resolveDependencies(resolver);
        }
    }
}
