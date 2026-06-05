package geminiclient.mixin;

import geminiclient.gemini.modules.impl.visual.itemPhysical.ClientPhysic;
import geminiclient.gemini.modules.impl.visual.itemPhysical.ItemEntityRenderStateExtender;
import geminiclient.gemini.modules.impl.visual.ItemPhysical;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public class MixinItemEntityRenderState implements ItemEntityRenderStateExtender {

    @Unique
    private float itemPhysical$rotX;
    @Unique
    private float itemPhysical$rotY;
    @Unique
    private boolean itemPhysical$isBlock;
    @Unique
    private boolean itemPhysical$additionalOffset;

    @Override
    public float getXRot() {
        return itemPhysical$rotX;
    }

    @Override
    public float getYRot() {
        return itemPhysical$rotY;
    }

    @Override
    public boolean hasAdditionalOffset() {
        return itemPhysical$additionalOffset;
    }

    @Override
    public boolean isBlock() {
        return itemPhysical$isBlock;
    }

    @Override
    public void extractPhysic(ItemEntity entity) {
        ItemEntityRenderState state = (ItemEntityRenderState) (Object) this;
        itemPhysical$isBlock = state.item.usesBlockLight();
        ClientPhysic.calculateRotation(entity, state);
        itemPhysical$additionalOffset = ItemPhysical.requiresOffset(entity);
        itemPhysical$rotX = entity.getXRot();
        itemPhysical$rotY = entity.getYRot();
    }
}
