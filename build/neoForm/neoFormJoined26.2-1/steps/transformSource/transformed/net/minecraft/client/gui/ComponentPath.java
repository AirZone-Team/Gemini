package net.minecraft.client.gui;

import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public interface ComponentPath {
    static ComponentPath leaf(GuiEventListener component) {
        return new ComponentPath.Leaf(component);
    }

    static @Nullable ComponentPath path(ContainerEventHandler container, @Nullable ComponentPath childPath) {
        return childPath == null ? null : new ComponentPath.Path(container, childPath);
    }

    static ComponentPath path(GuiEventListener target, ContainerEventHandler... containerPath) {
        ComponentPath path = leaf(target);

        for (ContainerEventHandler container : containerPath) {
            path = path(container, path);
        }

        return path;
    }

    GuiEventListener component();

    void applyFocus(boolean focused);

    GuiEventListener leafComponent();

    record Leaf(GuiEventListener component) implements ComponentPath {
        @Override
        public void applyFocus(boolean focused) {
            this.component.setFocused(focused);
        }

        @Override
        public GuiEventListener leafComponent() {
            return this.component;
        }
    }

    record Path(ContainerEventHandler component, ComponentPath childPath) implements ComponentPath {
        @Override
        public void applyFocus(boolean focused) {
            if (!focused) {
                this.component.setFocused(null);
            } else {
                this.component.setFocused(this.childPath.component());
            }

            this.childPath.applyFocus(focused);
        }

        @Override
        public GuiEventListener leafComponent() {
            return this.childPath.leafComponent();
        }
    }
}
