package geminiclient.gemini.utils;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class ResourceLocationUtils {

    public static Identifier getIdentifier(String path) {
        return Identifier.fromNamespaceAndPath("gemini", path);
    }

    public static ByteBuffer loadResource(Identifier identifier) {
        final var manager = mc.getResourceManager();
        Optional<Resource> resource = manager.getResource(identifier);

        if (resource.isEmpty()) {
            throw new RuntimeException("Couldn't find resource at " + identifier);
        }

        try (InputStream is = resource.get().open()) {
            byte[] bytes = is.readAllBytes();
            ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
            buffer.put(bytes);
            buffer.flip();
            return buffer;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read file", e);
        }
    }
}
