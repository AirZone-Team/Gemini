package net.minecraft.client.gui.components;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.CommonComponents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class ScrollableLayout implements Layout {
    private static final int DEFAULT_SCROLLBAR_SPACING = 4;
    private final Layout content;
    private final ScrollableLayout.Container container;
    private final ScrollableLayout.ReserveStrategy reserveStrategy;
    private int scrollbarSpacing = 4;
    private int minWidth;
    private int minHeight;
    private int maxHeight;

    public ScrollableLayout(Minecraft minecraft, Layout content, int maxHeight) {
        this(minecraft, content, maxHeight, ScrollableLayout.ReserveStrategy.BOTH);
    }

    public ScrollableLayout(Minecraft minecraft, Layout content, int maxHeight, ScrollableLayout.ReserveStrategy reserveStrategy) {
        this.content = content;
        this.maxHeight = maxHeight;
        this.reserveStrategy = reserveStrategy;
        this.container = new ScrollableLayout.Container(minecraft, 0, maxHeight, AbstractScrollArea.defaultSettings(10));
    }

    public void setScrollbarSpacing(int scrollbarSpacing) {
        this.scrollbarSpacing = scrollbarSpacing;
    }

    public void setMinWidth(int minWidth) {
        this.minWidth = minWidth;
        this.container.setWidth(Math.max(this.content.getWidth(), minWidth));
    }

    public void setMinHeight(int minHeight) {
        this.minHeight = minHeight;
        this.container.setHeight(Math.max(this.content.getHeight(), minHeight));
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
        this.container.setHeight(Math.min(this.content.getHeight(), maxHeight));
        this.container.refreshScrollAmount();
    }

    @Override
    public void arrangeElements() {
        this.content.arrangeElements();
        int contentWidth = this.content.getWidth();

        int scrollbarReserve = switch (this.reserveStrategy) {
            case RIGHT -> this.container.scrollbarReserve();
            case BOTH -> 2 * this.container.scrollbarReserve();
        };
        this.container.setWidth(Math.max(contentWidth, this.minWidth) + scrollbarReserve);
        this.container.setHeight(Math.clamp(this.container.getHeight(), this.minHeight, this.maxHeight));
        this.container.refreshChildren();
        this.container.refreshScrollAmount();
    }

    @Override
    public void visitChildren(Consumer<LayoutElement> layoutElementVisitor) {
        layoutElementVisitor.accept(this.container);
    }

    @Override
    public void removeChildren() {
        this.container.children().clear();
        this.content.removeChildren();
    }

    @Override
    public void setX(int x) {
        this.container.setX(x);
    }

    @Override
    public void setY(int y) {
        this.container.setY(y);
    }

    @Override
    public int getX() {
        return this.container.getX();
    }

    @Override
    public int getY() {
        return this.container.getY();
    }

    @Override
    public int getWidth() {
        return this.container.getWidth();
    }

    @Override
    public int getHeight() {
        return this.container.getHeight();
    }

    private class Container extends AbstractContainerWidget {
        private final Minecraft minecraft;
        private final List<AbstractWidget> children = new ArrayList<>();

        public Container(Minecraft minecraft, int width, int height, AbstractScrollArea.ScrollbarSettings scrollbarSettings) {
            super(0, 0, width, height, CommonComponents.EMPTY, scrollbarSettings);
            this.minecraft = minecraft;
            this.refreshChildren();
        }

        public void refreshChildren() {
            this.children.clear();
            ScrollableLayout.this.content.visitWidgets(this.children::add);
        }

        @Override
        protected int contentHeight() {
            return ScrollableLayout.this.content.getHeight();
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            graphics.enableScissor(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height);

            for (AbstractWidget child : this.children) {
                child.extractRenderState(graphics, mouseX, mouseY, a);
            }

            graphics.disableScissor();
            this.extractScrollbar(graphics, mouseX, mouseY);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
        }

        @Override
        public ScreenRectangle getBorderForArrowNavigation(ScreenDirection opposite) {
            GuiEventListener focused = this.getFocused();
            return focused != null
                ? focused.getBorderForArrowNavigation(opposite)
                : new ScreenRectangle(this.getX(), this.getY(), this.width, this.contentHeight()).getBorder(opposite);
        }

        @Override
        public void setFocused(@Nullable GuiEventListener focused) {
            super.setFocused(focused);
            if (focused != null && this.minecraft.getLastInputType().isKeyboard()) {
                ScreenRectangle area = this.getRectangle();
                ScreenRectangle focusedRect = focused.getRectangle();
                int topDelta = focusedRect.top() - area.top();
                int bottomDelta = focusedRect.bottom() - area.bottom();
                double scrollRate = this.scrollRate();
                if (topDelta < 0) {
                    this.setScrollAmount(this.scrollAmount() + topDelta - scrollRate);
                } else if (bottomDelta > 0) {
                    this.setScrollAmount(this.scrollAmount() + bottomDelta + scrollRate);
                }
            }
        }

        @Override
        public void setX(int x) {
            super.setX(x);
            ScrollableLayout.this.content
                .setX(x + (ScrollableLayout.this.reserveStrategy == ScrollableLayout.ReserveStrategy.BOTH ? this.scrollbarReserve() : 0));
        }

        @Override
        public void setY(int y) {
            super.setY(y);
            ScrollableLayout.this.content.setY(y - (int)this.scrollAmount());
        }

        private int scrollbarReserve() {
            return ScrollableLayout.this.scrollbarSpacing + this.scrollbarWidth();
        }

        @Override
        public void setScrollAmount(double scrollAmount) {
            super.setScrollAmount(scrollAmount);
            ScrollableLayout.this.content.setY(this.getRectangle().top() - (int)this.scrollAmount());
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.children;
        }

        @Override
        public Collection<? extends NarratableEntry> getNarratables() {
            return this.children;
        }
    }

    public enum ReserveStrategy {
        RIGHT,
        BOTH;
    }
}
