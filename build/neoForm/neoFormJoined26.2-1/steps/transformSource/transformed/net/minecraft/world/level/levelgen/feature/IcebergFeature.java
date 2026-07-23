package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;

public class IcebergFeature extends Feature<BlockStateConfiguration> {
    public IcebergFeature(Codec<BlockStateConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<BlockStateConfiguration> context) {
        BlockPos origin = context.origin();
        WorldGenLevel level = context.level();
        origin = new BlockPos(origin.getX(), context.chunkGenerator().getSeaLevel(), origin.getZ());
        RandomSource random = context.random();
        boolean snowOnTop = random.nextDouble() > 0.7;
        BlockState mainBlockState = context.config().state;
        double shapeAngle = random.nextDouble() * 2.0 * Math.PI;
        int shapeEllipseA = 11 - random.nextInt(5);
        int shapeEllipseC = 3 + random.nextInt(3);
        boolean isEllipse = random.nextDouble() > 0.7;
        int maxWidthRoundIceberg = 11;
        int overWaterHeight = isEllipse ? random.nextInt(6) + 6 : random.nextInt(15) + 3;
        if (!isEllipse && random.nextDouble() > 0.9) {
            overWaterHeight += random.nextInt(19) + 7;
        }

        int underWaterHeight = Math.min(overWaterHeight + random.nextInt(11), 18);
        int width = Math.min(overWaterHeight + random.nextInt(7) - random.nextInt(5), 11);
        int a = isEllipse ? shapeEllipseA : 11;

        for (int xo = -a; xo < a; xo++) {
            for (int zo = -a; zo < a; zo++) {
                for (int yOff = 0; yOff < overWaterHeight; yOff++) {
                    int radius = isEllipse
                        ? this.heightDependentRadiusEllipse(yOff, overWaterHeight, width)
                        : this.heightDependentRadiusRound(random, yOff, overWaterHeight, width);
                    if (isEllipse || xo < radius) {
                        this.generateIcebergBlock(
                            level, random, origin, overWaterHeight, xo, yOff, zo, radius, a, isEllipse, shapeEllipseC, shapeAngle, snowOnTop, mainBlockState
                        );
                    }
                }
            }
        }

        this.smooth(level, origin, width, overWaterHeight, isEllipse, shapeEllipseA);

        for (int xo = -a; xo < a; xo++) {
            for (int zo = -a; zo < a; zo++) {
                for (int yOff = -1; yOff > -underWaterHeight; yOff--) {
                    int newA = isEllipse ? Mth.ceil(a * (1.0F - (float)Math.pow(yOff, 2.0) / (underWaterHeight * 8.0F))) : a;
                    int radius = this.heightDependentRadiusSteep(random, -yOff, underWaterHeight, width);
                    if (xo < radius) {
                        this.generateIcebergBlock(
                            level,
                            random,
                            origin,
                            underWaterHeight,
                            xo,
                            yOff,
                            zo,
                            radius,
                            newA,
                            isEllipse,
                            shapeEllipseC,
                            shapeAngle,
                            snowOnTop,
                            mainBlockState
                        );
                    }
                }
            }
        }

        boolean doCutOut = isEllipse ? random.nextDouble() > 0.1 : random.nextDouble() > 0.7;
        if (doCutOut) {
            this.generateCutOut(random, level, width, overWaterHeight, origin, isEllipse, shapeEllipseA, shapeAngle, shapeEllipseC);
        }

        return true;
    }

