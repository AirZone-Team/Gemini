package geminiclient.gemini.utils;

import geminiclient.gemini.base.MinecraftInstance;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;

public class MovementUtils implements MinecraftInstance {
    public static boolean moving() {
        boolean idk = Input.EMPTY.forward() || Input.EMPTY.backward();
        return idk;
    }
}
