package geminiclient.gemini.commands.impl;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.commands.Command;
import geminiclient.gemini.utils.ClientUtils;

public class LoadConfig extends Command {
    @Override
    public void onCommand(String message) {
        String[] args = message.split(" ");

        if (args[0].equalsIgnoreCase(".config")) {
            if (args.length == 3) {
                if (args[1].equalsIgnoreCase("load")) {
                    Gemini.fileSystem.loadConfig(args[2]);
                    ClientUtils.addChatMessage(String.format("Loading Config name: %s",args[2]));
                }
            }
        }
    }
}
