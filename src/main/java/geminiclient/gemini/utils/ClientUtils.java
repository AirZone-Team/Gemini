package geminiclient.gemini.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

public class ClientUtils {
    private static final String PREFIX = "§7[§b" + "Gemini" + "§7] ";

    public static void component(Component component) {
        ChatComponent chat = Minecraft.getInstance().gui.hud.getChat();
        chat.addClientSystemMessage(component);
    }

    public static void addChatMessage(String message) {
        addChatMessage(true, message);
    }

    public static void addChatMessage(boolean prefix, String message) {
        component(Component.nullToEmpty((prefix ? PREFIX : "") + message));
    }
}
