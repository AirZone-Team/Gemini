package geminiclient.gemini.base;

import java.nio.file.Path;

/**
 * Represents a wallpaper entry (static image or animated video).
 */
public record WallpaperEntry(
        Path filePath,
        String fileName,
        WallpaperType type,
        long fileSize
) {
    public enum WallpaperType {
        STATIC,    // PNG, JPG, JPEG
        ANIMATED   // MP4, WEBM, GIF
    }

    /**
     * Determines wallpaper type from file extension.
     */
    public static WallpaperType getTypeFromPath(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".mp4") || name.endsWith(".webm") || name.endsWith(".gif")) {
            return WallpaperType.ANIMATED;
        }
        return WallpaperType.STATIC;
    }

    public boolean isAnimated() {
        return type == WallpaperType.ANIMATED;
    }
}
