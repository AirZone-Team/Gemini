package geminiclient.gemini.modules;

import geminiclient.gemini.modules.impl.combat.*;
import geminiclient.gemini.modules.impl.movement.*;
import geminiclient.gemini.modules.impl.player.*;
import geminiclient.gemini.modules.impl.visual.*;

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
                new AutoTool(),
                new NoWeb(),
                new Glow(),
                new InvManager(),
                new EffectDisplay(),
                new InvMove(),
                new NoJumpDelay());
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
