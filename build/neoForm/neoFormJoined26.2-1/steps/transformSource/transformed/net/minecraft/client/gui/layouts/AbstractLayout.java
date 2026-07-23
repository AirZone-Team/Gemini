package net.minecraft.client.gui.layouts;

import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class AbstractLayout implements Layout {
    private int x;
    private int y;
    protected int width;
    protected int height;

    public AbstractLayout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @Override
    public void setX(int x) {
        this.visitChildren(child -> {
            int newChildX = child.getX() + (x - this.getX());
            child.setX(newChildX);
        });
        this.x = x;
    }

    @Override
    public void setY(int y) {
        this.visitChildren(child -> {
            int newChildY = child.getY() + (y - this.getY());
            child.setY(newChildY);
        });
        this.y = y;
    }

    @Override
    public int getX() {
        return this.x;
    }

    @Override
    public int getY() {
        return this.y;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    protected abstract static class AbstractChildWrapper {
        public final LayoutElement child;
        public final LayoutSettings.LayoutSettingsImpl layoutSettings;

        protected AbstractChildWrapper(LayoutElement child, LayoutSettings layoutSettings) {
            this.child = child;
            this.layoutSettings = layoutSettings.getExposed();
        }

        public int getHeight() {
            return this.child.getHeight() + this.layoutSettings.paddingTop + this.layoutSettings.paddingBottom;
        }

        public int getWidth() {
            return this.child.getWidth() + this.layoutSettings.paddingLeft + this.layoutSettings.paddingRight;
        }

        public void setX(int x, int availableSpace) {
            float leastOffset = this.layoutSettings.paddingLeft;
            float mostOffset = availableSpace - this.child.getWidth() - this.layoutSettings.paddingRight;
            int offset = (int)Mth.lerp(this.layoutSettings.xAlignment, leastOffset, mostOffset);
            this.child.setX(offset + x);
        }

        public void setY(int y, int availableSpace) {
            float leastOffset = this.layoutSettings.paddingTop;
            float mostOffset = availableSpace - this.child.getHeight() - this.layoutSettings.paddingBottom;
            int offset = Math.round(Mth.lerp(this.layoutSettings.yAlignment, leastOffset, mostOffset));
            this.child.setY(offset + y);
        }
    }
}
