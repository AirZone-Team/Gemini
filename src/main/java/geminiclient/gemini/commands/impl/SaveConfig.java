package geminiclient.gemini.commands.impl;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.commands.Command;
import geminiclient.gemini.utils.ClientUtils;

public class SaveConfig extends Command {
    public SaveConfig() {
        super(".save");
    }
    @Override
    public void onCommand(String message) {
        String[] args = message.split(" ");

        if (args[0].equalsIgnoreCase(".config")) {
            if (args.length == 2) {
                if (args[1].equalsIgnoreCase("save")) {
                    Gemini.fileSystem.saveConfig();
                    ClientUtils.addChatMessage(String.format("Saving Config name: %s",Gemini.lastConfigName));
                } else if (!args[1].equalsIgnoreCase("load")) {
                    ClientUtils.addChatMessage(".config save");
                }
            } else if (args.length == 3) {
                if (args[1].equalsIgnoreCase("save")) {
                    Gemini.fileSystem.saveConfig(args[2]);
                    ClientUtils.addChatMessage(String.format("Saving Config name: %s",args[2]));
                } else if (!args[1].equalsIgnoreCase("load")){
                    ClientUtils.addChatMessage(".config save <ConfigName>");
                }
            }
        }
    }
}
