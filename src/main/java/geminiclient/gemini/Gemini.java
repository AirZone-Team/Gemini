package geminiclient.gemini;

import geminiclient.gemini.base.RotationManager;
import geminiclient.gemini.event.EventManager;
import geminiclient.gemini.base.FileSystem;
import geminiclient.gemini.commands.CommandManager;
import geminiclient.gemini.base.KeyBindHandler;
import geminiclient.gemini.modules.ModuleManager;
import geminiclient.gemini.modules.impl.visual.Arraylists;
import geminiclient.gemini.modules.HudDragManager;
import geminiclient.gemini.modules.impl.visual.notice.NotificationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class Gemini {
    public static String lastConfigName;

    public static KeyBindHandler keyBindHandler;
    public static ModuleManager moduleManager;
    public static EventManager eventManager;
    public static CommandManager commandManager;
    public static FileSystem fileSystem;
    public static NotificationManager notificationManager;
    public static RotationManager rotationManager;
    public static HudDragManager hudDragManager;
    public static void init() {
        notificationManager = new NotificationManager();
        eventManager = new EventManager();
        rotationManager = new RotationManager();
        eventManager.register(rotationManager);
        Logger.getLogger("Add");
        moduleManager = new ModuleManager();
        System.out.println("Add All modules");
        keyBindHandler = new KeyBindHandler();
        commandManager = new CommandManager();
        fileSystem = new FileSystem(moduleManager);
        hudDragManager = new HudDragManager();
        eventManager.register(hudDragManager);

        // Sync available TTF fonts into Arraylists' Font ListValue before config load
        List<String> ttfFonts = fileSystem.scanTtfFonts();
        Arraylists arraylists = moduleManager.getModule(Arraylists.class);
        if (arraylists != null && !ttfFonts.isEmpty()) {
            List<String> options = new ArrayList<>();
            options.add("Default");
            options.addAll(ttfFonts);
            arraylists.ttfFont.setList(options);
        }

        fileSystem.loadConfigName();
        fileSystem.loadConfig();
    }
}
