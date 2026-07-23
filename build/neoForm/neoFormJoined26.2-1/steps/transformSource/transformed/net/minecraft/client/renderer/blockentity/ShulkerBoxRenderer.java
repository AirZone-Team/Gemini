package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.ShulkerBoxRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class ShulkerBoxRenderer implements BlockEntityRenderer<ShulkerBoxBlockEntity, ShulkerBoxRenderState> {
    private static final Map<Direction, Transformation> TRANSFORMATIONS = Util.makeEnumMap(Direction.class, ShulkerBoxRenderer::createModelTransform);
    private final SpriteGetter sprites;
    private final ShulkerBoxRenderer.ShulkerBoxModel model;

    public ShulkerBoxRenderer(BlockEntityRendererProvider.Context context) {
        this(context.entityModelSet(), context.sprites());
    }

    public ShulkerBoxRenderer(SpecialModelRenderer.BakingContext context) {
        this(context.entityModelSet(), context.sprites());
    }

    public ShulkerBoxRenderer(EntityModelSet context, SpriteGetter sprites) {
        this.sprites = sprites;
        this.model = new ShulkerBoxRenderer.ShulkerBoxModel(context.bakeLayer(ModelLayers.SHULKER_BOX));
    }

    public ShulkerBoxRenderState createRenderState() {
        return new ShulkerBoxRenderState();
    }

    public void extractRenderState(
        ShulkerBoxBlockEntity blockEntity,
        ShulkerBoxRenderState state,
        float partialTicks,
        Vec3 cameraPosition,
        ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.direction = blockEntity.getBlockState().getValueOrElse(ShulkerBoxBlock.FACING, Direction.UP);
        state.color = blockEntity.getColor();
        state.progress = blockEntity.getProgress(partialTicks);
    }

    public void submit(ShulkerBoxRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        DyeColor color = state.color;
        SpriteId sprite;
        if (color == null) {
            sprite = Sheets.DEFAULT_SHULKER_TEXTURE_LOCATION;
        } else {
            sprite = Sheets.getShulkerBoxSprite(color);
        }

        this.submit(
            poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.direction, state.progress, state.breakProgress, sprite, 0
        );
    }

    private void submit(
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        int lightCoords,
        int overlayCoords,
        Direction direction,
        float progress,
        ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress,
        SpriteId sprite,
        int outlineColor
    ) {
        poseStack.pushPose();
        poseStack.mulPose(modelTransform(direction));
        this.submit(poseStack, submitNodeCollector, lightCoords, overlayCoords, progress, breakProgress, sprite, outlineColor);
        poseStack.popPose();
    }

    public void submit(
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        int lightCoords,
        int overlayCoords,
        float progress,
        ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress,
        SpriteId sprite,
        int outlineColor
    ) {
        this.model.setupAnim(progress);
        submitNodeCollector.submitModel(this.model, progress, poseStack, lightCoords, overlayCoords, -1, sprite, this.sprites, outlineColor, breakProgress);
    }

    private static Transformation createModelTransform(Direction direction) {
        float scale = 0.9995F;
        return new Transformation(
            new Matrix4f()
                .translation(0.5F, 0.5F, 0.5F)
                .scale(0.9995F, 0.9995F, 0.9995F)
                .rotate(direction.getRotation())
                .scale(1.0F, -1.0F, -1.0F)
                .translate(0.0F, -1.0F, 0.0F)
        );
    }

    public static Transformation modelTransform(Direction direction) {
        return TRANSFORMATIONS.get(direction);
    }

    public void getExtents(float progress, Consumer<Vector3fc> output) {
        PoseStack poseStack = new PoseStack();
        this.model.setupAnim(progress);
        this.model.root().getExtentsForGui(poseStack, output);
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(ShulkerBoxBlockEntity blockEntity) {
        net.minecraft.core.BlockPos pos = blockEntity.getBlockPos();
        return new net.minecraft.world.phys.AABB(pos.getX() - 0.5, pos.getY() - 0.5, pos.getZ() - 0.5, pos.getX() + 1.5, pos.getY() + 1.5, pos.getZ() + 1.5);
    }

    private static class ShulkerBoxModel extends Model<Float> {
        private final ModelPart lid;

        public ShulkerBoxModel(ModelPart root) {
            super(root, RenderTypes::entityCutout);
            this.lid = root.getChild("lid");
        }

        public void setupAnim(Float progress) {
            super.setupAnim(progress);
            this.lid.setPos(0.0F, 24.0F - progress * 0.5F * 16.0F, 0.0F);
            this.lid.yRot = 270.0F * progress * (float) (Math.PI / 180.0);
        }
    }
}
