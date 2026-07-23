package net.minecraft.world.level.levelgen;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;

public abstract class Column {
    public static Column.Range around(int lowest, int highest) {
        return new Column.Range(lowest - 1, highest + 1);
    }

    public static Column.Range inside(int floor, int ceiling) {
        return new Column.Range(floor, ceiling);
    }

    public static Column below(int ceiling) {
        return new Column.Ray(ceiling, false);
    }

    public static Column fromHighest(int highest) {
        return new Column.Ray(highest + 1, false);
    }

    public static Column above(int floor) {
        return new Column.Ray(floor, true);
    }

    public static Column fromLowest(int lowest) {
        return new Column.Ray(lowest - 1, true);
    }

    public static Column line() {
        return Column.Line.INSTANCE;
    }

    public static Column create(OptionalInt floor, OptionalInt ceiling) {
        if (floor.isPresent() && ceiling.isPresent()) {
            return inside(floor.getAsInt(), ceiling.getAsInt());
        } else if (floor.isPresent()) {
            return above(floor.getAsInt());
        } else {
            return ceiling.isPresent() ? below(ceiling.getAsInt()) : line();
        }
    }

    public abstract OptionalInt getCeiling();

    public abstract OptionalInt getFloor();

    public abstract OptionalInt getHeight();

    public Column withFloor(OptionalInt floor) {
        return create(floor, this.getCeiling());
    }

    public Column withCeiling(OptionalInt ceiling) {
        return create(this.getFloor(), ceiling);
    }

    public static Optional<Column> scan(
        LevelSimulatedReader level, BlockPos pos, int searchRange, Predicate<BlockState> insideColumn, Predicate<BlockState> validEdge
    ) {
        BlockPos.MutableBlockPos mutablePos = pos.mutable();
        if (!level.isStateAtPosition(pos, insideColumn)) {
            return Optional.empty();
        }

        int nearestEmptyY = pos.getY();
        OptionalInt ceiling = scanDirection(level, searchRange, insideColumn, validEdge, mutablePos, nearestEmptyY, Direction.UP);
        OptionalInt floor = scanDirection(level, searchRange, insideColumn, validEdge, mutablePos, nearestEmptyY, Direction.DOWN);
        return Optional.of(create(floor, ceiling));
    }

    private static OptionalInt scanDirection(
        LevelSimulatedReader level,
        int searchRange,
        Predicate<BlockState> insideColumn,
        Predicate<BlockState> validEdge,
        BlockPos.MutableBlockPos mutablePos,
        int nearestEmptyY,
        Direction direction
    ) {
        mutablePos.setY(nearestEmptyY);

        for (int i = 1; i < searchRange && level.isStateAtPosition(mutablePos, insideColumn); i++) {
            mutablePos.move(direction);
        }

        return level.isStateAtPosition(mutablePos, validEdge) ? OptionalInt.of(mutablePos.getY()) : OptionalInt.empty();
    }

    public static final class Line extends Column {
        private static final Column.Line INSTANCE = new Column.Line();

        private Line() {
        }

        @Override
        public OptionalInt getCeiling() {
            return OptionalInt.empty();
        }

        @Override
        public OptionalInt getFloor() {
            return OptionalInt.empty();
        }

        @Override
        public OptionalInt getHeight() {
            return OptionalInt.empty();
        }

        @Override
        public String toString() {
            return "C(-)";
        }
    }

    public static final class Range extends Column {
        private final int floor;
        private final int ceiling;

        public Range(int floor, int ceiling) {
            this.floor = floor;
            this.ceiling = ceiling;
            if (this.height() < 0) {
                throw new IllegalArgumentException("Column of negative height: " + this);
            }
        }

        @Override
        public OptionalInt getCeiling() {
            return OptionalInt.of(this.ceiling);
        }

        @Override
        public OptionalInt getFloor() {
            return OptionalInt.of(this.floor);
        }

        @Override
        public OptionalInt getHeight() {
            return OptionalInt.of(this.height());
        }

        public int ceiling() {
            return this.ceiling;
        }

        public int floor() {
            return this.floor;
        }

        public int height() {
            return this.ceiling - this.floor - 1;
        }

        @Override
        public String toString() {
            return "C(" + this.ceiling + "-" + this.floor + ")";
        }
    }

    public static final class Ray extends Column {
        private final int edge;
        private final boolean pointingUp;

        public Ray(int edge, boolean pointingUp) {
            this.edge = edge;
            this.pointingUp = pointingUp;
        }

        @Override
        public OptionalInt getCeiling() {
            return this.pointingUp ? OptionalInt.empty() : OptionalInt.of(this.edge);
        }

        @Override
        public OptionalInt getFloor() {
            return this.pointingUp ? OptionalInt.of(this.edge) : OptionalInt.empty();
        }

        @Override
        public OptionalInt getHeight() {
            return OptionalInt.empty();
        }

        @Override
        public String toString() {
            return this.pointingUp ? "C(" + this.edge + "-)" : "C(-" + this.edge + ")";
        }
    }
}
