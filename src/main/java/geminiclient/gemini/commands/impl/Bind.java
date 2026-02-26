package geminiclient.gemini.commands.impl;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.commands.Command;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.utils.ClientUtils;

import java.awt.event.KeyEvent;

public class Bind extends Command {
    public Bind() {
        super(".bind");
    }
    @Override
    public void onCommand(String message) {
        String[] args = message.split(" ");
        if (!args[0].equalsIgnoreCase(".bind"))
            return;

        if (args.length != 3) {
            ClientUtils.addChatMessage(".bind <ModuleName> <Key>");
            return;
        }

        for (Module module : Gemini.moduleManager.getModules()) {
            if (args[1].equalsIgnoreCase(module.getName())) {
                if (args[2].length() == 1) {
                    int key = getKeyCodeFromCharString(args[2]);
                    if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_UNDEFINED) {
                        ClientUtils.addChatMessage("Error");
                        return;
                    }
                    module.key = key;
                    Gemini.fileSystem.saveConfig();
                    ClientUtils.addChatMessage(module.getName() + "'s key is " + key);
                } else {
                    ClientUtils.addChatMessage("The input value can only be one");
                }
                return;
            }
        }
    }

    public static int getKeyCodeFromCharString(String keyString) {
        if (keyString == null || keyString.length() != 1) {
            return KeyEvent.VK_UNDEFINED;
        }

        char keyChar = keyString.toUpperCase().charAt(0);

        if (keyChar >= 'A' && keyChar <= 'Z' || keyChar >= '0' && keyChar <= '9') {
            return keyChar;
        }

        if (keyChar == ' ') {
            return KeyEvent.VK_SPACE;
        }

        return KeyEvent.VK_UNDEFINED;
    }
}
