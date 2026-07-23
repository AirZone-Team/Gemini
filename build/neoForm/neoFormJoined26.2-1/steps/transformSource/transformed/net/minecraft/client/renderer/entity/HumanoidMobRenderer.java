package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class HumanoidMobRenderer<T extends Mob, S extends HumanoidRenderState, M extends HumanoidModel<S>> extends AgeableMobRenderer<T, S, M> {
    public HumanoidMobRenderer(EntityRendererProvider.Context context, M model, float shadow) {
        this(context, model, model, shadow);
    }

    public HumanoidMobRenderer(EntityRendererProvider.Context context, M model, M babyModel, float shadow) {
        this(context, model, babyModel, shadow, CustomHeadLayer.Transforms.DEFAULT);
    }

    public HumanoidMobRenderer(EntityRendererProvider.Context context, M model, M babyModel, float shadow, CustomHeadLayer.Transforms customHeadTransforms) {
        super(context, model, babyModel, shadow);
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getPlayerSkinRenderCache(), customHeadTransforms));
        this.addLayer(new WingsLayer<>(this, context.getModelSet(), context.getEquipmentRenderer()));
        this.addLayer(new ItemInHandLayer<>(this));
    }

    protected HumanoidModel.ArmPose getArmPose(T mob, HumanoidArm arm) {
        ItemStack itemHeldByArm = mob.getItemHeldByArm(arm);
        SwingAnimation anim = itemHeldByArm.get(DataComponents.SWING_ANIMATION);
        if (anim != null && anim.type() == SwingAnimationType.STAB && mob.swinging) {
            return HumanoidModel.ArmPose.SPEAR;
        } else {
            return itemHeldByArm.is(ItemTags.SPEARS) ? HumanoidModel.ArmPose.SPEAR : HumanoidModel.ArmPose.EMPTY;
        }
    }

    public void extractRenderState(T entity, S state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
        state.leftArmPose = this.getArmPose(entity, HumanoidArm.LEFT);
        state.rightArmPose = this.getArmPose(entity, HumanoidArm.RIGHT);
    }

    public static void extractHumanoidRenderState(LivingEntity entity, HumanoidRenderState state, float partialTicks, ItemModelResolver itemModelResolver) {
        ArmedEntityRenderState.extractArmedEntityRenderState(entity, state, itemModelResolver, partialTicks);
        state.isCrouching = entity.isCrouching();
        state.isFallFlying = entity.isFallFlying();
        state.isVisuallySwimming = entity.isVisuallySwimming();
        state.isPassenger = entity.isPassenger() && (entity.getVehicle() != null && entity.getVehicle().shouldRiderSit());
        state.speedValue = 1.0F;
        if (state.isFallFlying) {
            state.speedValue = (float)entity.getDeltaMovement().lengthSqr();
            state.speedValue /= 0.2F;
            state.speedValue = state.speedValue * (state.speedValue * state.speedValue);
        }

        if (state.speedValue < 1.0F) {
            state.speedValue = 1.0F;
        }

        state.swimAmount = entity.getSwimAmount(partialTicks);
        state.attackArm = getAttackArm(entity);
        state.useItemHand = entity.getUsedItemHand();
        state.maxCrossbowChargeDuration = CrossbowItem.getChargeDuration(entity.getUseItem(), entity);
        state.ticksUsingItem = entity.getTicksUsingItem(partialTicks);
        state.isUsingItem = entity.isUsingItem();
        state.elytraRotX = entity.elytraAnimationState.getRotX(partialTicks);
        state.elytraRotY = entity.elytraAnimationState.getRotY(partialTicks);
        state.elytraRotZ = entity.elytraAnimationState.getRotZ(partialTicks);
        state.headEquipment = getEquipmentIfRenderable(entity, EquipmentSlot.HEAD);
        state.chestEquipment = getEquipmentIfRenderable(entity, EquipmentSlot.CHEST);
        state.legsEquipment = getEquipmentIfRenderable(entity, EquipmentSlot.LEGS);
        state.feetEquipment = getEquipmentIfRenderable(entity, EquipmentSlot.FEET);
    }

    private static ItemStack getEquipmentIfRenderable(LivingEntity entity, EquipmentSlot slot) {
        ItemStack itemStack = entity.getItemBySlot(slot);
        return HumanoidArmorLayer.shouldRender(itemStack, slot) ? itemStack.copy() : ItemStack.EMPTY;
    }

    private static HumanoidArm getAttackArm(LivingEntity entity) {
        HumanoidArm mainArm = entity.getMainArm();
        return entity.swingingArm != InteractionHand.OFF_HAND ? mainArm : mainArm.getOpposite();
    }
}
