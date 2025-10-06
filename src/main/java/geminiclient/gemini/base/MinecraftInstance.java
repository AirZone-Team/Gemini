package geminiclient.gemini.base;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public interface MinecraftInstance {
    LocalPlayer player = Minecraft.getInstance().player;
    Minecraft mc = Minecraft.getInstance();
}
