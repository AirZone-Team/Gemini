package geminiclient.gemini.modules;

import geminiclient.gemini.modules.impl.combat.KillAura;
import geminiclient.gemini.modules.impl.movement.Sprint;
import geminiclient.gemini.modules.impl.visual.Arraylists;
import geminiclient.gemini.modules.impl.visual.ClickGui;
import geminiclient.gemini.modules.impl.visual.FullLight;
import geminiclient.gemini.modules.impl.visual.ItemPhysic;

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
                new ItemPhysic()
        );
    }

    private void addAll(Module... modules) {
        Collections.addAll(moduleList, modules);
    }

    public List<Module> getModules() {
        return moduleList;
    }
}
