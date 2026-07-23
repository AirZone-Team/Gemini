package net.minecraft.world.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public abstract class ProjectileWeaponItem extends Item {
    public static final Predicate<ItemStack> ARROW_ONLY = itemStack -> itemStack.is(ItemTags.ARROWS);
    public static final Predicate<ItemStack> ARROW_OR_FIREWORK = ARROW_ONLY.or(itemStack -> itemStack.is(Items.FIREWORK_ROCKET));

    public ProjectileWeaponItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * @deprecated Use ItemStack sensitive version {@link ProjectileWeaponItem#getSupportedHeldProjectiles(ItemStack)}
     */
    @Deprecated
    public Predicate<ItemStack> getSupportedHeldProjectiles() {
        return this.getAllSupportedProjectiles();
    }

    /**
     * @deprecated Use ItemStack sensitive version {@link ProjectileWeaponItem#getAllSupportedProjectiles(ItemStack)}
     */
    @Deprecated
    public abstract Predicate<ItemStack> getAllSupportedProjectiles();

    /**
     * Override this method if the weapon stack allows special projectile that would only be used if it's in hand.
     * The default return value is a union-predicate of {@link ProjectileWeaponItem#getAllSupportedProjectiles(ItemStack)}
     * and {@link ProjectileWeaponItem#getSupportedHeldProjectiles()}
     *
     * @param stack The ProjectileWeapon stack
     * @return A predicate that returns true for supported projectile stack in hand
     */
    public Predicate<ItemStack> getSupportedHeldProjectiles(ItemStack stack) {
        return getAllSupportedProjectiles(stack).or(getSupportedHeldProjectiles());
    }

    /**
     * Override this method if the allowed projectile is weapon stack dependent.
     *
     * @param stack The ProjectileWeapon stack
     * @return A predicate that returns true for all supported projectile stack
     */
    public Predicate<ItemStack> getAllSupportedProjectiles(ItemStack stack) {
        return getAllSupportedProjectiles();
    }

    public static ItemStack getHeldProjectile(LivingEntity entity, Predicate<ItemStack> valid) {
        if (valid.test(entity.getItemInHand(InteractionHand.OFF_HAND))) {
            return entity.getItemInHand(InteractionHand.OFF_HAND);
        } else {
            return valid.test(entity.getItemInHand(InteractionHand.MAIN_HAND)) ? entity.getItemInHand(InteractionHand.MAIN_HAND) : ItemStack.EMPTY;
        }
    }

    public abstract int getDefaultProjectileRange();

    protected void shoot(
        ServerLevel level,
        LivingEntity shooter,
        InteractionHand hand,
        ItemStack weapon,
        List<ItemStack> projectiles,
        float power,
        float uncertainty,
        boolean isCrit,
        @Nullable LivingEntity targetOverride
    ) {
        float maxAngle = EnchantmentHelper.processProjectileSpread(level, weapon, shooter, 0.0F);
        float angleStep = projectiles.size() == 1 ? 0.0F : 2.0F * maxAngle / (projectiles.size() - 1);
        float angleOffset = (projectiles.size() - 1) % 2 * angleStep / 2.0F;
        float direction = 1.0F;

        for (int i = 0; i < projectiles.size(); i++) {
            ItemStack projectile = projectiles.get(i);
            if (!projectile.isEmpty()) {
                float angle = angleOffset + direction * ((i + 1) / 2) * angleStep;
                direction = -direction;
                int index = i;
                Projectile.spawnProjectile(
                    this.createProjectile(level, shooter, weapon, projectile, isCrit),
                    level,
                    projectile,
                    projectileEntity -> this.shootProjectile(shooter, projectileEntity, index, power, uncertainty, angle, targetOverride)
                );
                weapon.hurtAndBreak(this.getDurabilityUse(projectile), shooter, hand.asEquipmentSlot());
                if (weapon.isEmpty()) {
                    break;
                }
            }
        }
    }

    protected int getDurabilityUse(ItemStack projectile) {
        return 1;
    }

    protected abstract void shootProjectile(
        final LivingEntity shooter,
        final Projectile projectileEntity,
        final int index,
        final float power,
        final float uncertainty,
        final float angle,
        final @Nullable LivingEntity targetOverrride
    );

    protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack projectile, boolean isCrit) {
        ArrowItem arrowItem = projectile.getItem() instanceof ArrowItem arrow ? arrow : (ArrowItem)Items.ARROW;
        AbstractArrow arrow = arrowItem.createArrow(level, projectile, shooter, weapon);
        if (isCrit) {
            arrow.setCritArrow(true);
        }

        return customArrow(arrow, projectile, weapon);
    }

    protected static List<ItemStack> draw(ItemStack weapon, ItemStack projectile, LivingEntity shooter) {
        if (projectile.isEmpty()) {
            return List.of();
        }

        int numProjectiles = shooter.level() instanceof ServerLevel serverLevel ? EnchantmentHelper.processProjectileCount(serverLevel, weapon, shooter, 1) : 1;
        List<ItemStack> drawn = new ArrayList<>(numProjectiles);
        ItemStack projectileCopy = projectile.copy();

        for (int i = 0; i < numProjectiles; i++) {
            ItemStack drawnStack = useAmmo(weapon, i == 0 ? projectile : projectileCopy, shooter, i > 0);
            if (!drawnStack.isEmpty()) {
                drawn.add(drawnStack);
            }
        }

        return drawn;
    }

    protected static ItemStack useAmmo(ItemStack weapon, ItemStack projectile, LivingEntity holder, boolean forceInfinite) {
        // Neo: Adjust this check to respect ArrowItem#isInfinite, bypassing processAmmoUse if true.
        int ammoToUse = !forceInfinite && holder.level() instanceof ServerLevel serverLevel && !(holder.hasInfiniteMaterials() || (projectile.getItem() instanceof ArrowItem ai && ai.isInfinite(projectile, weapon, holder)))
            ? EnchantmentHelper.processAmmoUse(serverLevel, weapon, projectile, 1)
            : 0;
        if (ammoToUse > projectile.getCount()) {
            return ItemStack.EMPTY;
        }

        if (ammoToUse == 0) {
            ItemStack copy = projectile.copyWithCount(1);
            copy.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
            return copy;
        }

        ItemStack used = projectile.split(ammoToUse);
        if (projectile.isEmpty() && holder instanceof Player player) {
            player.getInventory().removeItem(projectile);
        }

        return used;
    }

    public AbstractArrow customArrow(AbstractArrow arrow, ItemStack projectileStack, ItemStack weaponStack) {
        return arrow;
    }

    /**
     * Neo: Controls what ammo ItemStack that Creative Mode should return if the player has no valid ammo in inventory.
     * Modded weapons should override this to return their own ammo if they do not use vanilla arrows.
     * @param player The player (if in context) firing the weapon
     * @param projectileWeaponItem The weapon ItemStack the ammo is for
     * @return The default ammo ItemStack for this weapon
     */
    public ItemStack getDefaultCreativeAmmo(@Nullable Player player, ItemStack projectileWeaponItem) {
        return Items.ARROW.getDefaultInstance();
    }
}
