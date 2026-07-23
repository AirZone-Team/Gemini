package net.minecraft.client.renderer;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class ScreenEffectRenderer {
    private static final Identifier UNDERWATER_LOCATION = Identifier.withDefaultNamespace("textures/misc/underwater.png");
    private final Minecraft minecraft;
    private final SpriteGetter sprites;
    public static final int ITEM_ACTIVATION_ANIMATION_LENGTH = 40;
    private @Nullable ItemStack itemActivationItem;
    private int itemActivationTicks;
    private float itemActivationOffX;
    private float itemActivationOffY;

    public ScreenEffectRenderer(Minecraft minecraft, SpriteGetter sprites) {
        this.minecraft = minecraft;
        this.sprites = sprites;
    }

    public void tick() {
        if (this.itemActivationTicks > 0) {
            this.itemActivationTicks--;
            if (this.itemActivationTicks == 0) {
                this.itemActivationItem = null;
            }
        }
    }

    public void submit(boolean isFirstPerson, boolean isSleeping, float partialTicks, SubmitNodeCollector submitNodeCollector, boolean hideGui) {
        PoseStack poseStack = new PoseStack();
        Player player = this.minecraft.player;
        if (isFirstPerson && !isSleeping) {
            var overlay = getViewBlockingStateAndPos(player);
            if (overlay != null && !net.neoforged.neoforge.client.ClientHooks.renderBlockOverlay(player, poseStack, net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent.OverlayType.BLOCK, overlay.getLeft(), overlay.getRight(), this.sprites, submitNodeCollector)) {
                BlockStateModelSet blockStateModelSet = this.minecraft.getModelManager().getBlockStateModelSet();
                TextureAtlasSprite sprite = blockStateModelSet.getParticleMaterial(overlay.getLeft()).sprite();
                submitBlockSprite(sprite, poseStack, submitNodeCollector, -15132391);
            }

            if (!this.minecraft.player.isSpectator()) {
                if (this.minecraft.player.isEyeInFluid(FluidTags.WATER)) {
                    if (!net.neoforged.neoforge.client.ClientHooks.renderWaterOverlay(player, poseStack, this.sprites, submitNodeCollector))
                    submitWater(this.minecraft, poseStack, submitNodeCollector);
                }

                if (this.minecraft.player.isOnFire()) {
                    TextureAtlasSprite fireSprite = this.sprites.get(ModelBakery.FIRE_1);
                    if (!net.neoforged.neoforge.client.ClientHooks.renderFireOverlay(player, poseStack, this.sprites, submitNodeCollector))
                    submitFire(poseStack, submitNodeCollector, fireSprite);
                }
            }
        }

        if (!hideGui) {
            this.renderItemActivationAnimation(poseStack, partialTicks, submitNodeCollector);
        }
    }

    private void renderItemActivationAnimation(PoseStack poseStack, float partialTicks, SubmitNodeCollector submitNodeCollector) {
        if (this.itemActivationItem != null && this.itemActivationTicks > 0) {
            int tick = 40 - this.itemActivationTicks;
            float scale = (tick + partialTicks) / 40.0F;
            float ts = scale * scale;
            float tc = scale * ts;
            float smoothScale = 10.25F * tc * ts - 24.95F * ts * ts + 25.5F * tc - 13.8F * ts + 4.0F * scale;
            float piScale = smoothScale * (float) Math.PI;
            WindowRenderState windowState = this.minecraft.gameRenderer.gameRenderState().windowRenderState;
            float aspectRatio = (float)windowState.width / windowState.height;
            float offX = this.itemActivationOffX * 0.3F * aspectRatio;
            float offY = this.itemActivationOffY * 0.3F;
            poseStack.pushPose();
            poseStack.translate(offX * Mth.abs(Mth.sin(piScale * 2.0F)), offY * Mth.abs(Mth.sin(piScale * 2.0F)), -10.0F + 9.0F * Mth.sin(piScale));
            float size = 0.8F;
            poseStack.scale(0.8F, 0.8F, 0.8F);
            poseStack.mulPose(Axis.YP.rotationDegrees(900.0F * Mth.abs(Mth.sin(piScale))));
            poseStack.mulPose(Axis.XP.rotationDegrees(6.0F * Mth.cos(scale * 8.0F)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(6.0F * Mth.cos(scale * 8.0F)));
            this.minecraft.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
            ItemStackRenderState itemState = new ItemStackRenderState();
            this.minecraft.getItemModelResolver().updateForTopItem(itemState, this.itemActivationItem, ItemDisplayContext.FIXED, this.minecraft.level, null, 0);
            itemState.submit(poseStack, submitNodeCollector, 15728880, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    public void resetItemActivation() {
        this.itemActivationItem = null;
    }

    public void displayItemActivation(ItemStack itemStack, RandomSource random) {
        this.itemActivationItem = itemStack;
        this.itemActivationTicks = 40;
        this.itemActivationOffX = random.nextFloat() * 2.0F - 1.0F;
        this.itemActivationOffY = random.nextFloat() * 2.0F - 1.0F;
    }

    private static @Nullable BlockState getViewBlockingState(Player player) {
        return net.minecraft.Optionull.map(getViewBlockingStateAndPos(player), org.apache.commons.lang3.tuple.Pair::getLeft);
    }

    private static org.apache.commons.lang3.tuple.@Nullable Pair<BlockState, BlockPos> getViewBlockingStateAndPos(Player player) {
        if (player.noPhysics) {
            return null;
        }

        BlockPos.MutableBlockPos testPos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < 8; i++) {
            testPos.set(
                player.getX() + ((i >> 0) % 2 - 0.5F) * player.getBbWidth() * 0.8F,
                player.getEyeY() + ((i >> 1) % 2 - 0.5F) * 0.1F * player.getScale(),
                player.getZ() + ((i >> 2) % 2 - 0.5F) * player.getBbWidth() * 0.8F
            );
            BlockState blockState = player.level().getBlockState(testPos);
            if (blockState.getRenderShape() != RenderShape.INVISIBLE && blockState.isViewBlocking(player.level(), testPos)) {
                return org.apache.commons.lang3.tuple.Pair.of(blockState, testPos.immutable());
            }
        }

        return null;
    }

    private static void submitBlockSprite(TextureAtlasSprite sprite, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int color) {
        submitNodeCollector.submitCustomGeometry(
            poseStack,
            RenderTypes.blockScreenEffect(sprite.atlasLocation()),
            (pose, builder) -> buildSpriteQuad(builder, pose.pose(), sprite, -1.0F, -1.0F, 1.0F, 1.0F, -0.5F, color)
        );
    }

    private static void submitWater(Minecraft minecraft, PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
        submitFluid(minecraft, poseStack, submitNodeCollector, UNDERWATER_LOCATION);
    }

    public static void submitFluid(Minecraft minecraft, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, Identifier texture) {
        LocalPlayer player = minecraft.player;
        BlockPos pos = BlockPos.containing(player.getEyePosition());
        float brightness = Lightmap.getBrightness(player.level().dimensionType(), player.level().getMaxLocalRawBrightness(pos));
        int color = ARGB.colorFromFloat(0.1F, brightness, brightness, brightness);
        float u0 = -player.getYRot() / 64.0F;
        float v0 = player.getXRot() / 64.0F;
        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.blockScreenEffect(texture), (pose, builder) -> {
            float uvSize = 4.0F;
            buildQuad(builder, pose.pose(), -1.0F, -1.0F, 1.0F, 1.0F, -0.5F, u0 + 4.0F, v0 + 4.0F, u0, v0, color);
        });
    }

    private static void submitFire(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, TextureAtlasSprite sprite) {
        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.fireScreenEffect(sprite.atlasLocation()), (basePose, builder) -> {
            Matrix4f pose = new Matrix4f();
            pose.set(basePose.pose());
            pose.translate(0.24F, -0.3F, 0.0F);
            pose.rotateY((float) (-Math.PI / 18));
            buildFireQuad(sprite, builder, pose);
            pose.set(basePose.pose());
            pose.translate(-0.24F, -0.3F, 0.0F);
            pose.rotateY((float) (Math.PI / 18));
            buildFireQuad(sprite, builder, pose);
        });
    }

    private static void buildFireQuad(TextureAtlasSprite sprite, VertexConsumer builder, Matrix4f pose) {
        float size = 1.0F;
        buildSpriteQuad(builder, pose, sprite, -0.5F, -0.5F, 0.5F, 0.5F, -0.5F, -436207617);
    }

    private static void buildSpriteQuad(
        VertexConsumer builder, Matrix4f pose, TextureAtlasSprite sprite, float x0, float y0, float x1, float y1, float z, int color
    ) {
        buildQuad(builder, pose, x0, y0, x1, y1, z, sprite.getU1(), sprite.getV1(), sprite.getU0(), sprite.getV0(), color);
    }

    private static void buildQuad(
        VertexConsumer builder, Matrix4f pose, float x0, float y0, float x1, float y1, float z, float u0, float v0, float u1, float v1, int color
    ) {
        builder.addVertex(pose, x0, y0, z).setUv(u0, v0).setColor(color);
        builder.addVertex(pose, x1, y0, z).setUv(u1, v0).setColor(color);
        builder.addVertex(pose, x1, y1, z).setUv(u1, v1).setColor(color);
        builder.addVertex(pose, x0, y1, z).setUv(u0, v1).setColor(color);
    }
}
