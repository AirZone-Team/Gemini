package geminiclient.gemini.modules.impl.movement;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.lwjgl.glfw.GLFW;

public class InvMove extends Module {
    public InvMove() {
        super("InvMove", ModuleEnum.Movement);
    }

    private boolean hadInventoryOpened;
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (isInventoryOpened())
            allowMove();

        if (isInventoryOpened())
            allowMove();
        else {
            if(hadInventoryOpened) {
                allowMove();
                hadInventoryOpened = false;
            }
        }
    }

    private boolean isInventoryOpened() {
        return mc.screen instanceof InventoryScreen || mc.screen instanceof ContainerScreen;
    }

    private void allowMove() {
        KeyMapping[] keys = {
                mc.options.keyUp,
                mc.options.keyDown,
                mc.options.keyLeft,
                mc.options.keyRight,
                mc.options.keyJump
        };

        for(KeyMapping key : keys) {
            // 检查按键是否被按下
            boolean isPressed = GLFW.glfwGetKey(mc.getWindow().handle(), key.getDefaultKey().getValue()) == GLFW.GLFW_PRESS;

            // 模拟按键状态
            key.setDown(isPressed);
        }

        hadInventoryOpened = true;
    }
}