    private void generateCutOut(
        RandomSource random,
        LevelAccessor level,
        int width,
        int height,
        BlockPos globalOrigin,
        boolean isEllipse,
        int shapeEllipseA,
        double shapeAngle,
        int shapeEllipseC
    ) {
        int randomSignX = random.nextBoolean() ? -1 : 1;
        int randomSignZ = random.nextBoolean() ? -1 : 1;
        int xOff = random.nextInt(Math.max(width / 2 - 2, 1));
        if (random.nextBoolean()) {
            xOff = width / 2 + 1 - random.nextInt(Math.max(width - width / 2 - 1, 1));
        }

        int zOff = random.nextInt(Math.max(width / 2 - 2, 1));
        if (random.nextBoolean()) {
            zOff = width / 2 + 1 - random.nextInt(Math.max(width - width / 2 - 1, 1));
        }

        if (isEllipse) {
            xOff = zOff = random.nextInt(Math.max(shapeEllipseA - 5, 1));
        }

        BlockPos localOrigin = new BlockPos(randomSignX * xOff, 0, randomSignZ * zOff);
        double angle = isEllipse ? shapeAngle + (Math.PI / 2) : random.nextDouble() * 2.0 * Math.PI;

        for (int yOff = 0; yOff < height - 3; yOff++) {
            int radius = this.heightDependentRadiusRound(random, yOff, height, width);
            this.carve(radius, yOff, globalOrigin, level, false, angle, localOrigin, shapeEllipseA, shapeEllipseC);
        }

        for (int yOff = -1; yOff > -height + random.nextInt(5); yOff--) {
            int radius = this.heightDependentRadiusSteep(random, -yOff, height, width);
            this.carve(radius, yOff, globalOrigin, level, true, angle, localOrigin, shapeEllipseA, shapeEllipseC);
        }
    }

    private void carve(
        int radius,
        int yOff,
        BlockPos globalOrigin,
        LevelAccessor level,
        boolean underWater,
        double angle,
        BlockPos localOrigin,
        int shapeEllipseA,
        int shapeEllipseC
    ) {
        int a = radius + 1 + shapeEllipseA / 3;
        int c = Math.min(radius - 3, 3) + shapeEllipseC / 2 - 1;

        for (int xo = -a; xo < a; xo++) {
            for (int zo = -a; zo < a; zo++) {
                double signedDist = this.signedDistanceEllipse(xo, zo, localOrigin, a, c, angle);
                if (signedDist < 0.0) {
                    BlockPos pos = globalOrigin.offset(xo, yOff, zo);
                    BlockState state = level.getBlockState(pos);
                    if (isIcebergState(state) || state.is(Blocks.SNOW_BLOCK)) {
                        if (underWater) {
                            this.setBlock(level, pos, Blocks.WATER.defaultBlockState());
                        } else {
                            this.setBlock(level, pos, Blocks.AIR.defaultBlockState());
                            this.removeFloatingSnowLayer(level, pos);
                        }
                    }
                }
            }
        }
    }

    private void removeFloatingSnowLayer(LevelAccessor level, BlockPos pos) {
        if (level.getBlockState(pos.above()).is(Blocks.SNOW)) {
            this.setBlock(level, pos.above(), Blocks.AIR.defaultBlockState());
        }
    }

    private void generateIcebergBlock(
        LevelAccessor level,
        RandomSource random,
        BlockPos origin,
        int height,
        int xo,
        int yOff,
        int zo,
        int radius,
        int a,
        boolean isEllipse,
        int shapeEllipseC,
        double shapeAngle,
        boolean snowOnTop,
        BlockState mainBlockState
    ) {
        double signedDist = isEllipse
            ? this.signedDistanceEllipse(xo, zo, BlockPos.ZERO, a, this.getEllipseC(yOff, height, shapeEllipseC), shapeAngle)
            : this.signedDistanceCircle(xo, zo, BlockPos.ZERO, radius, random);
        if (signedDist < 0.0) {
            BlockPos pos = origin.offset(xo, yOff, zo);
            double compareVal = isEllipse ? -0.5 : -6 - random.nextInt(3);
            if (signedDist > compareVal && random.nextDouble() > 0.9) {
                return;
            }

            this.setIcebergBlock(pos, level, random, height - yOff, height, isEllipse, snowOnTop, mainBlockState);
        }
    }

