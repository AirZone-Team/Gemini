package geminiclient.gemini.base;

import com.mojang.blaze3d.platform.NativeImage;
import geminiclient.gemini.customRenderer.cpu.CustomRectRenderer;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomBlurRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer.GlyphFont;
import geminiclient.gemini.customRenderer.glsl.SdfUIRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Background selector popup screen.
 * Displays a scrollable list of available wallpapers with thumbnails.
 */
public class BackgroundSelectorScreen extends Screen {

    // ========================
    // Constants
    // ========================

    private static final int PANEL_WIDTH = 480;
    private static final int PANEL_HEIGHT = 400;
    private static final int TITLE_HEIGHT = 50;
    private static final int ITEM_HEIGHT = 80;
    private static final int ITEM_PADDING = 12;
    private static final int THUMBNAIL_SIZE = 64; // Reduced to 64x64 (1/4 of 128x128)
    private static final int SCROLL_SPEED = 20;
    private static final int SCROLLBAR_WIDTH = 6;

    // Fonts - All using googlesans-regular.ttf (lowercase for Minecraft compatibility)
    private static final Identifier FONT_BOLD =
            Identifier.fromNamespaceAndPath("gemini", "font/googlesans-regular.ttf");
    private static final Identifier FONT_MEDIUM =
            Identifier.fromNamespaceAndPath("gemini", "font/googlesans-regular.ttf");
    private static final Identifier FONT_LIGHT =
            Identifier.fromNamespaceAndPath("gemini", "font/googlesans-regular.ttf");

    // Colors (matching MainMenuScreen style)
    private static final int PANEL_BG = 0xE8161D28;
    private static final int PANEL_OUTLINE = 0x4089DDFF;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int ITEM_BG = 0x40000000;
    private static final int ITEM_HOVER_BG = 0x6089DDFF;
    private static final int ITEM_SELECTED_BG = 0x8089DDFF;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int SUBTITLE_COLOR = 0xFF89DDFF;
    private static final int ACCENT = 0xFF89DDFF;

    private static final float TITLE_FONT_SIZE = 24f;
    private static final float ITEM_FONT_SIZE = 14f;
    private static final float SUBTITLE_FONT_SIZE = 11f;

    // ========================
    // State
    // ========================

    private final Screen parent;
    private final BackgroundConfig backgroundConfig;
    private final List<WallpaperEntry> wallpapers = new ArrayList<>();
    private final Map<Path, Identifier> thumbnailCache = new HashMap<>();

    private GlyphFont titleFont;
    private GlyphFont itemFont;
    private GlyphFont subtitleFont;

    private float scrollOffset = 0f;
    private float targetScrollOffset = 0f;
    private int hoveredIndex = -1;
    private int selectedIndex = -1;
    private int hoveredDeleteIndex = -1; // Track which delete button is hovered

    private boolean isDraggingScrollbar = false; // Track if scrollbar is being dragged
    private int dragStartY = 0; // Mouse Y position when drag started
    private float dragStartScroll = 0f; // Scroll offset when drag started

    // Parallax effect
    private float parallaxOffsetX = 0f;
    private float parallaxOffsetY = 0f;
    private float lastMouseX = 0f;
    private float lastMouseY = 0f;
    private float mouseStillTime = 0f; // Time since mouse stopped moving
    private static final float PARALLAX_STRENGTH = 0.2f; // Reduced from 0.5f - slower movement (2/5 of original = 0.5 * 2/5 = 0.2)
    private static final float PARALLAX_MAX = 100f; // Maximum parallax offset in pixels
    private static final float PARALLAX_RETURN_DELAY = 5f; // Wait 5 seconds before returning to center
    private static final float MOUSE_STILL_THRESHOLD = 0.5f; // Mouse movement below 0.5px is considered still

    private int panelX, panelY;
    private int listY, listHeight;

    // ========================
    // Constructor
    // ========================

    public BackgroundSelectorScreen(Screen parent, BackgroundConfig backgroundConfig) {
        super(Component.literal("Background Selector"));
        this.parent = parent;
        this.backgroundConfig = backgroundConfig;
    }

    @Override
    protected void init() {
        super.init();

        // Calculate panel position (centered)
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - PANEL_HEIGHT) / 2;
        listY = panelY + TITLE_HEIGHT;
        listHeight = PANEL_HEIGHT - TITLE_HEIGHT;

