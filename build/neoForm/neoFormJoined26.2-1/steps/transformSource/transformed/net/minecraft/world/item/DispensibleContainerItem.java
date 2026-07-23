package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

import net.neoforged.neoforge.common.extensions.IDispensibleContainerItemExtension;

public interface DispensibleContainerItem extends IDispensibleContainerItemExtension {
    default void checkExtraContent(@Nullable LivingEntity user, Level level, ItemStack itemStack, BlockPos pos) {
    }

    @Deprecated //Forge: use the ItemStack sensitive version
    boolean emptyContents(final @Nullable LivingEntity user, final Level level, final BlockPos pos, final @Nullable BlockHitResult hitResult);
}
