package geminiclient.gemini.commands;

import com.cubk.event.annotations.EventTarget;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.commands.impl.Bind;
import geminiclient.gemini.events.impl.ChatEvent;
import geminiclient.gemini.modules.Module;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandManager {
    private final List<Command> commands = new ArrayList<>();

    public CommandManager() {
        Gemini.eventManager.register(this);
        addCommands(
                new Bind()
        );
    }

    private void addCommands(Command... commands) {
        this.commands.addAll(Arrays.asList(commands));
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
