package net.minecraft.world.level;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BlockColumn;

public final class NoiseColumn implements BlockColumn {
    private final int minY;
    private final BlockState[] column;

    public NoiseColumn(int minY, BlockState[] column) {
        this.minY = minY;
        this.column = column;
    }

    @Override
    public BlockState getBlock(int blockY) {
        int yIndex = blockY - this.minY;
        return yIndex >= 0 && yIndex < this.column.length ? this.column[yIndex] : Blocks.AIR.defaultBlockState();
    }

    @Override
    public void setBlock(int blockY, BlockState state) {
        int yIndex = blockY - this.minY;
        if (yIndex >= 0 && yIndex < this.column.length) {
            this.column[yIndex] = state;
        } else {
            throw new IllegalArgumentException("Outside of column height: " + blockY);
        }
    }
}
