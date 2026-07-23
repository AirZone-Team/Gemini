package net.minecraft.client.gui.screens.advancements;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import java.util.Map;
import java.util.Optional;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class AdvancementTab {
    private final Minecraft minecraft;
    private final AdvancementsScreen screen;
    private final AdvancementTabType type;
    private final int index;
    private final AdvancementNode rootNode;
    private final DisplayInfo display;
    private final ItemStack icon;
    private final Component title;
    private final AdvancementWidget root;
    private final Map<AdvancementHolder, AdvancementWidget> widgets = Maps.newLinkedHashMap();
    private double scrollX;
    private double scrollY;
    private int minX = Integer.MAX_VALUE;
    private int minY = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE;
    private int maxY = Integer.MIN_VALUE;
    private float fade;
    private boolean centered;
    private @Nullable AdvancementWidget hovered;
    private int page;

    public AdvancementTab(Minecraft minecraft, AdvancementsScreen screen, AdvancementTabType type, int index, AdvancementNode rootNode, DisplayInfo display) {
        this.minecraft = minecraft;
        this.screen = screen;
        this.type = type;
        this.index = index;
        this.rootNode = rootNode;
        this.display = display;
        this.icon = display.getIcon().create();
        this.title = display.getTitle();
        this.root = new AdvancementWidget(this, minecraft, rootNode, display);
        this.addWidget(this.root, rootNode.holder());
    }

    public AdvancementTab(Minecraft mc, AdvancementsScreen screen, AdvancementTabType type, int index, int page, AdvancementNode adv, DisplayInfo info) {
        this(mc, screen, type, index, adv, info);
        this.page = page;
    }

    public int getPage() {
        return page;
    }

    public AdvancementTabType getType() {
        return this.type;
    }

    public int getIndex() {
        return this.index;
    }

    public AdvancementNode getRootNode() {
        return this.rootNode;
    }

    public Component getTitle() {
        return this.title;
    }

    public DisplayInfo getDisplay() {
        return this.display;
    }

    public void tick(int relativeMouseX, int relativeMouseY) {
        boolean hovering = false;
        if (relativeMouseX > 0 && relativeMouseX < 234 && relativeMouseY > 0 && relativeMouseY < 113) {
            int intScrollX = Mth.floor(this.scrollX);
            int intScrollY = Mth.floor(this.scrollY);

            for (AdvancementWidget widget : this.widgets.values()) {
                if (widget.isMouseOver(intScrollX, intScrollY, relativeMouseX, relativeMouseY)) {
                    hovering = true;
                    this.hovered = widget;
                    break;
                }
            }
        }

        if (hovering) {
            this.fade = Mth.clamp(this.fade + 0.06F, 0.0F, 0.3F);
        } else {
            this.fade = Mth.clamp(this.fade - 0.12F, 0.0F, 1.0F);
            if (this.hovered != null) {
                this.hovered = null;
            }
        }
    }

    public void extractTab(GuiGraphicsExtractor graphics, int xo, int yo, int mouseX, int mouseY, boolean selected) {
        int tabX = xo + this.type.getX(this.index);
        int tabY = yo + this.type.getY(this.index);
        this.type.extractRenderState(graphics, tabX, tabY, selected, this.index);
        if (!selected && mouseX > tabX && mouseY > tabY && mouseX < tabX + this.type.getWidth() && mouseY < tabY + this.type.getHeight()) {
            graphics.requestCursor(CursorTypes.POINTING_HAND);
        }
    }

    public void extractIcon(GuiGraphicsExtractor graphics, int xo, int yo) {
        this.type.extractIcon(graphics, xo, yo, this.index, this.icon);
    }

    public void extractContents(GuiGraphicsExtractor graphics, int windowLeft, int windowTop) {
        if (!this.centered) {
            this.scrollX = 117 - (this.maxX + this.minX) / 2;
            this.scrollY = 56 - (this.maxY + this.minY) / 2;
            this.centered = true;
        }

        graphics.enableScissor(windowLeft, windowTop, windowLeft + 234, windowTop + 113);
        graphics.pose().pushMatrix();
        graphics.pose().translate(windowLeft, windowTop);
        Identifier background = this.display.getBackground().map(ClientAsset.ResourceTexture::texturePath).orElse(TextureManager.INTENTIONAL_MISSING_TEXTURE);
        int intScrollX = Mth.floor(this.scrollX);
        int intScrollY = Mth.floor(this.scrollY);
        int left = intScrollX % 16;
        int top = intScrollY % 16;

        for (int x = -1; x <= 15; x++) {
            for (int y = -1; y <= 8; y++) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, background, left + 16 * x, top + 16 * y, 0.0F, 0.0F, 16, 16, 16, 16);
            }
        }

        this.root.extractConnectivity(graphics, intScrollX, intScrollY, true);
        this.root.extractConnectivity(graphics, intScrollX, intScrollY, false);
        this.root.extractRenderState(graphics, intScrollX, intScrollY);
        graphics.pose().popMatrix();
        graphics.disableScissor();
    }

    public void extractTooltips(GuiGraphicsExtractor graphics, int xo, int yo) {
        graphics.fill(0, 0, 234, 113, Mth.floor(this.fade * 255.0F) << 24);
        if (this.hovered != null) {
            int intScrollX = Mth.floor(this.scrollX);
            int intScrollY = Mth.floor(this.scrollY);
            this.hovered.extractHover(graphics, intScrollX, intScrollY, this.fade, xo, yo);
        }
    }

    public boolean isMouseOver(int xo, int yo, double mx, double my) {
        return this.type.isMouseOver(xo, yo, this.index, mx, my);
    }

    public static @Nullable AdvancementTab create(Minecraft minecraft, AdvancementsScreen screen, int index, AdvancementNode root) {
        Optional<DisplayInfo> display = root.advancement().display();
        if (display.isEmpty()) {
            return null;
        }

        for (AdvancementTabType type : AdvancementTabType.values()) {
            if ((index % AdvancementTabType.MAX_TABS) < type.getMax()) {
                return new AdvancementTab(minecraft, screen, type, index % AdvancementTabType.MAX_TABS, index / AdvancementTabType.MAX_TABS, root, display.get());
            }

            index -= type.getMax();
        }

        return null;
    }

    public void scroll(double x, double y) {
        if (this.canScrollHorizontally()) {
            this.scrollX = Mth.clamp(this.scrollX + x, -(this.maxX - 234), 0.0);
        }

        if (this.canScrollVertically()) {
            this.scrollY = Mth.clamp(this.scrollY + y, -(this.maxY - 113), 0.0);
        }
    }

    public boolean canScrollHorizontally() {
        return this.maxX - this.minX > 234;
    }

    public boolean canScrollVertically() {
        return this.maxY - this.minY > 113;
    }

    public void addAdvancement(AdvancementNode node) {
        Optional<DisplayInfo> display = node.advancement().display();
        if (!display.isEmpty()) {
            AdvancementWidget widget = new AdvancementWidget(this, this.minecraft, node, display.get());
            this.addWidget(widget, node.holder());
        }
    }

    private void addWidget(AdvancementWidget widget, AdvancementHolder advancement) {
        this.widgets.put(advancement, widget);
        int x0 = widget.getX();
        int x1 = x0 + 28;
        int y0 = widget.getY();
        int y1 = y0 + 27;
        this.minX = Math.min(this.minX, x0);
        this.maxX = Math.max(this.maxX, x1);
        this.minY = Math.min(this.minY, y0);
        this.maxY = Math.max(this.maxY, y1);

        for (AdvancementWidget other : this.widgets.values()) {
            other.attachToParent();
        }
    }

    public @Nullable AdvancementWidget getWidget(AdvancementHolder advancement) {
        return this.widgets.get(advancement);
    }

    public AdvancementsScreen getScreen() {
        return this.screen;
    }
}
