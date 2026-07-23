package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.state.BlockState;

public class HangingSignItem extends SignItem {
    public HangingSignItem(Block hangingSign, Block wallHangingSign, Item.Properties properties) {
        super(properties, hangingSign, wallHangingSign, Direction.UP);
    }

    @Override
    protected boolean canPlace(LevelReader level, BlockState possibleState, BlockPos pos) {
        return possibleState.getBlock() instanceof WallHangingSignBlock hangingSign && !hangingSign.canPlace(possibleState, level, pos)
            ? false
            : super.canPlace(level, possibleState, pos);
    }
}
