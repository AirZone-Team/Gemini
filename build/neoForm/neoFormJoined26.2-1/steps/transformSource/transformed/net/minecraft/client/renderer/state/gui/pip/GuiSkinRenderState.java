package net.minecraft.client.renderer.state.gui.pip;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.model.Model;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public record GuiSkinRenderState(
    Model.Simple playerModel,
    Identifier texture,
    float rotationX,
    float rotationY,
    float pivotY,
    int x0,
    int y0,
    int x1,
    int y1,
    float scale,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {
    public GuiSkinRenderState(
        Model.Simple playerModel,
        Identifier texture,
        float rotationX,
        float rotationY,
        float pivotY,
        int x0,
        int y0,
        int x1,
        int y1,
        float scale,
        @Nullable ScreenRectangle scissorArea
    ) {
        this(
            playerModel,
            texture,
            rotationX,
            rotationY,
            pivotY,
            x0,
            y0,
            x1,
            y1,
            scale,
            scissorArea,
            PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea)
        );
    }
}
