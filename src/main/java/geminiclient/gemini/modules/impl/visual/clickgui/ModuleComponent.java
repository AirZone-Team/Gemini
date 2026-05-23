package geminiclient.gemini.modules.impl.visual.clickgui;

import geminiclient.gemini.modules.impl.visual.clickgui.component.*;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.values.ValueParent;
import geminiclient.gemini.values.impl.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class ModuleComponent {
    public final Module module;
    public int x, y, width, height;
    private boolean isExpanded = false;
    private final List<ValueComponent> allValueComponents = new ArrayList<>();

    // 统一的颜色主题 - 使用黑灰色调
    private static final int ACCENT_COLOR = new Color(220, 220, 220).getRGB();
    private static final int BASE_BG = new Color(18, 18, 18, 230).getRGB();
    private static final int HOVER_BG = new Color(30, 30, 30, 230).getRGB();
    private static final int TEXT_COLOR = Color.WHITE.getRGB();
    private static final int BORDER_COLOR = new Color(60, 65, 75).getRGB();

    protected boolean isModuleHeaderHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public ModuleComponent(Module module, int x, int y, int width, int height) {
        this.module = module;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        int componentHeight = 16;
        for (ValueParent value : module.getValues()) {
            ValueComponent component = null;

            if (value instanceof BoolValue) {
                component = new BoolValueComponent((BoolValue) value, 0, 0, width, componentHeight);
            } else if (value instanceof FloatValue) {
                component = new FloatValueComponent((FloatValue) value, 0, 0, width, componentHeight);
            } else if (value instanceof IntValue) {
                component = new IntValueComponent((IntValue) value, 0, 0, width, componentHeight);
            } else if (value instanceof ListValue) {
                component = new ListValueComponent((ListValue) value, 0, 0, width, componentHeight);
            } else if (value instanceof FloatRangeValue) {
                component = new FloatRangeValueComponent((FloatRangeValue) value, 0, 0, width, componentHeight);
            } else if (value instanceof IntRangeValue) {
                component = new IntRangeValueComponent((IntRangeValue) value, 0, 0, width, componentHeight);
            } else if (value instanceof CheckboxValue) {
                component = new CheckboxValueComponent((CheckboxValue) value,0,0,width,componentHeight);
            } else if (value instanceof ColorValue) {
                component = new ColorValueComponent((ColorValue) value, 0, 0, width, componentHeight);
            }

            if (component != null) {
                this.allValueComponents.add(component);
            }
        }
    }

    public int getTotalHeight() {
        int totalHeight = this.height;

        if (isExpanded) {
            for (ValueComponent component : getVisibleValueComponents()) {
                totalHeight += component.height;

                if (component instanceof ListValueComponent listComp) {
                    totalHeight += listComp.getExpandedListHeight();
                } else if (component instanceof CheckboxValueComponent checkboxComp) {
                    totalHeight += checkboxComp.getExpandedListHeight();
                }
            }
        }
        return totalHeight;
    }

    private List<ValueComponent> getVisibleValueComponents() {
        return allValueComponents.stream()
                .filter(ValueComponent::isVisible)
                .collect(Collectors.toList());
    }

    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        boolean isHovered = isModuleHeaderHovered(mouseX, mouseY);

        int bgColor = isHovered ? HOVER_BG : BASE_BG;
        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        if (module.enabled) {
            guiGraphics.fill(x, y, x + 2, y + height, ACCENT_COLOR);
        }

        guiGraphics.fill(x, y + height - 1, x + width, y + height, BORDER_COLOR);

        guiGraphics.text(mc.font, module.getName(), x + 4, y + 4, TEXT_COLOR, true);

        if (!allValueComponents.isEmpty()) {
            String symbol = isExpanded ? "▼" : "▶";
            int arrowColor = (isExpanded || isHovered) ? ACCENT_COLOR : TEXT_COLOR;
            guiGraphics.text(mc.font, symbol, x + width - 12, y + 4, arrowColor, true);
        }

        if (isExpanded) {
            int currentY = y + height;

            for (ValueComponent component : getVisibleValueComponents()) {
                component.x = x;
                component.y = currentY;
                component.width = width;
                component.render(guiGraphics, mouseX, mouseY, partialTicks);
                guiGraphics.fill(component.x, currentY + component.height - 1, component.x + component.width, currentY + component.height, BORDER_COLOR);

                int componentRenderHeight = component.height;
                if (component instanceof ListValueComponent listComp) {
                    componentRenderHeight += listComp.getExpandedListHeight();
                } else if (component instanceof CheckboxValueComponent checkboxComp) {
                    componentRenderHeight += checkboxComp.getExpandedListHeight();
                }
                currentY += componentRenderHeight;
            }
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isModuleHeaderHovered(mouseX, mouseY)) {
            if (button == 0) {
                module.toggle();
                return true;
            } else if (button == 1) {
                isExpanded = !isExpanded;
                return true;
            }
        }

        if (isExpanded) {
            int currentY = y + height;

            for (ValueComponent component : getVisibleValueComponents()) {
                component.x = x;
                component.y = currentY;

                if (component.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }

                int componentInteractionHeight = component.height;
                if (component instanceof ListValueComponent listComp) {
                    componentInteractionHeight += listComp.getExpandedListHeight();
                }
                if (component instanceof CheckboxValueComponent valueComponent) {
                    componentInteractionHeight += valueComponent.getExpandedListHeight();
                }
                currentY += componentInteractionHeight;
            }
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isExpanded) {
            int currentY = y + height;
            for (ValueComponent component : getVisibleValueComponents()) {
                component.x = x;
                component.y = currentY;

                if (component.mouseReleased(mouseX, mouseY, button)) {
                    return true;
                }

                int componentInteractionHeight = component.height;
                if (component instanceof ListValueComponent listComp) {
                    componentInteractionHeight += listComp.getExpandedListHeight();
                }
                if (component instanceof CheckboxValueComponent valueComponent) {
                    componentInteractionHeight += valueComponent.getExpandedListHeight();
                }
                currentY += componentInteractionHeight;
            }
        }
        return false;
    }
}