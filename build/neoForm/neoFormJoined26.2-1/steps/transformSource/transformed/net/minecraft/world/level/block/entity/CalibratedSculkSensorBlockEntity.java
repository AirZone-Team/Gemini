package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CalibratedSculkSensorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import org.jspecify.annotations.Nullable;

public class CalibratedSculkSensorBlockEntity extends SculkSensorBlockEntity {
    public CalibratedSculkSensorBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(BlockEntityTypes.CALIBRATED_SCULK_SENSOR, worldPosition, blockState);
    }

    @Override
    public VibrationSystem.User createVibrationUser() {
        return new CalibratedSculkSensorBlockEntity.VibrationUser(this.getBlockPos());
    }

    protected class VibrationUser extends SculkSensorBlockEntity.VibrationUser {
        public VibrationUser(BlockPos blockPos) {
            super(blockPos);
        }

        @Override
        public int getListenerRadius() {
            return 16;
        }

        @Override
        public boolean canReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> event, GameEvent.@Nullable Context context) {
            int comparisonType = this.getBackSignal(level, this.blockPos, CalibratedSculkSensorBlockEntity.this.getBlockState());
            return comparisonType != 0 && VibrationSystem.getGameEventFrequency(event) != comparisonType
                ? false
                : super.canReceiveVibration(level, pos, event, context);
        }

        private int getBackSignal(Level level, BlockPos pos, BlockState state) {
            Direction direction = state.getValue(CalibratedSculkSensorBlock.FACING).getOpposite();
            return level.getSignal(pos.relative(direction), direction);
        }
    }
}
