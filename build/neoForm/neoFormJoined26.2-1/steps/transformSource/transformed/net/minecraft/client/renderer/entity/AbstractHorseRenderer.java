package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.EquineRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class AbstractHorseRenderer<T extends AbstractHorse, S extends EquineRenderState, M extends EntityModel<? super S>>
    extends AgeableMobRenderer<T, S, M> {
    public AbstractHorseRenderer(EntityRendererProvider.Context context, M model, M babyModel) {
        super(context, model, babyModel, 0.75F);
    }

    public void extractRenderState(T entity, S state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.saddle = entity.getItemBySlot(EquipmentSlot.SADDLE).copy();
        state.bodyArmorItem = entity.getBodyArmorItem().copy();
        state.isRidden = entity.isVehicle();
        state.eatAnimation = entity.getEatAnim(partialTicks);
        state.standAnimation = entity.getStandAnim(partialTicks);
        state.feedingAnimation = entity.getMouthAnim(partialTicks);
        state.animateTail = entity.tailCounter > 0;
    }
}
