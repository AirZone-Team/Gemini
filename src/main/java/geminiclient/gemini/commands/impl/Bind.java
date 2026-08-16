package geminiclient.gemini.commands.impl;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.commands.Command;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.utils.ClientUtils;
import geminiclient.gemini.utils.KeyUtils;

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
                // 支持命名键：LSHIFT/RSHIFT、方向键、F1-F24、MOUSE1-8、NONE 等
                int key = KeyUtils.getKeyCode(args[2]);
                if (key == KeyUtils.UNDEFINED) {
                    ClientUtils.addChatMessage("Error");
                    return;
                }
                module.key = key;
                Gemini.fileSystem.saveConfig();
                ClientUtils.addChatMessage(module.getName() + "'s key is " + KeyUtils.getKeyName(key));
                return;
            }
        }
    }
}
