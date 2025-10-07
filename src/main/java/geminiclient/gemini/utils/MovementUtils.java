package geminiclient.gemini.utils;

import geminiclient.gemini.base.MinecraftInstance;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;

public class MovementUtils implements MinecraftInstance {
    public static boolean moving() {
        if (mc.player == null)
            return false;

        return mc.player.input.getMoveVector().x != 0.0 || mc.player.input.getMoveVector().y != 0.0f;
    }
}
