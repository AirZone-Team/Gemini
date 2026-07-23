package net.minecraft.client.renderer.block;

import java.util.Map;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BlockStateModelSet {
    private final Map<BlockState, BlockStateModel> modelByState;
    private final BlockStateModel missingModel;

    public BlockStateModelSet(Map<BlockState, BlockStateModel> modelByState, BlockStateModel missingModel) {
        this.modelByState = modelByState;
        this.missingModel = missingModel;
    }

    public BlockStateModel get(BlockState state) {
        return this.modelByState.getOrDefault(state, this.missingModel);
    }

    public BlockStateModel missingModel() {
        return this.missingModel;
    }

    public Material.Baked getParticleMaterial(BlockState blockState) {
        return this.getParticleMaterial(blockState, net.minecraft.client.renderer.block.BlockAndTintGetter.EMPTY, net.minecraft.core.BlockPos.ZERO);
    }

    public Material.Baked getParticleMaterial(BlockState blockState, net.minecraft.client.renderer.block.BlockAndTintGetter level, net.minecraft.core.BlockPos pos) {
        return this.get(blockState).particleMaterial(level, pos, blockState);
    }
}
