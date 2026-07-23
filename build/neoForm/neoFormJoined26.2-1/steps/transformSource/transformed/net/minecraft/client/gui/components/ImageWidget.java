package net.minecraft.client.gui.components;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public abstract class ImageWidget extends AbstractWidget {
    private ImageWidget(int x, int y, int width, int height) {
        super(x, y, width, height, CommonComponents.EMPTY);
    }

    public static ImageWidget texture(int width, int height, Identifier texture, int textureWidth, int textureHeight) {
        return new ImageWidget.Texture(0, 0, width, height, texture, textureWidth, textureHeight);
    }

    public static ImageWidget sprite(int width, int height, Identifier sprite) {
        return new ImageWidget.Sprite(0, 0, width, height, sprite);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
    }

    @Override
    public boolean isActive() {
        return false;
    }

    public abstract void updateResource(Identifier identifier);

    @Override
    public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent navigationEvent) {
        return null;
    }

    private static class Sprite extends ImageWidget {
        private Identifier sprite;

        public Sprite(int x, int y, int width, int height, Identifier sprite) {
            super(x, y, width, height);
            this.sprite = sprite;
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.sprite, this.getX(), this.getY(), this.getWidth(), this.getHeight());
        }

        @Override
        public void updateResource(Identifier identifier) {
            this.sprite = identifier;
        }
    }

    private static class Texture extends ImageWidget {
        private Identifier texture;
        private final int textureWidth;
        private final int textureHeight;

        public Texture(int x, int y, int width, int height, Identifier texture, int textureWidth, int textureHeight) {
            super(x, y, width, height);
            this.texture = texture;
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                this.texture,
                this.getX(),
                this.getY(),
                0.0F,
                0.0F,
                this.getWidth(),
                this.getHeight(),
                this.textureWidth,
                this.textureHeight
            );
        }

        @Override
        public void updateResource(Identifier identifier) {
            this.texture = identifier;
        }
    }
}
