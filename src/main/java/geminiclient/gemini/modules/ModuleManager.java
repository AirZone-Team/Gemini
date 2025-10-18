package geminiclient.gemini.modules;

import geminiclient.gemini.modules.impl.combat.KillAura;
import geminiclient.gemini.modules.impl.combat.Velocity;
import geminiclient.gemini.modules.impl.movement.NoSlow;
import geminiclient.gemini.modules.impl.movement.Sprint;
import geminiclient.gemini.modules.impl.player.AutoTool;
import geminiclient.gemini.modules.impl.player.NoFall;
import geminiclient.gemini.modules.impl.player.Stealer;
import geminiclient.gemini.modules.impl.visual.Arraylists;
import geminiclient.gemini.modules.impl.visual.ClickGui;
import geminiclient.gemini.modules.impl.visual.FullLight;
import geminiclient.gemini.modules.impl.visual.Notification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModuleManager {
    private final List<Module> moduleList = new ArrayList<>();

    public ModuleManager() {
        addAll(
                new KillAura(),
                new Sprint(),
                new FullLight(),
                new ClickGui(),
                new Arraylists(),
                new Velocity(),
                new NoFall(),
                new Stealer(),
                new NoSlow(),
                new Notification(),
                new AutoTool());
    }

    @SuppressWarnings("unchecked")
    public <T> T getModule(final Class<T> clazz) {
        for (final Module module : moduleList) {
            if (module.getClass() == clazz) {
                return (T) module;
            }
        }
        return null;
    }

    private void addAll(Module... modules) {
        Collections.addAll(moduleList, modules);
    }

    public List<Module> getModules() {
        return moduleList;
    }
}
