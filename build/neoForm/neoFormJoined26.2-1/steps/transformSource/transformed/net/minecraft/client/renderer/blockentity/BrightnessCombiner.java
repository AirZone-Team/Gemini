package net.minecraft.client.renderer.blockentity;

import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BrightnessCombiner<S extends BlockEntity> implements DoubleBlockCombiner.Combiner<S, Int2IntFunction> {
    public Int2IntFunction acceptDouble(S first, S second) {
        return i -> LightCoordsUtil.max(
            LightCoordsUtil.getLightCoords(first.getLevel(), first.getBlockPos()), LightCoordsUtil.getLightCoords(second.getLevel(), second.getBlockPos())
        );
    }

    public Int2IntFunction acceptSingle(S single) {
        return i -> i;
    }

    public Int2IntFunction acceptNone() {
        return i -> i;
    }
}
