package geminiclient.gemini.customRenderer.glsl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ARGB;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static geminiclient.gemini.base.MinecraftInstance.mc;

/**
 * GPU-accelerated image processing utility.
 *
 * <p>Provides upload/download between {@link NativeImage} and GPU
 * {@link DynamicTexture}. Uses NeoForge's {@link CommandEncoder} API
 * for GPU readback instead of raw GL calls.</p>
 *
 * <h3>Upload pattern</h3>
 * <pre>{@code
 * DynamicTexture tex = GpuImageProcessor.upload(image, "input");
 * // Use tex in GPU rendering...
 * GpuImageProcessor.release(tex, "input");
 * }</pre>
 *
 * <h3>Download pattern (async)</h3>
 * <pre>{@code
 * CompletableFuture<NativeImage> future = GpuImageProcessor.downloadAsync(texture, w, h);
 * future.thenAccept(img -> {
 *     // process img on client thread...
 *     img.close();
 * });
 * }</pre>
 */
public class GpuImageProcessor {

    /**
     * Upload a NativeImage to a new DynamicTexture (GPU memory).
     * The texture is registered with the TextureManager. The source
     * image is NOT closed.
     *
     * @param image source RGBA image
     * @param label unique debug label for TextureManager
     * @return registered DynamicTexture
     */
    public static DynamicTexture upload(NativeImage image, String label) {
        NativeImage copy = cloneImage(image);
        DynamicTexture tex = new DynamicTexture(() -> label, copy);
        mc.getTextureManager().register(
                geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier("gpu_" + label),
                tex);
        copy.close();
        return tex;
    }

    /**
     * Asynchronously download GPU texture data to a new NativeImage.
     * Uses {@link CommandEncoder#copyTextureToBuffer} for proper GPU readback.
     *
     * <p>The returned {@link CompletableFuture} completes when the GPU copy
     * finishes. The caller MUST close the NativeImage when done.</p>
     *
     * @param texture the DynamicTexture to read back
     * @param width   texture width in pixels
     * @param height  texture height in pixels
     * @return future that completes with the downloaded NativeImage
     */
    public static CompletableFuture<NativeImage> downloadAsync(DynamicTexture texture, int width, int height) {
        RenderSystem.assertOnRenderThread();

        GpuDevice device = RenderSystem.getDevice();
        CommandEncoder encoder = device.createCommandEncoder();

        long bufferSize = (long) width * height * 4;
        GpuBuffer stagingBuffer = device.createBuffer(
                () -> "gpu_download_buf",
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ,
                bufferSize
        );

        CompletableFuture<NativeImage> future = new CompletableFuture<>();

        encoder.copyTextureToBuffer(texture.getTexture(), stagingBuffer, 0, () -> {
            try {
                GpuBuffer.MappedView view = encoder.mapBuffer(stagingBuffer, true, false);
                ByteBuffer data = view.data();
                NativeImage img = new NativeImage(width, height, false);
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int idx = (y * width + x) * 4;
                        int r = data.get(idx) & 0xFF;
                        int g = data.get(idx + 1) & 0xFF;
                        int b = data.get(idx + 2) & 0xFF;
                        int a = data.get(idx + 3) & 0xFF;
                        img.setPixel(x, y, ARGB.color(a, r, g, b));
                    }
                }
                view.close();
                stagingBuffer.close();
                future.complete(img);
            } catch (Exception e) {
                stagingBuffer.close();
                future.completeExceptionally(e);
            }
        }, 0);

        return future;
    }

    /**
     * Synchronously download GPU texture data to a new NativeImage.
     * Blocks the calling thread until the GPU copy completes.
     * Must be called on render thread.
     *
     * @param texture the DynamicTexture to read back
     * @param width   texture width in pixels
     * @param height  texture height in pixels
     * @return new NativeImage with RGBA pixel data
     */
    public static NativeImage download(DynamicTexture texture, int width, int height) {
        try {
            return downloadAsync(texture, width, height).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Download interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Download failed", e.getCause());
        }
    }

    /**
     * Release a texture from TextureManager and free VRAM.
     */
    public static void release(DynamicTexture texture, String label) {
        mc.getTextureManager().release(
                geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier("gpu_" + label));
        texture.close();
    }

    // ========================
    //  Utility
    // ========================

    public static NativeImage cloneImage(NativeImage source) {
        int w = source.getWidth(), h = source.getHeight();
        NativeImage copy = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                copy.setPixel(x, y, source.getPixel(x, y));
            }
        }
        return copy;
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static int clamp8(int v) {
        return clamp(v, 0, 255);
    }

    static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
