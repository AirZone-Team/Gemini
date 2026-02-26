package geminiclient.gemini.commands.impl;

import geminiclient.gemini.commands.Command;
import geminiclient.gemini.commands.CommandManager;
import geminiclient.gemini.utils.ClientUtils;

public class Help extends Command {
    public Help() {
        super(".help");
    }
    @Override
    public void onCommand(String message) {
        String[] args = message.split(" ");
        if (args[0].equalsIgnoreCase(".help") || args[0].equalsIgnoreCase(".")) {
            ClientUtils.addChatMessage("You can use them");
            for (Command l : CommandManager.getCommands()) {
                ClientUtils.addChatMessage(l.getCommand());
            }
        }

    }
}
