package geminiclient.gemini;

import geminiclient.gemini.event.EventManager;
import geminiclient.gemini.base.FileSystem;
import geminiclient.gemini.commands.CommandManager;
import geminiclient.gemini.base.KeyBindHandler;
import geminiclient.gemini.modules.ModuleManager;

import java.util.logging.Logger;

public class Gemini {
    public static String lastConfigName;

    public static KeyBindHandler keyBindHandler;
    public static ModuleManager moduleManager;
    public static EventManager eventManager;
    public static CommandManager commandManager;
    public static FileSystem fileSystem;
    public static void init() {
        eventManager = new EventManager();
        Logger.getLogger("Add");
        moduleManager = new ModuleManager();
        System.out.println("Add All modules");
        keyBindHandler = new KeyBindHandler();
        commandManager = new CommandManager();
        fileSystem = new FileSystem(moduleManager);
        fileSystem.loadConfigName();
        fileSystem.loadConfig();
    }
}
