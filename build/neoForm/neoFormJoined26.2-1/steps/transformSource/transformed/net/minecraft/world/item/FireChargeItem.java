package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public class FireChargeItem extends Item implements ProjectileItem {
    public FireChargeItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState blockState = level.getBlockState(pos);
        boolean used = false;
        BlockState blockstate2 = blockState.getToolModifiedState(context, net.neoforged.neoforge.common.ItemAbilities.FIRESTARTER_LIGHT, false);
        if (blockstate2 == null) {
            pos = pos.relative(context.getClickedFace());
            if (BaseFireBlock.canBePlacedAt(level, pos, context.getHorizontalDirection())) {
                this.playSound(level, pos);
                level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
                level.gameEvent(context.getPlayer(), GameEvent.BLOCK_PLACE, pos);
                used = true;
            }
        } else {
            this.playSound(level, pos);
            level.setBlockAndUpdate(pos, blockstate2);
            level.gameEvent(context.getPlayer(), GameEvent.BLOCK_CHANGE, pos);
            used = true;
        }

        if (used) {
            context.getItemInHand().shrink(1);
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.FAIL;
        }
    }

    private void playSound(Level level, BlockPos pos) {
        RandomSource random = level.getRandom();
        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
    }

    @Override
    public Projectile asProjectile(Level level, Position position, ItemStack itemStack, Direction direction) {
        RandomSource random = level.getRandom();
        double dirX = random.triangle(direction.getStepX(), 0.11485000000000001);
        double dirY = random.triangle(direction.getStepY(), 0.11485000000000001);
        double dirZ = random.triangle(direction.getStepZ(), 0.11485000000000001);
        Vec3 dir = new Vec3(dirX, dirY, dirZ);
        SmallFireball fireball = new SmallFireball(level, position.x(), position.y(), position.z(), dir.normalize());
        fireball.setItem(itemStack);
        return fireball;
    }

    @Override
    public void shoot(Projectile projectile, double xd, double yd, double zd, float pow, float uncertainty) {
    }

    @Override
    public ProjectileItem.DispenseConfig createDispenseConfig() {
        return ProjectileItem.DispenseConfig.builder()
            .positionFunction((source, direction) -> DispenserBlock.getDispensePosition(source, 1.0, Vec3.ZERO))
            .uncertainty(6.6666665F)
            .power(1.0F)
            .overrideDispenseEvent(1018)
            .build();
    }

    @Override
    public boolean canPerformAction(ItemInstance stack, net.neoforged.neoforge.common.ItemAbility itemAbility) {
        return net.neoforged.neoforge.common.ItemAbilities.DEFAULT_FIRECHARGE_ACTIONS.contains(itemAbility);
    }
}
