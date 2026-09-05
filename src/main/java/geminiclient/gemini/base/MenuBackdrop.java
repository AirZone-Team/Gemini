package geminiclient.gemini.base;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import geminiclient.gemini.Gemini;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 主菜单与 AltManager 共享的自定义壁纸背景层。
 *
 * <p>负责自定义壁纸的加载与绘制：静态图（NativeImage / ImageIO 回退）与
 * JavaCV 视频（持久上传缓冲 + GPU 拷贝），带 110% 缩放的鼠标视差。
 * 两个客户端菜单界面共享同一份静态状态，因此从主菜单点进 AltManager
 * （或返回）时视频解码器与纹理不会销毁重建。</p>
 *
 * <p>生命周期用引用计数管理：每个使用方在 {@code init()} 调
 * {@link #acquire()}、{@code removed()} 调 {@link #release()}；计数归零
 * （进入世界或打开其他界面）才真正释放视频解码线程与纹理。壁纸开关与
 * 切换仍由 {@link FileSystem} 保存，通过 {@link #reload()} 通知重建。</p>
 */
public final class MenuBackdrop {

    private static FileSystem fileSystem;
    private static Identifier customBackgroundTexture;
    private static DynamicTexture customBackgroundDynamicTexture;
    private static GpuVideoTexture customBackgroundVideoTexture;
    /** Reused PBO-equivalent: CPU writes once, then the device copies it to the texture. */
    private static GpuBuffer customBackgroundUploadBuffer;
    private static Path customBackgroundFile;
    private static JavaCvVideoBackground customBackgroundVideo;
    private static boolean customBackgroundLoadFailed = false;

    private static int refCount;

    private MenuBackdrop() {
    }

    private static FileSystem fs() {
        if (fileSystem == null) {
            fileSystem = Gemini.fileSystem;
        }
        return fileSystem;
    }

    // ========================
    //  Lifecycle
    // ========================

    /** 使用方界面打开（init）时调用；计数 &gt; 0 期间壁纸资源保持存活。 */
    public static void acquire() {
        refCount++;
    }

    /** 使用方界面关闭（removed）时调用；计数归零才释放视频解码器与纹理。 */
    public static void release() {
        refCount = Math.max(0, refCount - 1);
        if (refCount == 0) {
            releaseCustomBackground();
        }
    }

    /** 壁纸文件被切换/重新导入后调用，下一次 render 会按新文件重建。 */
    public static void reload() {
        releaseCustomBackground();
        customBackgroundLoadFailed = false;
    }

    /** 立即释放背景资源（壁纸被关闭时调用，视频解码器不能空转）。 */
    public static void releaseAll() {
        releaseCustomBackground();
    }

    /** 自定义壁纸当前是否实际绘制（决定界面是否回退到网格背景）。 */
    public static boolean isActive() {
        FileSystem fs = fs();
        return fs != null && fs.isCustomBackgroundEnabled()
                && fs.customBackgroundFileExists() && !customBackgroundLoadFailed;
    }

    /** 是否存在可用的壁纸文件（不考虑开关状态）。 */
    public static boolean wallpaperAvailable() {
        FileSystem fs = fs();
        return fs != null && fs.customBackgroundFileExists();
    }

    /** 拖放/导入新壁纸；成功导入时返回 true（调用方通常需要 reload）。 */
    public static boolean importWallpapers(List<Path> files) {
        FileSystem fs = fs();
        if (fs == null) {
            return false;
        }
        return fs.importFirstWallpaper(files).isPresent();
    }

    // ========================
    //  Render
    // ========================

    public static void render(GuiGraphicsExtractor gui, int width, int height, float mouseX, float mouseY) {
        FileSystem fs = fs();
        if (fs == null || !fs.isCustomBackgroundEnabled()) {
            return;
        }
        renderCustomBackground(gui, width, height, mouseX, mouseY);
    }

    private static void renderCustomBackground(GuiGraphicsExtractor gui, int width, int height,
                                               float mouseX, float mouseY) {
        if (!fs().customBackgroundFileExists()) {
            return;
        }

        Path bgFile = fs().getCustomBackgroundFile();
        if (bgFile != null) {
            bgFile = bgFile.toAbsolutePath().normalize();
        }
        if (bgFile == null || !Files.exists(bgFile)) {
            customBackgroundLoadFailed = true;
            return;
        }

        if (customBackgroundFile == null || !customBackgroundFile.equals(bgFile)) {
            releaseCustomBackground();
            customBackgroundFile = bgFile;
        }

        if (JavaCvVideoBackground.isSupportedVideo(bgFile)) {
            renderVideoBackground(gui, bgFile, width, height, mouseX, mouseY);
            return;
        }

        if (customBackgroundLoadFailed || customBackgroundTexture == null) {
            loadStaticBackground(bgFile);
        }

        drawCustomBackground(gui, width, height, mouseX, mouseY);
    }

    private static void loadStaticBackground(Path bgFile) {
        if (customBackgroundLoadFailed || customBackgroundTexture != null) {
            return;
        }

        try {
            NativeImage image = readBackgroundImage(bgFile);
            if (image == null) {
                customBackgroundLoadFailed = true;
                return;
            }

            customBackgroundDynamicTexture = new DynamicTexture(() -> "custom_background", image);
            customBackgroundTexture = Identifier.fromNamespaceAndPath("gemini", "custom_background");
            MinecraftInstance.mc.getTextureManager().register(customBackgroundTexture, customBackgroundDynamicTexture);
        } catch (Exception e) {
            customBackgroundLoadFailed = true;
        }
    }

    private static NativeImage readBackgroundImage(Path bgFile) {
        try (FileInputStream fis = new FileInputStream(bgFile.toFile())) {
            return NativeImage.read(fis);
        } catch (IOException readError) {
            return readBackgroundImageWithImageIo(bgFile);
        }
    }

    private static NativeImage readBackgroundImageWithImageIo(Path bgFile) {
        try {
            BufferedImage bufferedImage = ImageIO.read(bgFile.toFile());
            if (bufferedImage == null) {
                return null;
            }

            int width = bufferedImage.getWidth();
            int height = bufferedImage.getHeight();
            NativeImage image = new NativeImage(width, height, false);

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.setPixel(x, y, bufferedImage.getRGB(x, y));
                }
            }
            return image;
        } catch (Exception fallbackError) {
            return null;
        }
    }

    private static void renderVideoBackground(GuiGraphicsExtractor gui, Path bgFile,
                                              int width, int height, float mouseX, float mouseY) {
        if (customBackgroundLoadFailed) {
            return;
        }

        if (customBackgroundVideo == null || !customBackgroundVideo.isFor(bgFile)) {
            releaseCustomBackground();
            customBackgroundFile = bgFile;
            customBackgroundVideo = new JavaCvVideoBackground(bgFile);
            customBackgroundVideo.start();
        }

        JavaCvVideoBackground.VideoFrame frame = customBackgroundVideo.pollFrame();
        if (frame != null) {
            try {
                uploadVideoFrame(frame);
            } finally {
                customBackgroundVideo.releaseFrame(frame);
            }
        }

        if (customBackgroundVideo.hasFailed()) {
            customBackgroundLoadFailed = true;
            return;
        }

        drawCustomBackground(gui, width, height, mouseX, mouseY);
    }

    private static void uploadVideoFrame(JavaCvVideoBackground.VideoFrame frame) {
        if (customBackgroundVideoTexture == null
                || customBackgroundVideoTexture.width() != frame.width()
                || customBackgroundVideoTexture.height() != frame.height()) {
            releaseCustomBackgroundTexture();
            customBackgroundVideoTexture = new GpuVideoTexture(
                    () -> "custom_background_video", frame.width(), frame.height());
            customBackgroundTexture = Identifier.fromNamespaceAndPath("gemini", "custom_background");
            MinecraftInstance.mc.getTextureManager().register(customBackgroundTexture, customBackgroundVideoTexture);
        }

        int byteCount = Math.multiplyExact(Math.multiplyExact(frame.width(), frame.height()), 4);
        if (customBackgroundUploadBuffer == null || customBackgroundUploadBuffer.isClosed()
                || customBackgroundUploadBuffer.size() < byteCount) {
            closeVideoUploadBuffer();
            customBackgroundUploadBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "custom_background_video_upload",
                    GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_COPY_SRC,
                    byteCount);
        }

        // FFmpeg's direct RGBA bytes are copied to one persistent upload buffer, then
        // copied entirely on the GPU into a GpuTexture. This removes NativeImage's
        // per-frame full-frame CPU copy from the video path.
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(customBackgroundUploadBuffer.slice(0L, byteCount), frame.pixels().duplicate());
        encoder.copyBufferToTexture(
                customBackgroundUploadBuffer.slice(0L, byteCount),
                0, 0, frame.width(), frame.height(),
                customBackgroundVideoTexture.getTexture(),
                0, 0, frame.width(), frame.height(), 0, 0);
    }

    private static void drawCustomBackground(GuiGraphicsExtractor gui, int width, int height,
                                             float mouseX, float mouseY) {
        if (customBackgroundTexture == null) {
            return;
        }

        // Render the custom background with parallax effect
        try {
            // Calculate parallax offset based on mouse position
            float parallaxOffsetX = ((mouseX / (float) width) - 0.5f) * 40f;
            float parallaxOffsetY = ((mouseY / (float) height) - 0.5f) * 40f;

            // Scale image by 110% to cover parallax movement and avoid tiling
            float scale = 1.1f;
            int scaledWidth = (int) (width * scale);
            int scaledHeight = (int) (height * scale);

            // Center the scaled image and apply parallax offset
            int renderX = (int) (-(scaledWidth - width) / 2f + parallaxOffsetX);
            int renderY = (int) (-(scaledHeight - height) / 2f + parallaxOffsetY);

            // Render: blit(pipeline, texture, screenX, screenY, texU, texV, screenW, screenH, texWidth, texHeight, color)
            gui.blit(RenderPipelines.GUI_TEXTURED, customBackgroundTexture,
                    renderX, renderY, 0, 0, scaledWidth, scaledHeight, scaledWidth, scaledHeight, 0xFFFFFFFF);
        } catch (Exception e) {
            // Silently fail
        }
    }

    private static void releaseCustomBackground() {
        if (customBackgroundVideo != null) {
            customBackgroundVideo.close();
            customBackgroundVideo = null;
        }
        releaseCustomBackgroundTexture();
        customBackgroundFile = null;
        customBackgroundLoadFailed = false;
    }

    private static void releaseCustomBackgroundTexture() {
        closeVideoUploadBuffer();
        if (customBackgroundTexture == null) {
            customBackgroundDynamicTexture = null;
            customBackgroundVideoTexture = null;
            return;
        }

        AbstractTexture texture = MinecraftInstance.mc.getTextureManager().getTexture(customBackgroundTexture);
        if (texture != null) {
            texture.close();
        }
        MinecraftInstance.mc.getTextureManager().release(customBackgroundTexture);
        customBackgroundTexture = null;
        customBackgroundDynamicTexture = null;
        customBackgroundVideoTexture = null;
    }

    private static void closeVideoUploadBuffer() {
        if (customBackgroundUploadBuffer != null && !customBackgroundUploadBuffer.isClosed()) {
            customBackgroundUploadBuffer.close();
        }
        customBackgroundUploadBuffer = null;
    }
}
