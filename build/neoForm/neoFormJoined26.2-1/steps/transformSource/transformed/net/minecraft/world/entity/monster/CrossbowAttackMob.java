package net.minecraft.world.entity.monster;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

public interface CrossbowAttackMob extends RangedAttackMob {
    void setChargingCrossbow(final boolean isCharging);

    @Nullable LivingEntity getTarget();

    void onCrossbowAttackPerformed();

    default void performCrossbowAttack(LivingEntity body, float crossbowPower) {
        InteractionHand hand = ProjectileUtil.getWeaponHoldingHand(body, item -> item instanceof CrossbowItem);
        ItemStack usedItem = body.getItemInHand(hand);
        if (usedItem.getItem() instanceof CrossbowItem crossbow) {
            crossbow.performShooting(body.level(), body, hand, usedItem, crossbowPower, 14 - body.level().getDifficulty().getId() * 4, this.getTarget());
        }

        this.onCrossbowAttackPerformed();
    }
}
