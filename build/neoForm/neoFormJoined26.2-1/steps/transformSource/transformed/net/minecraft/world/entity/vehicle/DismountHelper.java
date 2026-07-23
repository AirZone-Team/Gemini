package net.minecraft.world.entity.vehicle;

import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class DismountHelper {
    public static int[][] offsetsForDirection(Direction forward) {
        Direction right = forward.getClockWise();
        Direction left = right.getOpposite();
        Direction back = forward.getOpposite();
        return new int[][]{
            {right.getStepX(), right.getStepZ()},
            {left.getStepX(), left.getStepZ()},
            {back.getStepX() + right.getStepX(), back.getStepZ() + right.getStepZ()},
            {back.getStepX() + left.getStepX(), back.getStepZ() + left.getStepZ()},
            {forward.getStepX() + right.getStepX(), forward.getStepZ() + right.getStepZ()},
            {forward.getStepX() + left.getStepX(), forward.getStepZ() + left.getStepZ()},
            {back.getStepX(), back.getStepZ()},
            {forward.getStepX(), forward.getStepZ()}
        };
    }

    public static boolean isBlockFloorValid(double blockFloorHeight) {
        return !Double.isInfinite(blockFloorHeight) && blockFloorHeight < 1.0;
    }

    public static boolean canDismountTo(CollisionGetter level, LivingEntity passenger, AABB box) {
        for (VoxelShape collision : level.getBlockCollisions(passenger, box)) {
            if (!collision.isEmpty()) {
                return false;
            }
        }

        return level.getWorldBorder().isWithinBounds(box);
    }

    public static boolean canDismountTo(CollisionGetter level, Vec3 location, LivingEntity passenger, Pose dismountPose) {
        return canDismountTo(level, passenger, passenger.getLocalBoundsForPose(dismountPose).move(location));
    }

    public static VoxelShape nonClimbableShape(BlockGetter level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        return !blockState.is(BlockTags.CLIMBABLE) && (!(blockState.getBlock() instanceof TrapDoorBlock) || !blockState.getValue(TrapDoorBlock.OPEN))
            ? blockState.getCollisionShape(level, pos)
            : Shapes.empty();
    }

    public static double findCeilingFrom(BlockPos pos, int blocks, Function<BlockPos, VoxelShape> shapeGetter) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        int y = 0;

        while (y < blocks) {
            VoxelShape collisionShape = shapeGetter.apply(cursor);
            if (!collisionShape.isEmpty()) {
                return pos.getY() + y + collisionShape.min(Direction.Axis.Y);
            }

            y++;
            cursor.move(Direction.UP);
        }

        return Double.POSITIVE_INFINITY;
    }

    public static @Nullable Vec3 findSafeDismountLocation(EntityType<?> type, CollisionGetter level, BlockPos blockPos, boolean checkDangerous) {
        if (checkDangerous && type.isBlockDangerous(level.getBlockState(blockPos))) {
            return null;
        }

        double floorHeight = level.getBlockFloorHeight(nonClimbableShape(level, blockPos), () -> nonClimbableShape(level, blockPos.below()));
        if (!isBlockFloorValid(floorHeight)) {
            return null;
        }

        if (checkDangerous && floorHeight <= 0.0 && type.isBlockDangerous(level.getBlockState(blockPos.below()))) {
            return null;
        }

        Vec3 position = Vec3.upFromBottomCenterOf(blockPos, floorHeight);
        AABB aabb = type.getDimensions().makeBoundingBox(position);

        for (VoxelShape shape : level.getBlockCollisions(null, aabb)) {
            if (!shape.isEmpty()) {
                return null;
            }
        }

        if (type != EntityTypes.PLAYER
            || !level.getBlockState(blockPos).is(BlockTags.INVALID_SPAWN_INSIDE) && !level.getBlockState(blockPos.above()).is(BlockTags.INVALID_SPAWN_INSIDE)) {
            return !level.getWorldBorder().isWithinBounds(aabb) ? null : position;
        } else {
            return null;
        }
    }
}
