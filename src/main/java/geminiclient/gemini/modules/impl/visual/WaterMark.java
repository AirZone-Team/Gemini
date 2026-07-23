package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render2DEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Fonts;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3RenderUtils;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class WaterMark extends Module {
    private static final String CLIENT_NAME = "Gemini";
    private static final String SUPPORTING_TEXT = "CLIENT HUD";

    private static final int HEIGHT = 34;
    private static final int PADDING = 6;
    private static final int BRAND_ICON_SIZE = 22;
    private static final int SPARKLE_SIZE = 10;
    private static final int ICON_TEXT_GAP = 6;

    public WaterMark() {
        super("WaterMark", ModuleEnum.Visual);
        hudX = 6;
        hudY = 6;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        GuiGraphicsExtractor g = event.guiGraphics();
        Layout layout = calculateLayout();

        Md3Theme.elevation1(g, hudX, hudY, layout.width(), HEIGHT, Md3Theme.R_LARGE);
        CustomRoundedRectRenderer.drawRoundedRect(
                g, hudX, hudY, layout.width(), HEIGHT,
                Md3Theme.R_LARGE, Md3Theme.SURFACE_CONTAINER_LOWEST);
        CustomRoundedRectRenderer.drawRoundedOutline(
                g, hudX, hudY, layout.width(), HEIGHT,
                Md3Theme.R_LARGE, Md3Theme.withAlpha(Md3Theme.OUTLINE_VARIANT, 0.55f), 1);

        CustomRoundedRectRenderer.drawCircle(
                g, layout.brandCenterX(), layout.brandCenterY(),
                BRAND_ICON_SIZE, Md3Theme.PRIMARY_CONTAINER);
        Md3RenderUtils.drawSparkle(
                g, layout.brandCenterX(), layout.brandCenterY(),
                SPARKLE_SIZE, Md3Theme.PRIMARY);

        CustomFontRenderer.GlyphFont titleFont = Md3Fonts.title();
        CustomFontRenderer.GlyphFont labelFont = Md3Fonts.label();
        Md3Fonts.drawText(g, titleFont, CLIENT_NAME,
                layout.brandTextX(), layout.titleY(), Md3Theme.ON_SURFACE);
        Md3Fonts.drawText(g, labelFont, SUPPORTING_TEXT,
                layout.brandTextX(), layout.supportingY(), Md3Theme.ON_SURFACE_VARIANT);

        Gemini.hudDragManager.registerDragRegion(this, hudX, hudY, layout.width(), HEIGHT);
    }

    @Override
    public void renderEditorOutline(GuiGraphicsExtractor g) {
        Layout layout = calculateLayout();
        CustomRoundedRectRenderer.drawRoundedOutline(
                g, hudX, hudY, layout.width(), HEIGHT,
                Md3Theme.R_LARGE, 0xAAFFD700, 2);
        Gemini.hudDragManager.registerDragRegion(this, hudX, hudY, layout.width(), HEIGHT);
    }

    private Layout calculateLayout() {
        CustomFontRenderer.GlyphFont titleFont = Md3Fonts.title();
        CustomFontRenderer.GlyphFont labelFont = Md3Fonts.label();

        int brandCenterX = hudX + PADDING + BRAND_ICON_SIZE / 2;
        int brandCenterY = hudY + HEIGHT / 2;
        int brandTextX = hudX + PADDING + BRAND_ICON_SIZE + ICON_TEXT_GAP;
        int brandTextWidth = (int) Math.ceil(Math.max(
                Md3Fonts.width(titleFont, CLIENT_NAME),
                Md3Fonts.width(labelFont, SUPPORTING_TEXT)));

        int width = brandTextX + brandTextWidth + PADDING - hudX;

        float titleY = hudY + 5f;
        float supportingY = hudY + HEIGHT - 5f - Md3Fonts.lineHeight(labelFont);

        return new Layout(width, brandCenterX, brandCenterY, brandTextX,
                titleY, supportingY);
    }

    private record Layout(
            int width,
            int brandCenterX,
            int brandCenterY,
            int brandTextX,
            float titleY,
            float supportingY
    ) {
    }
}