    private void setIcebergBlock(
        BlockPos pos, LevelAccessor level, RandomSource random, int hDiff, int height, boolean isEllipse, boolean snowOnTop, BlockState mainBlockState
    ) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.ICE) || state.is(Blocks.WATER)) {
            boolean randomness = !isEllipse || random.nextDouble() > 0.05;
            int divisor = isEllipse ? 3 : 2;
            if (snowOnTop && !state.is(Blocks.WATER) && hDiff <= random.nextInt(Math.max(1, height / divisor)) + height * 0.6 && randomness) {
                this.setBlock(level, pos, Blocks.SNOW_BLOCK.defaultBlockState());
            } else {
                this.setBlock(level, pos, mainBlockState);
            }
        }
    }

    private int getEllipseC(int yOff, int height, int shapeEllipseC) {
        int c = shapeEllipseC;
        if (yOff > 0 && height - yOff <= 3) {
            c -= 4 - (height - yOff);
        }

        return c;
    }

    private double signedDistanceCircle(int xo, int zo, BlockPos origin, int radius, RandomSource random) {
        float off = 10.0F * Mth.clamp(random.nextFloat(), 0.2F, 0.8F) / radius;
        return off + Math.pow(xo - origin.getX(), 2.0) + Math.pow(zo - origin.getZ(), 2.0) - Math.pow(radius, 2.0);
    }

    private double signedDistanceEllipse(int xo, int zo, BlockPos origin, int a, int c, double angle) {
        return Math.pow(((xo - origin.getX()) * Math.cos(angle) - (zo - origin.getZ()) * Math.sin(angle)) / a, 2.0)
            + Math.pow(((xo - origin.getX()) * Math.sin(angle) + (zo - origin.getZ()) * Math.cos(angle)) / c, 2.0)
            - 1.0;
    }

    private int heightDependentRadiusRound(RandomSource random, int yOff, int height, int width) {
        float k = 3.5F - random.nextFloat();
        float scale = (1.0F - (float)Math.pow(yOff, 2.0) / (height * k)) * width;
        if (height > 15 + random.nextInt(5)) {
            int tempYOff = yOff < 3 + random.nextInt(6) ? yOff / 2 : yOff;
            scale = (1.0F - tempYOff / (height * k * 0.4F)) * width;
        }

        return Mth.ceil(scale / 2.0F);
    }

    private int heightDependentRadiusEllipse(int yOff, int height, int width) {
        float k = 1.0F;
        float scale = (1.0F - (float)Math.pow(yOff, 2.0) / (height * 1.0F)) * width;
        return Mth.ceil(scale / 2.0F);
    }

    private int heightDependentRadiusSteep(RandomSource random, int yOff, int height, int width) {
        float k = 1.0F + random.nextFloat() / 2.0F;
        float scale = (1.0F - yOff / (height * k)) * width;
        return Mth.ceil(scale / 2.0F);
    }

    private static boolean isIcebergState(BlockState state) {
        return state.is(Blocks.PACKED_ICE) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.BLUE_ICE);
    }

    private boolean belowIsAir(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos.below()).isAir();
    }

    private void smooth(LevelAccessor level, BlockPos origin, int width, int height, boolean isEllipse, int shapeEllipseA) {
        int a = isEllipse ? shapeEllipseA : width / 2;

        for (int x = -a; x <= a; x++) {
            for (int z = -a; z <= a; z++) {
                for (int yOff = 0; yOff <= height; yOff++) {
                    BlockPos pos = origin.offset(x, yOff, z);
                    BlockState state = level.getBlockState(pos);
                    if (isIcebergState(state) || state.is(Blocks.SNOW)) {
                        if (this.belowIsAir(level, pos)) {
                            this.setBlock(level, pos, Blocks.AIR.defaultBlockState());
                            this.setBlock(level, pos.above(), Blocks.AIR.defaultBlockState());
                        } else if (isIcebergState(state)) {
                            BlockState[] sides = new BlockState[]{
                                level.getBlockState(pos.west()),
                                level.getBlockState(pos.east()),
                                level.getBlockState(pos.north()),
                                level.getBlockState(pos.south())
                            };
                            int counter = 0;

                            for (BlockState side : sides) {
                                if (!isIcebergState(side)) {
                                    counter++;
                                }
                            }

                            if (counter >= 3) {
                                this.setBlock(level, pos, Blocks.AIR.defaultBlockState());
                            }
                        }
                    }
                }
            }
        }
    }
}
