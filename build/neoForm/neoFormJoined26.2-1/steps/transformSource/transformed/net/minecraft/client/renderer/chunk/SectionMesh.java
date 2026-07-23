package net.minecraft.client.renderer.chunk;

import com.mojang.blaze3d.IndexType;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public interface SectionMesh extends AutoCloseable {
    default boolean isDifferentPointOfView(TranslucencyPointOfView pointOfView) {
        return false;
    }

    default boolean hasRenderableLayers() {
        return false;
    }

    default boolean hasTranslucentGeometry() {
        return false;
    }

    default boolean isEmpty(ChunkSectionLayer layer) {
        return true;
    }

    default List<BlockEntity> getRenderableBlockEntities() {
        return Collections.emptyList();
    }

    boolean facesCanSeeEachother(Direction direction1, Direction direction2);

    default SectionMesh.@Nullable SectionDraw getSectionDraw(ChunkSectionLayer layer) {
        return null;
    }

    @Override
    default void close() {
    }

    record SectionDraw(int indexCount, IndexType indexType, boolean hasCustomIndexBuffer) {
    }
}
