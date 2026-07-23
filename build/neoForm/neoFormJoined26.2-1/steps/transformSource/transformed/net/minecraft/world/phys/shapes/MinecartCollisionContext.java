package net.minecraft.world.phys.shapes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.jspecify.annotations.Nullable;

public class MinecartCollisionContext extends EntityCollisionContext {
    private @Nullable BlockPos ingoreBelow;
    private @Nullable BlockPos slopeIgnore;

    protected MinecartCollisionContext(AbstractMinecart entity, boolean alwaysStandOnFluid) {
        super(entity, alwaysStandOnFluid, false);
        this.setupContext(entity);
    }

    private void setupContext(AbstractMinecart entity) {
        BlockPos currentRailPos = entity.getCurrentBlockPosOrRailBelow();
        BlockState currentState = entity.level().getBlockState(currentRailPos);
        boolean onRails = BaseRailBlock.isRail(currentState);
        if (onRails) {
            this.ingoreBelow = currentRailPos.below();
            RailShape shape = ((BaseRailBlock)currentState.getBlock()).getRailDirection(currentState, entity.level(), currentRailPos, entity);
            if (shape.isSlope()) {
                this.slopeIgnore = switch (shape) {
                    case ASCENDING_EAST -> currentRailPos.east();
                    case ASCENDING_WEST -> currentRailPos.west();
                    case ASCENDING_NORTH -> currentRailPos.north();
                    case ASCENDING_SOUTH -> currentRailPos.south();
                    default -> null;
                };
            }
        }
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, CollisionGetter collisionGetter, BlockPos pos) {
        return !pos.equals(this.ingoreBelow) && !pos.equals(this.slopeIgnore) ? super.getCollisionShape(state, collisionGetter, pos) : Shapes.empty();
    }
}
