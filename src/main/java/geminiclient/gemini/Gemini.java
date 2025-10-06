package geminiclient.gemini;

import com.cubk.event.EventManager;
import geminiclient.gemini.commands.CommandManager;
import geminiclient.gemini.events.ForgeEvent;
import geminiclient.gemini.base.KeyBindHandler;
import geminiclient.gemini.modules.ModuleManager;
import net.neoforged.neoforge.common.NeoForge;

public class Gemini {
    public static KeyBindHandler keyBindHandler;
    public static ModuleManager moduleManager;
    public static EventManager eventManager;
    public static ForgeEvent forgeEvent;
    public static CommandManager commandManager;
    public static void init() {
        eventManager = new EventManager();
        System.out.println("EventManager already");
        moduleManager = new ModuleManager();
        System.out.println("Add All modules");
        keyBindHandler = new KeyBindHandler();
        commandManager = new CommandManager();
        NeoForge.EVENT_BUS.register(forgeEvent = new ForgeEvent());
    }
}
