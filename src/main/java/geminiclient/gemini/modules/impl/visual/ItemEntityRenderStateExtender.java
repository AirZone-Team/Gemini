package geminiclient.gemini.modules.impl.visual;

import net.minecraft.world.entity.item.ItemEntity;

public interface ItemEntityRenderStateExtender {

    boolean isBlock();

    float getXRot();

    float getYRot();

    boolean hasAdditionalOffset();

    void extractPhysic(ItemEntity item);
}
