package net.minecraft.client.gui.components;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class OptionsList extends ContainerObjectSelectionList<OptionsList.AbstractEntry> {
    private static final int BIG_BUTTON_WIDTH = 310;
    private static final int DEFAULT_ITEM_HEIGHT = 25;
    private final OptionsSubScreen screen;

    public OptionsList(Minecraft minecraft, int width, OptionsSubScreen screen) {
        super(minecraft, width, screen.layout.getContentHeight(), screen.layout.getHeaderHeight(), 25);
        this.centerListVertically = false;
        this.screen = screen;
    }

    public void addBig(OptionInstance<?> option) {
        this.addEntry(OptionsList.Entry.big(this.minecraft.options, option, this.screen));
    }

    public void addBig(AbstractWidget widget) {
        this.addEntry(OptionsList.Entry.big(widget, this.screen));
    }

    public void addSmall(OptionInstance<?>... options) {
        for (int i = 0; i < options.length; i += 2) {
            OptionInstance<?> secondOption = i < options.length - 1 ? options[i + 1] : null;
            this.addEntry(OptionsList.Entry.small(this.minecraft.options, options[i], secondOption, this.screen));
        }
    }

    public void addSmall(List<AbstractWidget> widgets) {
        for (int i = 0; i < widgets.size(); i += 2) {
            this.addSmall(widgets.get(i), i < widgets.size() - 1 ? widgets.get(i + 1) : null);
        }
    }

    public void addSmall(AbstractWidget firstOption, @Nullable AbstractWidget secondOption) {
        this.addEntry(OptionsList.Entry.small(firstOption, secondOption, this.screen));
    }

    public void addSmall(AbstractWidget firstOption, OptionInstance<?> firstOptionInstance, @Nullable AbstractWidget secondOption) {
        this.addEntry(OptionsList.Entry.small(firstOption, firstOptionInstance, secondOption, this.screen));
    }

    public void addHeader(Component text) {
        int lineHeight = 9;
        int paddingTop = this.children().isEmpty() ? 0 : lineHeight * 2;
        this.addEntry(new OptionsList.HeaderEntry(this.screen, text, paddingTop), paddingTop + lineHeight + 4);
    }

    @Override
    public int getRowWidth() {
        return 310;
    }

    public @Nullable AbstractWidget findOption(OptionInstance<?> option) {
        for (OptionsList.AbstractEntry child : this.children()) {
            if (child instanceof OptionsList.Entry entry) {
                AbstractWidget widgetForOption = entry.findOption(option);
                if (widgetForOption != null) {
                    return widgetForOption;
                }
            }
        }

        return null;
    }

    public void applyUnsavedChanges() {
        for (OptionsList.AbstractEntry child : this.children()) {
            if (child instanceof OptionsList.Entry entry) {
                for (OptionsList.OptionInstanceWidget optionInstanceWidget : entry.children) {
                    if (optionInstanceWidget.optionInstance() != null
                        && optionInstanceWidget.widget() instanceof OptionInstance.OptionInstanceSliderButton<?> optionSlider) {
                        optionSlider.applyUnsavedValue();
                    }
                }
            }
        }
    }

    public void resetOption(OptionInstance<?> option) {
        for (OptionsList.AbstractEntry child : this.children()) {
            if (child instanceof OptionsList.Entry entry) {
                for (OptionsList.OptionInstanceWidget optionInstanceWidget : entry.children) {
                    if (optionInstanceWidget.optionInstance() == option
                        && optionInstanceWidget.widget() instanceof ResettableOptionWidget resettableOptionWidget) {
                        resettableOptionWidget.resetValue();
                        return;
                    }
                }
            }
        }
    }

    protected abstract static class AbstractEntry extends ContainerObjectSelectionList.Entry<OptionsList.AbstractEntry> {
    }

    protected static class Entry extends OptionsList.AbstractEntry {
        private final List<OptionsList.OptionInstanceWidget> children;
        private final Screen screen;
        private static final int X_OFFSET = 160;

        private Entry(List<OptionsList.OptionInstanceWidget> widgets, Screen screen) {
            this.children = widgets;
            this.screen = screen;
        }

        public static OptionsList.Entry big(Options options, OptionInstance<?> optionInstance, Screen screen) {
            return new OptionsList.Entry(List.of(new OptionsList.OptionInstanceWidget(optionInstance.createButton(options, 0, 0, 310), optionInstance)), screen);
        }

        public static OptionsList.Entry big(AbstractWidget widget, Screen screen) {
            widget.setWidth(310);
            return new OptionsList.Entry(List.of(new OptionsList.OptionInstanceWidget(widget, null)), screen);
        }

        public static OptionsList.Entry small(AbstractWidget leftWidget, @Nullable AbstractWidget rightWidget, Screen screen) {
            return rightWidget == null
                ? new OptionsList.Entry(List.of(new OptionsList.OptionInstanceWidget(leftWidget)), screen)
                : new OptionsList.Entry(List.of(new OptionsList.OptionInstanceWidget(leftWidget), new OptionsList.OptionInstanceWidget(rightWidget)), screen);
        }

        public static OptionsList.Entry small(
            AbstractWidget leftWidget, OptionInstance<?> leftWidgetOptionInstance, @Nullable AbstractWidget rightWidget, Screen screen
        ) {
            return rightWidget == null
                ? new OptionsList.Entry(List.of(new OptionsList.OptionInstanceWidget(leftWidget, leftWidgetOptionInstance)), screen)
                : new OptionsList.Entry(
                    List.of(new OptionsList.OptionInstanceWidget(leftWidget, leftWidgetOptionInstance), new OptionsList.OptionInstanceWidget(rightWidget)),
                    screen
                );
        }

        public static OptionsList.Entry small(Options options, OptionInstance<?> optionA, @Nullable OptionInstance<?> optionB, OptionsSubScreen screen) {
            AbstractWidget buttonA = optionA.createButton(options);
            return optionB == null
                ? new OptionsList.Entry(List.of(new OptionsList.OptionInstanceWidget(buttonA, optionA)), screen)
                : new OptionsList.Entry(
                    List.of(
                        new OptionsList.OptionInstanceWidget(buttonA, optionA), new OptionsList.OptionInstanceWidget(optionB.createButton(options), optionB)
                    ),
                    screen
                );
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            int xOffset = 0;
            int x = this.screen.width / 2 - 155;

            for (OptionsList.OptionInstanceWidget optionInstanceWidget : this.children) {
                optionInstanceWidget.widget().setPosition(x + xOffset, this.getContentY());
                optionInstanceWidget.widget().extractRenderState(graphics, mouseX, mouseY, a);
                xOffset += 160;
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return Lists.transform(this.children, OptionsList.OptionInstanceWidget::widget);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return Lists.transform(this.children, OptionsList.OptionInstanceWidget::widget);
        }

        public @Nullable AbstractWidget findOption(OptionInstance<?> option) {
            for (OptionsList.OptionInstanceWidget child : this.children) {
                if (child.optionInstance == option) {
                    return child.widget();
                }
            }

            return null;
        }
    }

    protected static class HeaderEntry extends OptionsList.AbstractEntry {
        private final Screen screen;
        private final int paddingTop;
        private final StringWidget widget;

        protected HeaderEntry(Screen screen, Component text, int paddingTop) {
            this.screen = screen;
            this.paddingTop = paddingTop;
            this.widget = new StringWidget(text, screen.getFont());
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(this.widget);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            this.widget.setPosition(this.screen.width / 2 - 155, this.getContentY() + this.paddingTop);
            this.widget.extractRenderState(graphics, mouseX, mouseY, a);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(this.widget);
        }
    }

    public record OptionInstanceWidget(AbstractWidget widget, @Nullable OptionInstance<?> optionInstance) {
        public OptionInstanceWidget(AbstractWidget widget) {
            this(widget, null);
        }
    }
}
