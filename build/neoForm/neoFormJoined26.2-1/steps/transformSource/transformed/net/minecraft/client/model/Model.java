package net.minecraft.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class Model<S> {
    protected final ModelPart root;
    protected final Function<Identifier, RenderType> renderType;
    private final List<ModelPart> allParts;

    public Model(ModelPart root, Function<Identifier, RenderType> renderType) {
        this.root = root;
        this.renderType = renderType;
        this.allParts = root.getAllParts();
    }

    protected static net.neoforged.neoforge.client.entity.animation.json.AnimationHolder getAnimation(Identifier key) {
        return net.neoforged.neoforge.client.entity.animation.json.AnimationLoader.INSTANCE.getAnimationHolder(key);
    }

    public final Function<Identifier, RenderType> renderType() {
        return this.renderType;
    }

    public final RenderType renderType(Identifier texture) {
        return this.renderType().apply(texture);
    }

    public final void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int lightCoords, int overlayCoords, int color) {
        this.root().render(poseStack, buffer, lightCoords, overlayCoords, color);
    }

    public final ModelPart root() {
        return this.root;
    }

    public final List<ModelPart> allParts() {
        return this.allParts;
    }

    public void setupAnim(S state) {
        this.resetPose();
    }

    public final void resetPose() {
        for (ModelPart part : this.allParts) {
            part.resetPose();
        }
    }

    public static class Simple extends Model<Unit> {
        public Simple(ModelPart root, Function<Identifier, RenderType> renderType) {
            super(root, renderType);
        }

        public void setupAnim(Unit state) {
        }
    }
}
