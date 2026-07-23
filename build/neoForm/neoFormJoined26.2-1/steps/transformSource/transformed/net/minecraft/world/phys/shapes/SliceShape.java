package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;

public class SliceShape extends VoxelShape {
    private final VoxelShape delegate;
    private final Direction.Axis axis;
    private static final DoubleList SLICE_COORDS = new CubePointRange(1);

    public SliceShape(VoxelShape delegate, Direction.Axis axis, int point) {
        super(makeSlice(delegate.shape, axis, point));
        this.delegate = delegate;
        this.axis = axis;
    }

    private static DiscreteVoxelShape makeSlice(DiscreteVoxelShape delegate, Direction.Axis axis, int point) {
        return new SubShape(
            delegate,
            axis.choose(point, 0, 0),
            axis.choose(0, point, 0),
            axis.choose(0, 0, point),
            axis.choose(point + 1, delegate.xSize, delegate.xSize),
            axis.choose(delegate.ySize, point + 1, delegate.ySize),
            axis.choose(delegate.zSize, delegate.zSize, point + 1)
        );
    }

    @Override
    public DoubleList getCoords(Direction.Axis axis) {
        return axis == this.axis ? SLICE_COORDS : this.delegate.getCoords(axis);
    }
}
