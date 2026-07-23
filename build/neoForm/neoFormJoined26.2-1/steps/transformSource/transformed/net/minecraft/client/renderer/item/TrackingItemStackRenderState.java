package net.minecraft.client.renderer.item;

import java.util.ArrayList;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TrackingItemStackRenderState extends ItemStackRenderState {
    private final List<Object> modelIdentityElements = new ArrayList<>();

    @Override
    public void appendModelIdentityElement(Object element) {
        this.modelIdentityElements.add(element);
    }

    public Object getModelIdentity() {
        return this.modelIdentityElements;
    }
}
