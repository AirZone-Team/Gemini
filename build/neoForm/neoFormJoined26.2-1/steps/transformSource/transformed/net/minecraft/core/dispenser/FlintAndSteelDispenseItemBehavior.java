package net.minecraft.core.dispenser;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

public class FlintAndSteelDispenseItemBehavior extends OptionalDispenseItemBehavior {
    @Override
    protected ItemStack execute(BlockSource source, ItemStack dispensed) {
        ServerLevel level = source.level();
        this.setSuccess(true);
        Direction facing = source.state().getValue(DispenserBlock.FACING);
        BlockPos targetPos = source.pos().relative(facing);
        BlockState target = level.getBlockState(targetPos);
        if (!tryIgniteExplosiveEntities(level, targetPos)) {
            if (BaseFireBlock.canBePlacedAt(level, targetPos, facing)) {
                level.setBlockAndUpdate(targetPos, BaseFireBlock.getState(level, targetPos));
                level.gameEvent(null, GameEvent.BLOCK_PLACE, targetPos);
            } else if (target.getToolModifiedState(new net.minecraft.world.item.context.UseOnContext(level, null, net.minecraft.world.InteractionHand.MAIN_HAND, dispensed, new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(targetPos), facing.getOpposite(), targetPos, false)), net.neoforged.neoforge.common.ItemAbilities.FIRESTARTER_LIGHT, false) instanceof BlockState blockstate2) {
                level.setBlockAndUpdate(targetPos, blockstate2);
                level.gameEvent(null, GameEvent.BLOCK_CHANGE, targetPos);
            } else if (target.isFlammable(level, targetPos, source.state().getValue(DispenserBlock.FACING).getOpposite())) {
                if (target.onCaughtFire(level, targetPos, source.state().getValue(DispenserBlock.FACING).getOpposite(), null)) {
                    if (target.getBlock() instanceof TntBlock)
                    level.removeBlock(targetPos, false);
                } else {
                    this.setSuccess(false);
                }
            }
        }

        if (this.isSuccess()) {
            dispensed.hurtAndBreak(1, level, null, item -> {});
        }

        return dispensed;
    }

    private static boolean tryIgniteExplosiveEntities(ServerLevel level, BlockPos pos) {
        List<SulfurCube> entities = level.getEntitiesOfClass(SulfurCube.class, new AABB(pos), SulfurCube::canExplode);
        if (entities.isEmpty()) {
            return false;
        }

        entities.getFirst().primeTime(false);
        return true;
    }
}
