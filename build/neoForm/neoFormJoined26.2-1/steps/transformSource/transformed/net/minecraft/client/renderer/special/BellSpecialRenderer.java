package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.bell.BellModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BellRenderer;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3fc;

@OnlyIn(Dist.CLIENT)
public class BellSpecialRenderer implements NoDataSpecialModelRenderer {
    private static final BellModel.State STATE = new BellModel.State(0.0F, null);
    private final BellModel model;
    private final SpriteGetter sprites;

    public BellSpecialRenderer(SpriteGetter sprites, BellModel model) {
        this.sprites = sprites;
        this.model = model;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor) {
        submitNodeCollector.submitModel(
            this.model, STATE, poseStack, lightCoords, overlayCoords, -1, BellRenderer.BELL_TEXTURE, this.sprites, outlineColor, null
        );
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        PoseStack poseStack = new PoseStack();
        this.model.setupAnim(STATE);
        this.model.root().getExtentsForGui(poseStack, output);
    }

    public record Unbaked() implements NoDataSpecialModelRenderer.Unbaked {
        public static final MapCodec<BellSpecialRenderer.Unbaked> MAP_CODEC = MapCodec.unit(new BellSpecialRenderer.Unbaked());

        @Override
        public MapCodec<BellSpecialRenderer.Unbaked> type() {
            return MAP_CODEC;
        }

        public BellSpecialRenderer bake(SpecialModelRenderer.BakingContext context) {
            return new BellSpecialRenderer(context.sprites(), new BellModel(context.entityModelSet().bakeLayer(ModelLayers.BELL)));
        }
    }
}
