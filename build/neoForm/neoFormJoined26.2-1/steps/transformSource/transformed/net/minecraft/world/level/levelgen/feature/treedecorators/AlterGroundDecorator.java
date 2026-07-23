package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public class AlterGroundDecorator extends TreeDecorator {
    public static final MapCodec<AlterGroundDecorator> CODEC = BlockStateProvider.CODEC.fieldOf("provider").xmap(AlterGroundDecorator::new, d -> d.provider);
    private final BlockStateProvider provider;

    public AlterGroundDecorator(BlockStateProvider provider) {
        this.provider = provider;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return TreeDecoratorType.ALTER_GROUND;
    }

    @Override
    public void place(TreeDecorator.Context context) {
        List<BlockPos> blockPositions = TreeFeature.getLowestTrunkOrRootOfTree(context);
        if (!blockPositions.isEmpty()) {
            var eventProvider = net.neoforged.neoforge.event.EventHooks.alterGround(context, blockPositions, this.provider::getOptionalState);
            int minY = blockPositions.getFirst().getY();
            blockPositions.stream().filter(pos -> pos.getY() == minY).forEach(pos -> {
                this.placeCircle(context, pos.west().north(), eventProvider);
                this.placeCircle(context, pos.east(2).north(), eventProvider);
                this.placeCircle(context, pos.west().south(2), eventProvider);
                this.placeCircle(context, pos.east(2).south(2), eventProvider);

                for (int i = 0; i < 5; i++) {
                    int placement = context.random().nextInt(64);
                    int xx = placement % 8;
                    int zz = placement / 8;
                    if (xx == 0 || xx == 7 || zz == 0 || zz == 7) {
                        this.placeCircle(context, pos.offset(-3 + xx, 0, -3 + zz), eventProvider);
                    }
                }
            });
        }
    }

    private void placeCircle(TreeDecorator.Context context, BlockPos pos) {
        placeCircle(context, pos, this.provider::getOptionalState);
    }

    private void placeCircle(TreeDecorator.Context context, BlockPos pos, net.neoforged.neoforge.event.level.AlterGroundEvent.StateProvider eventProvider) {
        for (int xx = -2; xx <= 2; xx++) {
            for (int zz = -2; zz <= 2; zz++) {
                if (Math.abs(xx) != 2 || Math.abs(zz) != 2) {
                    this.placeBlockAt(context, pos.offset(xx, 0, zz), eventProvider);
                }
            }
        }
    }

    private void placeBlockAt(TreeDecorator.Context context, BlockPos pos) {
        placeBlockAt(context, pos, this.provider::getOptionalState);
    }

    private void placeBlockAt(TreeDecorator.Context context, BlockPos pos, net.neoforged.neoforge.event.level.AlterGroundEvent.StateProvider eventProvider) {
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos cursor = pos.above(dy);
            BlockState replaceWith = eventProvider.getState(context.level(), context.random(), cursor);
            if (replaceWith != null) {
                context.setBlock(cursor, replaceWith);
                break;
            }

            if (!context.isAir(cursor) && dy < 0) {
                break;
            }
        }
    }
}
