package net.minecraft.client.gui.layouts;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FrameLayout extends AbstractLayout {
    private final List<FrameLayout.ChildContainer> children = new ArrayList<>();
    private int minWidth;
    private int minHeight;
    private final LayoutSettings defaultChildLayoutSettings = LayoutSettings.defaults().align(0.5F, 0.5F);

    public FrameLayout() {
        this(0, 0, 0, 0);
    }

    public FrameLayout(int minWidth, int minHeight) {
        this(0, 0, minWidth, minHeight);
    }

    public FrameLayout(int x, int y, int minWidth, int minHeight) {
        super(x, y, minWidth, minHeight);
        this.setMinDimensions(minWidth, minHeight);
    }

    public FrameLayout setMinDimensions(int minWidth, int minHeight) {
        return this.setMinWidth(minWidth).setMinHeight(minHeight);
    }

    public FrameLayout setMinHeight(int minHeight) {
        this.minHeight = minHeight;
        return this;
    }

    public FrameLayout setMinWidth(int minWidth) {
        this.minWidth = minWidth;
        return this;
    }

    public LayoutSettings newChildLayoutSettings() {
        return this.defaultChildLayoutSettings.copy();
    }

    public LayoutSettings defaultChildLayoutSetting() {
        return this.defaultChildLayoutSettings;
    }

    @Override
    public void arrangeElements() {
        super.arrangeElements();
        int resultWidth = this.minWidth;
        int resultHeight = this.minHeight;

        for (FrameLayout.ChildContainer child : this.children) {
            resultWidth = Math.max(resultWidth, child.getWidth());
            resultHeight = Math.max(resultHeight, child.getHeight());
        }

        for (FrameLayout.ChildContainer child : this.children) {
            child.setX(this.getX(), resultWidth);
            child.setY(this.getY(), resultHeight);
        }

        this.width = resultWidth;
        this.height = resultHeight;
    }

    public <T extends LayoutElement> T addChild(T child) {
        return this.addChild(child, this.newChildLayoutSettings());
    }

    public <T extends LayoutElement> T addChild(T child, LayoutSettings childLayoutSettings) {
        this.children.add(new FrameLayout.ChildContainer(child, childLayoutSettings));
        return child;
    }

    public <T extends LayoutElement> T addChild(T child, Consumer<LayoutSettings> layoutSettingsAdjustments) {
        return this.addChild(child, Util.make(this.newChildLayoutSettings(), layoutSettingsAdjustments));
    }

    @Override
    public void visitChildren(Consumer<LayoutElement> layoutElementVisitor) {
        this.children.forEach(wrapper -> layoutElementVisitor.accept(wrapper.child));
    }

    @Override
    public void removeChildren() {
        this.children.clear();
    }

    public static void centerInRectangle(LayoutElement widget, int x, int y, int width, int height) {
        alignInRectangle(widget, x, y, width, height, 0.5F, 0.5F);
    }

    public static void centerInRectangle(LayoutElement widget, ScreenRectangle rectangle) {
        centerInRectangle(widget, rectangle.position().x(), rectangle.position().y(), rectangle.width(), rectangle.height());
    }

    public static void alignInRectangle(LayoutElement widget, ScreenRectangle rectangle, float alignX, float alignY) {
        alignInRectangle(widget, rectangle.left(), rectangle.top(), rectangle.width(), rectangle.height(), alignX, alignY);
    }

    public static void alignInRectangle(LayoutElement widget, int x, int y, int width, int height, float alignX, float alignY) {
        alignInDimension(x, width, widget.getWidth(), widget::setX, alignX);
        alignInDimension(y, height, widget.getHeight(), widget::setY, alignY);
    }

    public static void alignInDimension(int pos, int length, int widgetLength, Consumer<Integer> setWidgetPos, float align) {
        int offset = (int)Mth.lerp(align, 0.0F, length - widgetLength);
        setWidgetPos.accept(pos + offset);
    }

    private static class ChildContainer extends AbstractLayout.AbstractChildWrapper {
        protected ChildContainer(LayoutElement child, LayoutSettings layoutSettings) {
            super(child, layoutSettings);
        }
    }
}
