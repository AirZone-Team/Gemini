package net.minecraft.util;

import net.minecraft.core.Direction;

public class SegmentedAnglePrecision {
    private final int mask;
    private final int precision;
    private final float degreeToAngle;
    private final float angleToDegree;

    public SegmentedAnglePrecision(int bitPrecision) {
        if (bitPrecision < 2) {
            throw new IllegalArgumentException("Precision cannot be less than 2 bits");
        }

        if (bitPrecision > 30) {
            throw new IllegalArgumentException("Precision cannot be greater than 30 bits");
        }

        int twoPi = 1 << bitPrecision;
        this.mask = twoPi - 1;
        this.precision = bitPrecision;
        this.degreeToAngle = twoPi / 360.0F;
        this.angleToDegree = 360.0F / twoPi;
    }

    public boolean isSameAxis(int binaryAngleA, int binaryAngleB) {
        int semicircleMask = this.getMask() >> 1;
        return (binaryAngleA & semicircleMask) == (binaryAngleB & semicircleMask);
    }

    public int fromDirection(Direction direction) {
        if (direction.getAxis().isVertical()) {
            return 0;
        }

        int segmentedAngle2bit = direction.get2DDataValue();
        return segmentedAngle2bit << this.precision - 2;
    }

    public int fromDegreesWithTurns(float degrees) {
        return Math.round(degrees * this.degreeToAngle);
    }

    public int fromDegrees(float degrees) {
        return this.normalize(this.fromDegreesWithTurns(degrees));
    }

    public float toDegreesWithTurns(int binaryAngle) {
        return binaryAngle * this.angleToDegree;
    }

    public float toDegrees(int binaryAngle) {
        float degrees = this.toDegreesWithTurns(this.normalize(binaryAngle));
        return degrees >= 180.0F ? degrees - 360.0F : degrees;
    }

    public int normalize(int binaryAngle) {
        return binaryAngle & this.mask;
    }

    public int getMask() {
        return this.mask;
    }
}
