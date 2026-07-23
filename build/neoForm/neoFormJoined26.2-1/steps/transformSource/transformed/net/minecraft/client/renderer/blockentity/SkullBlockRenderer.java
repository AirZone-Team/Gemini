package net.minecraft.client.renderer.blockentity;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.math.Transformation;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.skull.DragonHeadModel;
import net.minecraft.client.model.object.skull.PiglinHeadModel;
import net.minecraft.client.model.object.skull.SkullModel;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.SkullBlockRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class SkullBlockRenderer implements BlockEntityRenderer<SkullBlockEntity, SkullBlockRenderState> {
    public static final WallAndGroundTransformations<Transformation> TRANSFORMATIONS = new WallAndGroundTransformations<>(
        SkullBlockRenderer::createWallTransformation, SkullBlockRenderer::createGroundTransformation, 16
    );
    private final Function<SkullBlock.Type, SkullModelBase> modelByType;
    public static final Map<SkullBlock.Type, Identifier> SKIN_BY_TYPE = Util.make(Maps.newHashMap(), map -> {
        map.put(SkullBlock.Types.SKELETON, Identifier.withDefaultNamespace("textures/entity/skeleton/skeleton.png"));
        map.put(SkullBlock.Types.WITHER_SKELETON, Identifier.withDefaultNamespace("textures/entity/skeleton/wither_skeleton.png"));
        map.put(SkullBlock.Types.ZOMBIE, Identifier.withDefaultNamespace("textures/entity/zombie/zombie.png"));
        map.put(SkullBlock.Types.CREEPER, Identifier.withDefaultNamespace("textures/entity/creeper/creeper.png"));
        map.put(SkullBlock.Types.DRAGON, Identifier.withDefaultNamespace("textures/entity/enderdragon/dragon.png"));
        map.put(SkullBlock.Types.PIGLIN, Identifier.withDefaultNamespace("textures/entity/piglin/piglin.png"));
        map.put(SkullBlock.Types.PLAYER, DefaultPlayerSkin.getDefaultTexture());
    });
    private final PlayerSkinRenderCache playerSkinRenderCache;

    public static @Nullable SkullModelBase createModel(EntityModelSet modelSet, SkullBlock.Type type) {
        if (type instanceof SkullBlock.Types vanillaType) {
            return switch (vanillaType) {
                case SKELETON -> new SkullModel(modelSet.bakeLayer(ModelLayers.SKELETON_SKULL));
                case WITHER_SKELETON -> new SkullModel(modelSet.bakeLayer(ModelLayers.WITHER_SKELETON_SKULL));
                case PLAYER -> new SkullModel(modelSet.bakeLayer(ModelLayers.PLAYER_HEAD));
                case ZOMBIE -> new SkullModel(modelSet.bakeLayer(ModelLayers.ZOMBIE_HEAD));
                case CREEPER -> new SkullModel(modelSet.bakeLayer(ModelLayers.CREEPER_HEAD));
                case DRAGON -> new DragonHeadModel(modelSet.bakeLayer(ModelLayers.DRAGON_SKULL));
                case PIGLIN -> new PiglinHeadModel(modelSet.bakeLayer(ModelLayers.PIGLIN_HEAD));
            };
        } else {
            return net.neoforged.neoforge.client.ClientHooks.getModdedSkullModel(modelSet, type); // Neo: Lookup model for modded skull types
        }
    }

    public SkullBlockRenderer(BlockEntityRendererProvider.Context context) {
        EntityModelSet modelSet = context.entityModelSet();
        this.playerSkinRenderCache = context.playerSkinRenderCache();
        this.modelByType = Util.memoize(type -> createModel(modelSet, type));
    }

    public SkullBlockRenderState createRenderState() {
        return new SkullBlockRenderState();
    }

    public void extractRenderState(
        SkullBlockEntity blockEntity,
        SkullBlockRenderState state,
        float partialTicks,
        Vec3 cameraPosition,
        ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.animationProgress = blockEntity.getAnimation(partialTicks);
        BlockState blockState = blockEntity.getBlockState();
        if (blockState.getBlock() instanceof WallSkullBlock) {
            Direction facing = blockState.getValue(WallSkullBlock.FACING);
            state.transformation = TRANSFORMATIONS.wallTransformation(facing);
        } else {
            state.transformation = TRANSFORMATIONS.freeTransformations(blockState.getValue(SkullBlock.ROTATION));
        }

        state.skullType = ((AbstractSkullBlock)blockState.getBlock()).getType();
        state.renderType = this.resolveSkullRenderType(state.skullType, blockEntity);
    }

    public void submit(SkullBlockRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        SkullModelBase model = this.modelByType.apply(state.skullType);
        poseStack.pushPose();
        poseStack.mulPose(state.transformation);
        submitSkull(state.animationProgress, poseStack, submitNodeCollector, state.lightCoords, model, state.renderType, 0, state.breakProgress);
        poseStack.popPose();
    }

    public static void submitSkull(
        float animationValue,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        int lightCoords,
        SkullModelBase model,
        RenderType renderType,
        int outlineColor,
        ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
    ) {
        SkullModelBase.State modelState = new SkullModelBase.State();
        modelState.animationPos = animationValue;
        submitNodeCollector.submitModel(model, modelState, poseStack, renderType, lightCoords, OverlayTexture.NO_OVERLAY, outlineColor, breakProgress);
    }

    private RenderType resolveSkullRenderType(SkullBlock.Type type, SkullBlockEntity entity) {
        if (type == SkullBlock.Types.PLAYER) {
            ResolvableProfile ownerProfile = entity.getOwnerProfile();
            if (ownerProfile != null) {
                return this.playerSkinRenderCache.getOrDefault(ownerProfile).renderType();
            }
        }

        return getSkullRenderType(type, null);
    }

    public static RenderType getSkullRenderType(SkullBlock.Type type, @Nullable Identifier texture) {
        return RenderTypes.entityCutoutZOffset(texture != null ? texture : SKIN_BY_TYPE.get(type));
    }

    public static RenderType getPlayerSkinRenderType(Identifier texture) {
        return RenderTypes.entityTranslucent(texture);
    }

    private static Transformation createWallTransformation(Direction wallDirection) {
        float offset = 0.25F;
        return new Transformation(
            new Vector3f(0.5F - wallDirection.getStepX() * 0.25F, 0.25F, 0.5F - wallDirection.getStepZ() * 0.25F),
            Axis.YP.rotationDegrees(-wallDirection.getOpposite().toYRot()),
            new Vector3f(-1.0F, -1.0F, 1.0F),
            null
        );
    }

    private static Transformation createGroundTransformation(int segment) {
        return new Transformation(
            new Matrix4f().translation(0.5F, 0.0F, 0.5F).rotate(Axis.YP.rotationDegrees(-RotationSegment.convertToDegrees(segment))).scale(-1.0F, -1.0F, 1.0F)
        );
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(SkullBlockEntity blockEntity) {
        SkullBlock.Type type = ((AbstractSkullBlock) blockEntity.getBlockState().getBlock()).getType();
        if (type == SkullBlock.Types.DRAGON) {
            net.minecraft.core.BlockPos pos = blockEntity.getBlockPos();
            return new net.minecraft.world.phys.AABB(pos.getX() - .75, pos.getY() - .35, pos.getZ() - .75, pos.getX() + 1.75, pos.getY() + 1.0, pos.getZ() + 1.75);
        }
        return BlockEntityRenderer.super.getRenderBoundingBox(blockEntity);
    }
}
