package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class EquipmentLayerRenderer {
    private static final int NO_LAYER_COLOR = 0;
    private final EquipmentAssetManager equipmentAssets;
    private final Function<EquipmentLayerRenderer.LayerTextureKey, Identifier> layerTextureLookup;
    private final Function<EquipmentLayerRenderer.TrimSpriteKey, TextureAtlasSprite> trimSpriteLookup;

    public EquipmentLayerRenderer(EquipmentAssetManager equipmentAssets, TextureAtlas armorTrimAtlas) {
        this.equipmentAssets = equipmentAssets;
        this.layerTextureLookup = Util.memoize(key -> key.layer.getTextureLocation(key.layerType));
        this.trimSpriteLookup = Util.memoize(key -> armorTrimAtlas.getSprite(key.spriteId()));
    }

    public <S> void renderLayers(
        EquipmentClientInfo.LayerType layerType,
        ResourceKey<EquipmentAsset> equipmentAssetId,
        Model<? super S> model,
        S state,
        ItemStack itemStack,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        int lightCoords,
        int outlineColor
    ) {
        this.renderLayers(layerType, equipmentAssetId, model, state, itemStack, poseStack, submitNodeCollector, lightCoords, null, outlineColor, 1);
    }

    public <S> void renderLayers(
        EquipmentClientInfo.LayerType layerType,
        ResourceKey<EquipmentAsset> equipmentAssetId,
        Model<? super S> model,
        S state,
        ItemStack itemStack,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        int lightCoords,
        @Nullable Identifier playerTextureOverride,
        int outlineColor,
        int order
    ) {
        net.neoforged.neoforge.client.extensions.common.IClientItemExtensions extensions = net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.of(itemStack);
        model = extensions.getGenericArmorModel(itemStack, layerType, model);
        List<EquipmentClientInfo.Layer> layers = this.equipmentAssets.get(equipmentAssetId).getLayers(layerType);
        if (!layers.isEmpty()) {
            int dyeColor = extensions.getDefaultDyeColor(itemStack);
            boolean renderFoil = itemStack.hasFoil();
            int nextOrder = order;

            int idx = 0;
            for (EquipmentClientInfo.Layer layer : layers) {
                int color = extensions.getArmorLayerTintColor(itemStack, layer, idx, dyeColor);
                if (color != 0) {
                    Identifier layerTexture = layer.usePlayerTexture() && playerTextureOverride != null
                        ? playerTextureOverride
                        : this.layerTextureLookup.apply(new EquipmentLayerRenderer.LayerTextureKey(layerType, layer));
                    layerTexture = net.neoforged.neoforge.client.ClientHooks.getArmorTexture(itemStack, layerType, layer, layerTexture);
                    submitNodeCollector.order(nextOrder++)
                        .submitModel(
                            model,
                            state,
                            poseStack,
                            RenderTypes.armorCutoutNoCull(layerTexture),
                            lightCoords,
                            OverlayTexture.NO_OVERLAY,
                            color,
                            null,
                            outlineColor,
                            null
                        );
                    if (renderFoil) {
                        submitNodeCollector.order(nextOrder++)
                            .submitModel(
                                model,
                                state,
                                poseStack,
                                RenderTypes.armorEntityGlint(),
                                lightCoords,
                                OverlayTexture.NO_OVERLAY,
                                color,
                                null,
                                outlineColor,
                                null
                            );
                    }

                    renderFoil = false;
                }
                idx++;
            }

            ArmorTrim trim = itemStack.get(DataComponents.TRIM);
            if (trim != null && layerType != EquipmentClientInfo.LayerType.HUMANOID_BABY) {
                TextureAtlasSprite sprite = this.trimSpriteLookup.apply(new EquipmentLayerRenderer.TrimSpriteKey(trim, layerType, equipmentAssetId));
                RenderType renderType = Sheets.armorTrimsSheet(trim.pattern().value().decal());
                submitNodeCollector.order(nextOrder++)
                    .submitModel(model, state, poseStack, renderType, lightCoords, OverlayTexture.NO_OVERLAY, -1, sprite, outlineColor, null);
            }
        }
    }

    public static int getColorForLayer(EquipmentClientInfo.Layer layer, int dyeColor) {
        Optional<EquipmentClientInfo.Dyeable> dyeable = layer.dyeable();
        if (dyeable.isPresent()) {
            int colorWhenUndyed = dyeable.get().colorWhenUndyed().map(ARGB::opaque).orElse(0);
            return dyeColor != 0 ? dyeColor : colorWhenUndyed;
        } else {
            return -1;
        }
    }

    private record LayerTextureKey(EquipmentClientInfo.LayerType layerType, EquipmentClientInfo.Layer layer) {
    }

    private record TrimSpriteKey(ArmorTrim trim, EquipmentClientInfo.LayerType layerType, ResourceKey<EquipmentAsset> equipmentAssetId) {
        public Identifier spriteId() {
            return this.trim.layerAssetId(this.layerType.trimAssetPrefix(), this.equipmentAssetId);
        }
    }
}