        // Initialize mouse position for parallax
        lastMouseX = this.width / 2f;
        lastMouseY = this.height / 2f;

        // Load fonts
        loadFonts();

        // Scan wallpapers
        scanWallpapers();

        // Pre-generate all thumbnails asynchronously to avoid lag when opening
        Thread thumbnailPreloader = new Thread(() -> {
            for (WallpaperEntry entry : wallpapers) {
                if (!entry.isAnimated() && !thumbnailCache.containsKey(entry.filePath())) {
                    try {
                        Identifier thumbnail = generateThumbnailSync(entry);
                        if (thumbnail != null) {
                            thumbnailCache.put(entry.filePath(), thumbnail);
                        }
                    } catch (Exception e) {
                        // Ignore errors during preload
                    }
                }
            }
        });
        thumbnailPreloader.setDaemon(true);
        thumbnailPreloader.setName("ThumbnailPreloader");
        thumbnailPreloader.start();

        // Find currently selected wallpaper
        Path currentBg = backgroundConfig.getCustomBackgroundFile();
        for (int i = 0; i < wallpapers.size(); i++) {
            if (wallpapers.get(i).filePath().equals(currentBg)) {
                selectedIndex = i;
                break;
            }
        }
    }

    private void loadFonts() {
        try {
            titleFont = CustomFontRenderer.loadFont(FONT_BOLD, TITLE_FONT_SIZE);
            itemFont = CustomFontRenderer.loadFont(FONT_MEDIUM, ITEM_FONT_SIZE);
            subtitleFont = CustomFontRenderer.loadFont(FONT_LIGHT, SUBTITLE_FONT_SIZE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ========================
    // Wallpaper Scanning
    // ========================

    private void scanWallpapers() {
        wallpapers.clear();
        Path dir = backgroundConfig.getConfigDirectory();

        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                    .filter(this::isSupportedFile)
                    .sorted()
                    .forEach(path -> {
                        try {
                            long size = Files.size(path);
                            WallpaperEntry.WallpaperType type = WallpaperEntry.getTypeFromPath(path);
                            wallpapers.add(new WallpaperEntry(path, path.getFileName().toString(), type, size));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isSupportedFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".mp4") || name.endsWith(".webm") || name.endsWith(".gif");
    }

    // ========================
    // Thumbnail Generation
    // ========================

    private Identifier getThumbnail(WallpaperEntry entry) {
        // Simply return from cache
        return thumbnailCache.get(entry.filePath());
    }

    private Identifier generateThumbnailSync(WallpaperEntry entry) {
        try {
            // Load original image
            BufferedImage originalImg = ImageIO.read(entry.filePath().toFile());
            if (originalImg == null) return null;

            int targetSize = THUMBNAIL_SIZE;

            // Create BufferedImage with high quality rendering
            BufferedImage scaledBuf = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g2d = scaledBuf.createGraphics();

            g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                    java.awt.RenderingHints.VALUE_RENDER_QUALITY);

            // Calculate aspect-fill scaling (crop to square)
            int srcWidth = originalImg.getWidth();
            int srcHeight = originalImg.getHeight();
            int cropSize = Math.min(srcWidth, srcHeight);
            int srcX = (srcWidth - cropSize) / 2;
            int srcY = (srcHeight - cropSize) / 2;

            // Draw cropped and scaled image
            g2d.drawImage(originalImg,
                0, 0, targetSize, targetSize,
                srcX, srcY, srcX + cropSize, srcY + cropSize,
                null);
            g2d.dispose();

            // Apply rounded corners
            int cornerRadius = 12;
            applyRoundedCorners(scaledBuf, cornerRadius);

            // Convert to NativeImage
            NativeImage nativeImg = new NativeImage(targetSize, targetSize, true);
            for (int y = 0; y < targetSize; y++) {
                for (int x = 0; x < targetSize; x++) {
                    int argb = scaledBuf.getRGB(x, y);
                    nativeImg.setPixel(x, y, argb);
                }
            }

            // Register texture on main thread
            final NativeImage finalImg = nativeImg;
            final Path finalPath = entry.filePath();
            this.minecraft.execute(() -> {
                try {
                    DynamicTexture texture = new DynamicTexture(() -> "thumbnail_" +
                            finalPath.getFileName().toString().hashCode(), finalImg);
                    Identifier id = Identifier.fromNamespaceAndPath("gemini", "thumbnail_" +
                            finalPath.getFileName().toString().hashCode());
                    this.minecraft.getTextureManager().register(id, texture);
                    thumbnailCache.put(finalPath, id);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            return null; // Will be added to cache by main thread
        } catch (Exception e) {
            return null;
        }
    }


    private void applyRoundedCorners(BufferedImage image, int radius) {
        int width = image.getWidth();
        int height = image.getHeight();

        // Process each pixel and set alpha to 0 for pixels outside the rounded rectangle
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Check if pixel is in a corner region
                boolean inCorner = false;
                int cornerCenterX = 0;
                int cornerCenterY = 0;

                // Top-left corner
                if (x < radius && y < radius) {
                    inCorner = true;
                    cornerCenterX = radius;
                    cornerCenterY = radius;
                }
                // Top-right corner
                else if (x >= width - radius && y < radius) {
                    inCorner = true;
                    cornerCenterX = width - radius - 1;
                    cornerCenterY = radius;
                }
                // Bottom-left corner
                else if (x < radius && y >= height - radius) {
                    inCorner = true;
                    cornerCenterX = radius;
                    cornerCenterY = height - radius - 1;
                }
                // Bottom-right corner
                else if (x >= width - radius && y >= height - radius) {
                    inCorner = true;
                    cornerCenterX = width - radius - 1;
                    cornerCenterY = height - radius - 1;
                }

                // If in corner, check if outside the circle
                if (inCorner) {
                    double distance = Math.sqrt(
                        Math.pow(x - cornerCenterX, 2) + Math.pow(y - cornerCenterY, 2)
                    );

                    // If outside the circle radius, make transparent
                    if (distance > radius) {
                        image.setRGB(x, y, 0x00000000); // Fully transparent
                    }
                }
            }
        }
    }

    // ========================
    // Rendering
    // ========================

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        // Calculate parallax offset based on mouse movement (opposite direction)
        float mouseDeltaX = mouseX - lastMouseX;
        float mouseDeltaY = mouseY - lastMouseY;

        // Check if mouse is moving (use higher threshold to avoid micro-movements)
        boolean mouseMoving = Math.abs(mouseDeltaX) > MOUSE_STILL_THRESHOLD || Math.abs(mouseDeltaY) > MOUSE_STILL_THRESHOLD;

        if (mouseMoving) {
            // Reset still time when mouse moves
            mouseStillTime = 0f;

            // Update last mouse position
            lastMouseX = mouseX;
            lastMouseY = mouseY;

            // Target parallax offset (move opposite to mouse movement)
            float targetParallaxX = parallaxOffsetX - mouseDeltaX * PARALLAX_STRENGTH * delta;
            float targetParallaxY = parallaxOffsetY - mouseDeltaY * PARALLAX_STRENGTH * delta;

            // Clamp to maximum offset
            targetParallaxX = Math.max(-PARALLAX_MAX, Math.min(PARALLAX_MAX, targetParallaxX));
            targetParallaxY = Math.max(-PARALLAX_MAX, Math.min(PARALLAX_MAX, targetParallaxY));

            // Smooth interpolation
            parallaxOffsetX += (targetParallaxX - parallaxOffsetX) * delta * 5f;
            parallaxOffsetY += (targetParallaxY - parallaxOffsetY) * delta * 5f;
        } else {
            // Mouse is still, increment still time
            mouseStillTime += delta;

            // Disabled: Do not return to center
            // User wants panel to stay at current offset position
        }

        // Calculate base panel position
        int basePanelX = (this.width - PANEL_WIDTH) / 2;
        int basePanelY = (this.height - PANEL_HEIGHT) / 2;

        // Apply parallax offset
        panelX = (int)(basePanelX + parallaxOffsetX);
        panelY = (int)(basePanelY + parallaxOffsetY);
        listY = panelY + TITLE_HEIGHT;

        // Use GLFW directly to check mouse button state
        long windowHandle = this.minecraft.getWindow().handle();
        boolean leftPressed = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean mouseOverScrollbar = isMouseOverScrollbar(mouseX, mouseY);

        // Start dragging if mouse is pressed over scrollbar
        if (!isDraggingScrollbar && leftPressed && mouseOverScrollbar) {
            isDraggingScrollbar = true;
            dragStartY = mouseY;
            dragStartScroll = scrollOffset;
        }

        // Stop dragging if mouse button is released
        if (isDraggingScrollbar && !leftPressed) {
            isDraggingScrollbar = false;
        }

        // Handle scrollbar dragging
        if (isDraggingScrollbar) {
            int totalItems = wallpapers.size() + 1;
            float totalContentHeight = totalItems * (ITEM_HEIGHT + ITEM_PADDING);
            float maxScroll = Math.min(0, listHeight - totalContentHeight);

            if (maxScroll < 0) {
                int scrollbarHeight = listHeight - 8;
                float visibleRatio = listHeight / totalContentHeight;
                int thumbHeight = Math.max(20, (int)(scrollbarHeight * visibleRatio));

                int scrollDragDeltaY = mouseY - dragStartY;
                float scrollableTrackHeight = scrollbarHeight - thumbHeight;
                float scrollRange = -maxScroll;
                float scrollDelta = (scrollDragDeltaY / scrollableTrackHeight) * scrollRange;

                scrollOffset = Math.max(maxScroll, Math.min(0, dragStartScroll - scrollDelta));
                targetScrollOffset = scrollOffset;
            }
        } else {
            // Smooth scroll with damping
            float scrollDiff = targetScrollOffset - scrollOffset;
            if (Math.abs(scrollDiff) < 0.5f) {
                scrollOffset = targetScrollOffset;
            } else {
                scrollOffset += scrollDiff * delta * 10f;
            }
        }

        // Very light dark overlay - more transparent
        gui.fill(0, 0, this.width, this.height, 0x30000000);

        // Panel with blur effect
        drawPanel(gui);
        drawTitle(gui);
        drawList(gui, mouseX, mouseY);

        super.extractRenderState(gui, mouseX, mouseY, delta);
    }

    private void drawPanel(GuiGraphicsExtractor gui) {
        // Background blur behind panel - lighter for better transparency
        CustomBlurRenderer.render(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT,
                12, 0x10161D28, 18f);

        // Shadow
        SdfUIRenderer.drawShadow(gui, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 12, 0, 4, 20, 0xA0000000);

        // Semi-transparent dark background - much lighter (30% opacity)
        CustomRoundedRectRenderer.drawRoundedRect(gui, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 12, 0x4D161D28);

        // Brighter outline
        CustomRoundedRectRenderer.drawRoundedOutline(gui, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 12, 0x8089DDFF, 2);
    }

    private void drawTitle(GuiGraphicsExtractor gui) {
        if (titleFont == null || itemFont == null) return;

        // Title on the left
        String title = "BackGround";
        float titleX = panelX + 20;
        float titleY = panelY + (TITLE_HEIGHT - TITLE_FONT_SIZE) / 2f;
        CustomFontRenderer.drawString(gui, titleFont, title, titleX, titleY, TITLE_COLOR);

        // Divider line
        int lineY = panelY + TITLE_HEIGHT - 1;
        CustomRectRenderer.drawRect(gui, panelX, lineY, PANEL_WIDTH, 1, 0x4089DDFF);
    }

    private void drawList(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        if (itemFont == null) return;

        // Scissor to panel area (leave space for scrollbar)
        int listWidth = PANEL_WIDTH - SCROLLBAR_WIDTH - 8;
        gui.enableScissor(panelX, listY, panelX + listWidth, listY + listHeight);

        hoveredIndex = -1;
        hoveredDeleteIndex = -1;
        int yOffset = (int) scrollOffset;

        // Draw wallpaper items
        for (int i = 0; i < wallpapers.size(); i++) {
            WallpaperEntry entry = wallpapers.get(i);
            int itemY = listY + yOffset + i * (ITEM_HEIGHT + ITEM_PADDING);

            // Skip if not visible
            if (itemY + ITEM_HEIGHT < listY || itemY > listY + listHeight) {
                continue;
            }

            boolean hovered = isMouseOverItem(mouseX, mouseY, itemY);
            if (hovered) hoveredIndex = i;
            boolean selected = i == selectedIndex;

            // Check if delete button is hovered
            boolean deleteHovered = isMouseOverDeleteButton(mouseX, mouseY, itemY);
            if (deleteHovered) hoveredDeleteIndex = i;

            drawListItem(gui, entry, panelX + ITEM_PADDING, itemY,
                    listWidth - ITEM_PADDING * 2, ITEM_HEIGHT, hovered, selected, deleteHovered);
        }

        // Draw "+" button at the end
        int addButtonIndex = wallpapers.size();
        int addButtonY = listY + yOffset + addButtonIndex * (ITEM_HEIGHT + ITEM_PADDING);

        // Check if visible
        if (addButtonY <= listY + listHeight && addButtonY + ITEM_HEIGHT >= listY) {
            boolean addButtonHovered = isMouseOverItem(mouseX, mouseY, addButtonY);
            if (addButtonHovered) hoveredIndex = -2; // Special index for add button
            drawAddButton(gui, panelX + ITEM_PADDING, addButtonY,
                    listWidth - ITEM_PADDING * 2, ITEM_HEIGHT, addButtonHovered);
        }

        gui.disableScissor();

        // Draw scrollbar
        drawScrollbar(gui);
    }

    private void drawListItem(GuiGraphicsExtractor gui, WallpaperEntry entry,
                              int x, int y, int w, int h, boolean hovered, boolean selected, boolean deleteHovered) {
        // Background
        int bgColor = selected ? ITEM_SELECTED_BG : (hovered ? ITEM_HOVER_BG : ITEM_BG);
        CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, w, h, 8, bgColor);

        if (hovered || selected) {
            CustomRoundedRectRenderer.drawRoundedOutline(gui, x, y, w, h, 8, ACCENT, 1);
        }

        // Thumbnail (left side)
        int thumbX = x + 8;
        int thumbY = y + (h - THUMBNAIL_SIZE) / 2;

        Identifier thumbnail = getThumbnail(entry);
        if (thumbnail != null) {
            // Draw thumbnail image directly - it already has rounded corners baked in
            gui.blit(RenderPipelines.GUI_TEXTURED, thumbnail,
                    thumbX, thumbY, 0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE,
                    THUMBNAIL_SIZE, THUMBNAIL_SIZE, 0xFFFFFFFF);
        } else {
            // Placeholder for thumbnails (animated or failed to load)
            CustomRoundedRectRenderer.drawRoundedRect(gui, thumbX, thumbY,
                    THUMBNAIL_SIZE, THUMBNAIL_SIZE, 12, 0x40FFFFFF);

            // Show play icon for animated
            if (entry.isAnimated() && itemFont != null) {
                String icon = "▶";
                float iconW = CustomFontRenderer.stringWidth(itemFont, icon);
                float iconX = thumbX + (THUMBNAIL_SIZE - iconW) / 2f;
                float iconY = thumbY + (THUMBNAIL_SIZE - ITEM_FONT_SIZE) / 2f;
                CustomFontRenderer.drawString(gui, itemFont, icon, iconX, iconY, 0xFFFFFFFF);
            }
        }

        // Text (right side)
        int textX = thumbX + THUMBNAIL_SIZE + 12;
        int textY = y + (int)((h - ITEM_FONT_SIZE - 4) / 2);

        if (itemFont != null && subtitleFont != null) {
            // File name
            String fileName = entry.fileName();
            if (fileName.length() > 25) {
                fileName = fileName.substring(0, 22) + "...";
            }
            CustomFontRenderer.drawString(gui, itemFont, fileName, textX, textY, TEXT_COLOR);

            // Animated label
            if (entry.isAnimated()) {
                float subtitleY = textY + ITEM_FONT_SIZE + 4;
                CustomFontRenderer.drawString(gui, subtitleFont, "Animated", textX, subtitleY, SUBTITLE_COLOR);
            }
        }

        // Delete button (right side)
        int deleteButtonSize = 32;
        int deleteX = x + w - deleteButtonSize - 8;
        int deleteY = y + (h - deleteButtonSize) / 2;

        // Delete button background
        int deleteBgColor = deleteHovered ? 0x80FF4444 : 0x40FF4444;
        CustomRoundedRectRenderer.drawRoundedRect(gui, deleteX, deleteY, deleteButtonSize, deleteButtonSize, 6, deleteBgColor);

        if (deleteHovered) {
            CustomRoundedRectRenderer.drawRoundedOutline(gui, deleteX, deleteY, deleteButtonSize, deleteButtonSize, 6, 0xFFFF4444, 1);
        }

        // Delete icon "×"
        if (itemFont != null) {
            String deleteIcon = "×";
            float deleteIconW = CustomFontRenderer.stringWidth(itemFont, deleteIcon);
            float deleteIconX = deleteX + (deleteButtonSize - deleteIconW) / 2f;
            float deleteIconY = deleteY + (deleteButtonSize - ITEM_FONT_SIZE) / 2f - 4;
            CustomFontRenderer.drawString(gui, itemFont, deleteIcon, deleteIconX, deleteIconY, 0xFFFFFFFF);
        }
    }

    private void drawAddButton(GuiGraphicsExtractor gui, int x, int y, int w, int h, boolean hovered) {

        int bgColor = 0x00000000;
        CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, w, h, 8, bgColor);

        if (hovered) {
            CustomRoundedRectRenderer.drawRoundedOutline(gui, x, y, w, h, 8, ACCENT, 1);
        }

        // Draw "+" using rectangles (cross shape) - smaller size
        int plusSize = 24; // Reduced from 40 to 24
        int plusThickness = 3; // Reduced from 4 to 3
        int plusX = x + (w - plusSize) / 2;
        int plusY = y + (h - plusSize) / 2 - 15;

        // Vertical bar of "+"
        CustomRectRenderer.drawRect(gui,
                plusX + (plusSize - plusThickness) / 2,
                plusY,
                plusThickness,
                plusSize,
                0xFF89DDFF);

        // Horizontal bar of "+"
        CustomRectRenderer.drawRect(gui,
                plusX,
                plusY + (plusSize - plusThickness) / 2,
                plusSize,
                plusThickness,
                0xFF89DDFF);

        // Draw hint text below the "+"
        if (itemFont != null) {
            String hintText = "Add BackGround..";
            float hintW = CustomFontRenderer.stringWidth(itemFont, hintText);
            float hintX = x + (w - hintW) / 2f;
            float hintY = plusY + plusSize + 12;

            CustomFontRenderer.drawString(gui, itemFont, hintText, hintX, hintY, 0xFF89DDFF);
        }
    }

    private boolean isMouseOverItem(int mouseX, int mouseY, int itemY) {
        int listWidth = PANEL_WIDTH - SCROLLBAR_WIDTH - 8;
        return mouseX >= panelX + ITEM_PADDING
                && mouseX <= panelX + listWidth - ITEM_PADDING
                && mouseY >= itemY
                && mouseY <= itemY + ITEM_HEIGHT;
    }

    private boolean isMouseOverDeleteButton(int mouseX, int mouseY, int itemY) {
        int listWidth = PANEL_WIDTH - SCROLLBAR_WIDTH - 8;
        int deleteButtonSize = 32;
        int deleteX = panelX + listWidth - ITEM_PADDING - deleteButtonSize - 8;
        int deleteY = itemY + (ITEM_HEIGHT - deleteButtonSize) / 2;

        return mouseX >= deleteX && mouseX <= deleteX + deleteButtonSize
                && mouseY >= deleteY && mouseY <= deleteY + deleteButtonSize;
    }

    private void drawScrollbar(GuiGraphicsExtractor gui) {
        int totalItems = wallpapers.size() + 1; // +1 for add button
        float totalContentHeight = totalItems * (ITEM_HEIGHT + ITEM_PADDING);

        // Only draw scrollbar if content exceeds visible area
        if (totalContentHeight <= listHeight) {
            return;
        }

        // Scrollbar track position (right side of panel)
        int scrollbarX = panelX + PANEL_WIDTH - SCROLLBAR_WIDTH - 4;
        int scrollbarY = listY + 4;
        int scrollbarHeight = listHeight - 8;

        // Calculate scrollbar thumb size and position
        float visibleRatio = listHeight / totalContentHeight;
        int thumbHeight = Math.max(20, (int)(scrollbarHeight * visibleRatio));

        // Calculate scroll ratio (clamp between 0 and 1)
        float maxScroll = totalContentHeight - listHeight;
        float scrollRatio = maxScroll > 0 ? Math.max(0, Math.min(1, -scrollOffset / maxScroll)) : 0;
        int thumbY = scrollbarY + (int)((scrollbarHeight - thumbHeight) * scrollRatio);

        // Draw scrollbar track (subtle background)
        CustomRoundedRectRenderer.drawRoundedRect(gui, scrollbarX, scrollbarY,
                SCROLLBAR_WIDTH, scrollbarHeight, 3, 0x20FFFFFF);

        // Draw scrollbar thumb
        CustomRoundedRectRenderer.drawRoundedRect(gui, scrollbarX, thumbY,
                SCROLLBAR_WIDTH, thumbHeight, 3, 0x8089DDFF);
    }

    // ========================
    // Input Handling
    // ========================

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean idk) {
        int mx = (int) mouse.x();
        int my = (int) mouse.y();

        // Check if click is inside panel first
        boolean insidePanel = mx >= panelX && mx <= panelX + PANEL_WIDTH
                && my >= panelY && my <= panelY + PANEL_HEIGHT;

        if (insidePanel) {
            // Scrollbar dragging is handled in extractRenderState
            if (isMouseOverScrollbar(mx, my)) {
                return true; // Consume click on scrollbar
            }

            // Check if delete button was clicked
            if (hoveredDeleteIndex >= 0 && hoveredDeleteIndex < wallpapers.size()) {
                deleteWallpaper(hoveredDeleteIndex);
                return true;
            }

            // Click on add button (hoveredIndex == -2)
            if (hoveredIndex == -2) {
                openFileChooser();
                return true;
            }

            // Click on item
            if (hoveredIndex >= 0 && hoveredIndex < wallpapers.size()) {
                selectWallpaper(hoveredIndex);
                return true;
            }

            return true; // Consume click inside panel
        } else {
            // Click outside panel to close
            onClose();
            return true;
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Disable mouse scroll - only allow dragging scrollbar
        return false;
    }

    private boolean isMouseOverScrollbar(int mouseX, int mouseY) {
        int totalItems = wallpapers.size() + 1;
        float totalContentHeight = totalItems * (ITEM_HEIGHT + ITEM_PADDING);

        // Only show scrollbar if content exceeds visible area
        if (totalContentHeight <= listHeight) {
            return false;
        }

        int scrollbarX = panelX + PANEL_WIDTH - SCROLLBAR_WIDTH - 4;
        int scrollbarY = listY + 4;
        int scrollbarHeight = listHeight - 8;

        return mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_WIDTH
                && mouseY >= scrollbarY && mouseY <= scrollbarY + scrollbarHeight;
    }

    private void selectWallpaper(int index) {
        selectedIndex = index;
        WallpaperEntry entry = wallpapers.get(index);

        // Update BackgroundConfig
        backgroundConfig.setSelectedWallpaper(entry.filePath());
        backgroundConfig.setCustomBackgroundEnabled(true);

        // If parent is MainMenuScreen, trigger texture reload
        if (parent instanceof MainMenuScreen) {
            ((MainMenuScreen) parent).reloadCustomBackground();
        }

        // Close and refresh parent
        onClose();
    }

    private void deleteWallpaper(int index) {
        try {
            WallpaperEntry entry = wallpapers.get(index);
            Path filePath = entry.filePath();

            System.out.println("[BackgroundSelector] Deleting wallpaper: " + filePath);

            // Check if this is the currently selected wallpaper
            boolean wasSelected = (index == selectedIndex);
            Path currentBg = backgroundConfig.getCustomBackgroundFile();
            boolean isCurrentBg = filePath.equals(currentBg);

            // Delete the file
            Files.deleteIfExists(filePath);
            System.out.println("[BackgroundSelector] Wallpaper deleted successfully");

            // Remove from list
            wallpapers.remove(index);

            // Clean up thumbnail cache
            Identifier thumbnailId = thumbnailCache.remove(filePath);
            if (thumbnailId != null) {
                AbstractTexture texture = this.minecraft.getTextureManager().getTexture(thumbnailId);
                if (texture != null) {
                    texture.close();
                }
                this.minecraft.getTextureManager().release(thumbnailId);
            }

            // Update selected index
            if (wasSelected || isCurrentBg) {
                // If deleted wallpaper was selected, select the first one if available
                if (!wallpapers.isEmpty()) {
                    selectedIndex = 0;
                    backgroundConfig.setSelectedWallpaper(wallpapers.get(0).filePath());
                    backgroundConfig.setCustomBackgroundEnabled(true);
                } else {
                    selectedIndex = -1;
                    backgroundConfig.setCustomBackgroundEnabled(false);
                }

                // Reload parent background
                if (parent instanceof MainMenuScreen) {
                    ((MainMenuScreen) parent).reloadCustomBackground();
                }
            } else if (selectedIndex > index) {
                // Adjust selected index if item before it was deleted
                selectedIndex--;
            }

        } catch (Exception e) {
            System.err.println("[BackgroundSelector] Error deleting wallpaper: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void openFileChooser() {
        // Use LWJGL's TinyFileDialogs for native file chooser (works without AWT/Swing)
        try {
            System.out.println("[BackgroundSelector] Opening file chooser...");

            // Run on a separate thread to avoid blocking the render thread
            Thread fileChooserThread = new Thread(() -> {
                try {
                    System.out.println("[BackgroundSelector] File chooser thread started");

                    // Use TinyFileDialogs for cross-platform native file dialog
                    String selectedPath = TinyFileDialogs.tinyfd_openFileDialog(
                            "Select Wallpaper",
                            System.getProperty("user.home"),
                            null,  // No filter patterns for simplicity
                            "Image Files (*.png, *.jpg, *.jpeg, *.gif, *.mp4, *.webm)",
                            false
                    );

                    System.out.println("[BackgroundSelector] Selected path: " + selectedPath);

                    if (selectedPath != null && !selectedPath.isEmpty()) {
                        File selectedFile = new File(selectedPath);
                        System.out.println("[BackgroundSelector] File selected: " + selectedFile.getAbsolutePath());

                        // Copy file to wallpapers directory
                        Path targetDir = backgroundConfig.getBackgroundsDirectory();
                        System.out.println("[BackgroundSelector] Target directory: " + targetDir);

                        Path targetPath = targetDir.resolve(selectedFile.getName());

                        // Handle duplicate filenames
                        int counter = 1;
                        String baseName = selectedFile.getName();
                        String extension = "";
                        int dotIndex = baseName.lastIndexOf('.');
                        if (dotIndex > 0) {
                            extension = baseName.substring(dotIndex);
                            baseName = baseName.substring(0, dotIndex);
                        }

                        while (Files.exists(targetPath)) {
                            targetPath = targetDir.resolve(baseName + "_" + counter + extension);
                            counter++;
                        }

                        final Path finalTargetPath = targetPath;

                        // Copy file
                        Files.copy(selectedFile.toPath(), finalTargetPath);
                        System.out.println("[BackgroundSelector] Wallpaper copied to: " + finalTargetPath);

                        // Execute on Minecraft thread to update UI
                        this.minecraft.execute(() -> {
                            try {
                                System.out.println("[BackgroundSelector] Updating UI on Minecraft thread");

                                // Set as current wallpaper
                                backgroundConfig.setSelectedWallpaper(finalTargetPath);
                                backgroundConfig.setCustomBackgroundEnabled(true);

                                // Reload parent background
                                if (parent instanceof MainMenuScreen) {
                                    ((MainMenuScreen) parent).reloadCustomBackground();
                                }

                                // Close and reopen selector to refresh list
                                onClose();
                                this.minecraft.gui.setScreen(new BackgroundSelectorScreen(parent, backgroundConfig));

                                System.out.println("[BackgroundSelector] UI update complete");
                            } catch (Exception e) {
                                System.err.println("[BackgroundSelector] Error updating UI: " + e.getMessage());
                                e.printStackTrace();
                            }
                        });
                    } else {
                        System.out.println("[BackgroundSelector] File selection cancelled");
                    }
                } catch (Exception e) {
                    System.err.println("[BackgroundSelector] Error in file chooser thread: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            fileChooserThread.setDaemon(true);
            fileChooserThread.setName("BackgroundSelector-FileChooser");
            fileChooserThread.start();
            System.out.println("[BackgroundSelector] File chooser thread started successfully");
        } catch (Exception e) {
            System.err.println("[BackgroundSelector] Error launching file chooser: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onClose() {
        // Force refresh parent screen to apply particle settings
        if (parent instanceof MainMenuScreen) {
            ((MainMenuScreen) parent).reloadCustomBackground();
        }
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        super.removed();
        // Clean up thumbnails
        for (Identifier id : thumbnailCache.values()) {
            AbstractTexture texture = this.minecraft.getTextureManager().getTexture(id);
            if (texture != null) {
                texture.close();
            }
            this.minecraft.getTextureManager().release(id);
        }
        thumbnailCache.clear();
    }
}
