package net.minecraft.client.main;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SilentInitException extends RuntimeException {
    public SilentInitException(String message) {
        super(message);
    }

    public SilentInitException(String message, Throwable cause) {
        super(message, cause);
    }
}
