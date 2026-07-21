package geminiclient.gemini.modules;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.base.MinecraftInstance;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render2DEvent;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages drag-to-reposition for 2D HUD modules when the chat screen is open.
 * Runs at priority 50 so module render handlers (priority 10) register their
 * drag regions first, and this handler processes mouse input after.
 */
public class HudDragManager implements MinecraftInstance {

    private record DragRegion(int x, int y, int w, int h) {}

    private final Map<Module, DragRegion> dragRegions = new ConcurrentHashMap<>();
    private Module draggedModule = null;
    private int dragOffsetX, dragOffsetY;
    private boolean wasMouseDown = false;
    private boolean editing = false;

    /**
     * Called by HUD modules during their render to register the bounding box
     * for hit-testing this frame.
     */
    public void registerDragRegion(Module module, int x, int y, int w, int h) {
        dragRegions.put(module, new DragRegion(x, y, w, h));
    }

    /**
     * Returns true if the chat screen is open and drag-editor mode is active.
     * Modules use this to show outlines/placeholders and right-alignment.
     */
    public boolean isEditing() {
        return editing;
    }

    /**
     * Returns true if a module's hudX is past the horizontal center of the screen.
     * Modules use this to decide right-alignment / mirror rendering.
     */
    public boolean isOnRightSide(Module module) {
        return module.hudX > mc.getWindow().getGuiScaledWidth() / 2;
    }

    @SuppressWarnings("unused")
    @EventTarget(50)
    public void onRender2D(Render2DEvent event) {
        if (!(mc.gui.screen() instanceof ChatScreen)) {
            if (draggedModule != null) draggedModule = null;
            wasMouseDown = false;
            editing = false;
            dragRegions.clear();
            return;
        }

        editing = true;

        // Enabled draggable HUD modules always provide an editor outline and bounds.
        for (Module module : Gemini.moduleManager.getModules()) {
            if (module.enabled) {
                module.renderEditorOutline(event.guiGraphics());
            }
        }

        if (draggedModule != null && !draggedModule.enabled) {
            draggedModule = null;
        }

        long window = mc.getWindow().handle();
        boolean mouseDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        int mouseX = (int) mc.mouseHandler.getScaledXPos(mc.getWindow());
        int mouseY = (int) mc.mouseHandler.getScaledYPos(mc.getWindow());
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        if (mouseDown && !wasMouseDown) {
            for (Map.Entry<Module, DragRegion> entry : dragRegions.entrySet()) {
                if (!entry.getKey().enabled) continue;
                DragRegion r = entry.getValue();
                if (mouseX >= r.x && mouseX <= r.x + r.w
                        && mouseY >= r.y && mouseY <= r.y + r.h) {
                    draggedModule = entry.getKey();
                    dragOffsetX = mouseX - draggedModule.hudX;
                    dragOffsetY = mouseY - draggedModule.hudY;
                    break;
                }
            }
        }

        if (draggedModule != null && mouseDown) {
            draggedModule.hudX = Math.max(0, Math.min(screenW - 10, mouseX - dragOffsetX));
            draggedModule.hudY = Math.max(0, Math.min(screenH - 10, mouseY - dragOffsetY));
        }

        if (!mouseDown && wasMouseDown && draggedModule != null) {
            draggedModule = null;
            Gemini.fileSystem.saveConfig();
        }

        wasMouseDown = mouseDown;
        dragRegions.clear();
    }
}
