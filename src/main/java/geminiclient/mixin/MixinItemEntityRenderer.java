package geminiclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import geminiclient.gemini.modules.impl.visual.ItemEntityRenderStateExtender;
import geminiclient.gemini.modules.impl.visual.ItemPhysical;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class MixinItemEntityRenderer extends EntityRenderer<ItemEntity, ItemEntityRenderState> {

    @Shadow
    @Final
    private RandomSource random;

    protected MixinItemEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void submit(ItemEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                         CameraRenderState camera, CallbackInfo ci) {
        if (ItemPhysical.submit(state, poseStack, collector, camera, this.random)) {
            super.submit(state, poseStack, collector, camera);
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
            at = @At("TAIL"), require = 1)
    public void injectExtract(ItemEntity item, ItemEntityRenderState state, float partialTicks, CallbackInfo info) {
        ((ItemEntityRenderStateExtender) state).extractPhysic(item);
    }
}
