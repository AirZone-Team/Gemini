package net.minecraft.client.renderer.state.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public record BlockOutlineRenderState(
    BlockPos pos,
    boolean isTranslucent,
    boolean highContrast,
    VoxelShape shape,
    @Nullable VoxelShape collisionShape,
    @Nullable VoxelShape occlusionShape,
    @Nullable VoxelShape interactionShape,
    java.util.List<net.neoforged.neoforge.client.CustomBlockOutlineRenderer> customRenderers
) {
    @Deprecated
    public BlockOutlineRenderState(
            BlockPos pos,
            boolean isTranslucent,
            boolean highContrast,
            VoxelShape shape,
            @Nullable VoxelShape collisionShape,
            @Nullable VoxelShape occlusionShape,
            @Nullable VoxelShape interactionShape
    ) {
        this(pos, isTranslucent, highContrast, shape, collisionShape, occlusionShape, interactionShape, java.util.List.of());
    }

    @Deprecated
    public BlockOutlineRenderState(BlockPos pos, boolean isTranslucent, boolean highContrast, VoxelShape shape) {
        this(pos, isTranslucent, highContrast, shape, java.util.List.of());
    }

    public BlockOutlineRenderState(BlockPos pos, boolean isTranslucent, boolean highContrast, VoxelShape shape, java.util.List<net.neoforged.neoforge.client.CustomBlockOutlineRenderer> customRenderers) {
        this(pos, isTranslucent, highContrast, shape, null, null, null, customRenderers);
    }
}
