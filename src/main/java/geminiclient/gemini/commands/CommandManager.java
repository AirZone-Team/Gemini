package geminiclient.gemini.commands;

import geminiclient.gemini.commands.impl.Help;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.commands.impl.Bind;
import geminiclient.gemini.commands.impl.LoadConfig;
import geminiclient.gemini.commands.impl.SaveConfig;
import geminiclient.gemini.event.events.impl.ChatEvent;
import geminiclient.gemini.utils.ClientUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandManager {
    public static List<Command> getCommands() {
        return commands;
    }

    private static final List<Command> commands = new ArrayList<>();

    public CommandManager() {
        Gemini.eventManager.register(this);
        addCommands(
                new Bind(),
                new SaveConfig(),
                new LoadConfig(),
                new Help()
        );
    }

    private void addCommands(Command... commands) {
        CommandManager.commands.addAll(Arrays.asList(commands));
    }

    @SuppressWarnings("unused")
    @EventTarget
    private void chatEvent(ChatEvent event) {
        if (event.message.startsWith(".")) {
            event.setCancelled(true);
            for (Command command : commands) {
                command.onCommand(event.message);
            }
        }
    }
}
