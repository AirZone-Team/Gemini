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

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
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
    private static final int THUMBNAIL_SIZE = 64;
    private static final int SCROLL_SPEED = 20;

    // Fonts
    private static final Identifier FONT_BOLD =
            Identifier.fromNamespaceAndPath("gemini", "font/sourcehansanssc-bold.ttf");
    private static final Identifier FONT_MEDIUM =
            Identifier.fromNamespaceAndPath("gemini", "font/sourcehansanssc-medium.ttf");
    private static final Identifier FONT_LIGHT =
            Identifier.fromNamespaceAndPath("gemini", "font/sourcehansanssc-light.ttf");

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

        // Load fonts
        loadFonts();

        // Scan wallpapers
        scanWallpapers();

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
        if (thumbnailCache.containsKey(entry.filePath())) {
            return thumbnailCache.get(entry.filePath());
        }

        // Only generate thumbnails for static images
        if (entry.isAnimated()) {
            return null; // Will show placeholder for animated
        }

        try {
            BufferedImage img = ImageIO.read(entry.filePath().toFile());
            if (img == null) return null;

            // Scale to thumbnail size with high quality
            int thumbSize = THUMBNAIL_SIZE;
            BufferedImage scaled = new BufferedImage(thumbSize, thumbSize, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g2d = scaled.createGraphics();

            // Use high-quality rendering hints
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                    java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

            // Calculate aspect-fit scaling
            float scale = Math.min((float) thumbSize / img.getWidth(), (float) thumbSize / img.getHeight());
            int scaledW = (int) (img.getWidth() * scale);
            int scaledH = (int) (img.getHeight() * scale);
            int offsetX = (thumbSize - scaledW) / 2;
            int offsetY = (thumbSize - scaledH) / 2;

            g2d.drawImage(img, offsetX, offsetY, scaledW, scaledH, null);
            g2d.dispose();

            // Convert to NativeImage
            NativeImage nativeImg = new NativeImage(thumbSize, thumbSize, true);
            for (int y = 0; y < thumbSize; y++) {
                for (int x = 0; x < thumbSize; x++) {
                    int argb = scaled.getRGB(x, y);
                    nativeImg.setPixel(x, y, argb);
                }
            }

            // Register texture
            DynamicTexture texture = new DynamicTexture(() -> "thumbnail_" +
                    entry.filePath().getFileName().toString().hashCode(), nativeImg);
            Identifier id = Identifier.fromNamespaceAndPath("gemini", "thumbnail_" +
                    entry.filePath().getFileName().toString().hashCode());
            this.minecraft.getTextureManager().register(id, texture);
            thumbnailCache.put(entry.filePath(), id);

            return id;
        } catch (Exception e) {
            return null;
        }
    }

    // ========================
    // Rendering
    // ========================

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        // Smooth scroll
        scrollOffset += (targetScrollOffset - scrollOffset) * delta * 10f;

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
                12, 0x20161D28, 18f);

        // Shadow
        SdfUIRenderer.drawShadow(gui, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 12, 0, 4, 20, 0xA0000000);

        // Semi-transparent dark background - lighter
        CustomRoundedRectRenderer.drawRoundedRect(gui, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 12, 0x90161D28);

        // Brighter outline
        CustomRoundedRectRenderer.drawRoundedOutline(gui, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 12, 0x8089DDFF, 2);
    }

    private void drawTitle(GuiGraphicsExtractor gui) {
        if (titleFont == null || itemFont == null) return;

        // Title on the left
        String title = "Background";
        float titleX = panelX + 20;
        float titleY = panelY + (TITLE_HEIGHT - TITLE_FONT_SIZE) / 2f;
        CustomFontRenderer.drawString(gui, titleFont, title, titleX, titleY, TITLE_COLOR);

        // Particle toggle on the right
        String particleLabel = "Particles";
        float particleLabelW = CustomFontRenderer.stringWidth(itemFont, particleLabel);

        // Toggle switch position
        int toggleW = 40;
        int toggleH = 20;
        int toggleX = panelX + PANEL_WIDTH - toggleW - 20;
        int toggleY = panelY + (TITLE_HEIGHT - toggleH) / 2;

        // Label position (left of toggle)
        float labelX = toggleX - particleLabelW - 10;
        float labelY = panelY + (TITLE_HEIGHT - ITEM_FONT_SIZE) / 2f;

        // Draw label
        CustomFontRenderer.drawString(gui, itemFont, particleLabel, labelX, labelY, TEXT_COLOR);

        // Get particle state from config
        boolean particlesEnabled = backgroundConfig.isParticlesEnabled();
        int toggleBg = particlesEnabled ? 0x8089DDFF : 0x40FFFFFF;
        CustomRoundedRectRenderer.drawRoundedRect(gui, toggleX, toggleY, toggleW, toggleH, 10, toggleBg);

        // Toggle knob
        int knobSize = 16;
        int knobX = particlesEnabled ? toggleX + toggleW - knobSize - 2 : toggleX + 2;
        int knobY = toggleY + 2;
        CustomRoundedRectRenderer.drawRoundedRect(gui, knobX, knobY, knobSize, knobSize, 8, 0xFFFFFFFF);

        // Divider line
        int lineY = panelY + TITLE_HEIGHT - 1;
        CustomRectRenderer.drawRect(gui, panelX, lineY, PANEL_WIDTH, 1, 0x4089DDFF);
    }

    private void drawList(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        if (itemFont == null) return;

        // Scissor to panel area
        gui.enableScissor(panelX, listY, panelX + PANEL_WIDTH, listY + listHeight);

        hoveredIndex = -1;
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

            drawListItem(gui, entry, panelX + ITEM_PADDING, itemY,
                    PANEL_WIDTH - ITEM_PADDING * 2, ITEM_HEIGHT, hovered, selected);
        }

        // Draw "+" button at the end
        int addButtonIndex = wallpapers.size();
        int addButtonY = listY + yOffset + addButtonIndex * (ITEM_HEIGHT + ITEM_PADDING);

        // Check if visible
        if (addButtonY <= listY + listHeight && addButtonY + ITEM_HEIGHT >= listY) {
            boolean addButtonHovered = isMouseOverItem(mouseX, mouseY, addButtonY);
            if (addButtonHovered) hoveredIndex = -2; // Special index for add button
            drawAddButton(gui, panelX + ITEM_PADDING, addButtonY,
                    PANEL_WIDTH - ITEM_PADDING * 2, ITEM_HEIGHT, addButtonHovered);
        }

        gui.disableScissor();
    }

    private void drawListItem(GuiGraphicsExtractor gui, WallpaperEntry entry,
                              int x, int y, int w, int h, boolean hovered, boolean selected) {
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
            // Draw thumbnail
            gui.blit(RenderPipelines.GUI_TEXTURED, thumbnail,
                    thumbX, thumbY, 0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE,
                    THUMBNAIL_SIZE, THUMBNAIL_SIZE, 0xFFFFFFFF);
        } else {
            // Placeholder for thumbnails (animated or failed to load)
            CustomRoundedRectRenderer.drawRoundedRect(gui, thumbX, thumbY,
                    THUMBNAIL_SIZE, THUMBNAIL_SIZE, 4, 0x40FFFFFF);

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
            if (fileName.length() > 35) {
                fileName = fileName.substring(0, 32) + "...";
            }
            CustomFontRenderer.drawString(gui, itemFont, fileName, textX, textY, TEXT_COLOR);

            // Animated label
            if (entry.isAnimated()) {
                float subtitleY = textY + ITEM_FONT_SIZE + 4;
                CustomFontRenderer.drawString(gui, subtitleFont, "Animated", textX, subtitleY, SUBTITLE_COLOR);
            }
        }
    }

    private void drawAddButton(GuiGraphicsExtractor gui, int x, int y, int w, int h, boolean hovered) {
        // Background
        int bgColor = hovered ? ITEM_HOVER_BG : ITEM_BG;
        CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, w, h, 8, bgColor);

        // Border
        CustomRoundedRectRenderer.drawRoundedOutline(gui, x, y, w, h, 8, 0x4089DDFF, 1);

        // Draw "+" icon in center (same size as thumbnail)
        int thumbX = x + ITEM_PADDING;
        int thumbY = y + (h - THUMBNAIL_SIZE) / 2;

        // Thumbnail-sized background for "+"
        CustomRoundedRectRenderer.drawRoundedRect(gui, thumbX, thumbY, THUMBNAIL_SIZE, THUMBNAIL_SIZE, 8, 0x4089DDFF);

        // Draw "+" symbol
        if (titleFont != null) {
            String plusText = "+";
            float plusW = CustomFontRenderer.stringWidth(titleFont, plusText);
            float plusX = thumbX + (THUMBNAIL_SIZE - plusW) / 2f;
            float plusY = thumbY + (THUMBNAIL_SIZE - TITLE_FONT_SIZE) / 2f;
            CustomFontRenderer.drawString(gui, titleFont, plusText, plusX, plusY, 0xFFFFFFFF);
        }

        // Text label
        int textX = thumbX + THUMBNAIL_SIZE + 12;
        int textY = y + (int)((h - ITEM_FONT_SIZE) / 2);

        if (itemFont != null) {
            CustomFontRenderer.drawString(gui, itemFont, "Add Wallpaper...", textX, textY, TEXT_COLOR);
        }
    }

    private boolean isMouseOverItem(int mouseX, int mouseY, int itemY) {
        return mouseX >= panelX + ITEM_PADDING
                && mouseX <= panelX + PANEL_WIDTH - ITEM_PADDING
                && mouseY >= itemY
                && mouseY <= itemY + ITEM_HEIGHT;
    }

    private boolean isMouseOverParticleToggle(int mouseX, int mouseY) {
        int toggleW = 40;
        int toggleH = 20;
        int toggleX = panelX + PANEL_WIDTH - toggleW - 20;
        int toggleY = panelY + (TITLE_HEIGHT - toggleH) / 2;

        return mouseX >= toggleX && mouseX <= toggleX + toggleW
                && mouseY >= toggleY && mouseY <= toggleY + toggleH;
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
            // Click on particle toggle
            if (isMouseOverParticleToggle(mx, my)) {
                boolean oldState = backgroundConfig.isParticlesEnabled();
                backgroundConfig.toggleParticles();
                boolean newState = backgroundConfig.isParticlesEnabled();
                System.out.println("[BackgroundSelector] Particle toggle clicked! " + oldState + " -> " + newState);
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
        // Scroll list
        float maxScroll = Math.max(0, wallpapers.size() * (ITEM_HEIGHT + ITEM_PADDING) - listHeight);
        targetScrollOffset = Math.max(-maxScroll, Math.min(0, targetScrollOffset + (float) scrollY * SCROLL_SPEED));
        return true;
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

    private void openFileChooser() {
        // Use AWT FileDialog for native Windows file chooser
        new Thread(() -> {
            try {
                javax.swing.JFileChooser fileChooser = new javax.swing.JFileChooser();
                fileChooser.setDialogTitle("Select Wallpaper");
                fileChooser.setFileSelectionMode(javax.swing.JFileChooser.FILES_ONLY);

                // File filter for images
                javax.swing.filechooser.FileNameExtensionFilter filter =
                    new javax.swing.filechooser.FileNameExtensionFilter(
                        "Image Files (PNG, JPG, JPEG, GIF, MP4, WEBM)",
                        "png", "jpg", "jpeg", "gif", "mp4", "webm");
                fileChooser.setFileFilter(filter);

                int result = fileChooser.showOpenDialog(null);

                if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                    java.io.File selectedFile = fileChooser.getSelectedFile();

                    // Copy file to wallpapers directory
                    Path targetDir = backgroundConfig.getBackgroundsDirectory();
                    Path targetPath = targetDir.resolve(selectedFile.getName());

                    // If file already exists, add number suffix
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

                    final Path finalTargetPath = targetPath; // Make it final for lambda
                    Files.copy(selectedFile.toPath(), finalTargetPath);
                    System.out.println("[BackgroundSelector] Added wallpaper: " + finalTargetPath);

                    // Rescan backgrounds and close selector to refresh
                    this.minecraft.execute(() -> {
                        backgroundConfig.setSelectedWallpaper(finalTargetPath);
                        backgroundConfig.setCustomBackgroundEnabled(true);

                        if (parent instanceof MainMenuScreen) {
                            ((MainMenuScreen) parent).reloadCustomBackground();
                        }

                        // Close and reopen selector to show new wallpaper
                        onClose();
                        this.minecraft.gui.setScreen(new BackgroundSelectorScreen(parent, backgroundConfig));
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void onClose() {
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
