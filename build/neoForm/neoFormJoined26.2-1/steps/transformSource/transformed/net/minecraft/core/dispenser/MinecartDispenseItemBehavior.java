package net.minecraft.core.dispenser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

public class MinecartDispenseItemBehavior extends DefaultDispenseItemBehavior {
    private final DefaultDispenseItemBehavior defaultDispenseItemBehavior = new DefaultDispenseItemBehavior();
    private final EntityType<? extends AbstractMinecart> entityType;

    public MinecartDispenseItemBehavior(EntityType<? extends AbstractMinecart> entityType) {
        this.entityType = entityType;
    }

    @Override
    public ItemStack execute(BlockSource source, ItemStack dispensed) {
        Direction direction = source.state().getValue(DispenserBlock.FACING);
        ServerLevel level = source.level();
        Vec3 center = source.center();
        double spawnX = center.x() + direction.getStepX() * 1.125;
        double spawnY = Math.floor(center.y()) + direction.getStepY();
        double spawnZ = center.z() + direction.getStepZ() * 1.125;
        BlockPos front = source.pos().relative(direction);
        BlockState blockFront = level.getBlockState(front);
        double yOffset;
        if (blockFront.is(BlockTags.RAILS)) {
            if (getRailShape(blockFront, level,front).isSlope()) {
                yOffset = 0.6;
            } else {
                yOffset = 0.1;
            }
        } else {
            if (!blockFront.isAir()) {
                return this.defaultDispenseItemBehavior.dispense(source, dispensed);
            }

            BlockState blockBelow = level.getBlockState(front.below());
            if (!blockBelow.is(BlockTags.RAILS)) {
                return this.defaultDispenseItemBehavior.dispense(source, dispensed);
            }

            if (direction != Direction.DOWN && getRailShape(blockBelow, level, front.below()).isSlope()) {
                yOffset = -0.4;
            } else {
                yOffset = -0.9;
            }
        }

        Vec3 spawnPos = new Vec3(spawnX, spawnY + yOffset, spawnZ);
        AbstractMinecart minecart = AbstractMinecart.createMinecart(
            level, spawnPos.x, spawnPos.y, spawnPos.z, this.entityType, EntitySpawnReason.DISPENSER, dispensed, null
        );
        if (minecart != null) {
            level.addFreshEntity(minecart);
            dispensed.shrink(1);
        }

        return dispensed;
    }

    private static RailShape getRailShape(BlockState blockFront, ServerLevel level, BlockPos pos) {
        return blockFront.getBlock() instanceof BaseRailBlock baserailblock ? baserailblock.getRailDirection(blockFront, level, pos, null) : RailShape.NORTH_SOUTH;
    }

    @Override
    protected void playSound(BlockSource source) {
        source.level().levelEvent(1000, source.pos(), 0);
    }
}
