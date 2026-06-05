package geminiclient.gemini.modules;

import geminiclient.gemini.modules.impl.combat.*;
import geminiclient.gemini.modules.impl.movement.*;
import geminiclient.gemini.modules.impl.player.*;
import geminiclient.gemini.modules.impl.visual.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
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
                new NoJumpDelay(),
                new Crit(),
                new Speed(),
                new ExpandClickGui(),
                new SuperKB(),
                new ESP(),
                new ItemPhysical(),
                new Scaffold(),
                new MovementFix(),
                new KeepSprint(),
                new BackTrack()
        );
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

    /**
     * 获取指定包下的所有类（递归扫描子包）
     */
    private List<Class<?>> getClassesInPackage(String packageName) throws IOException, ClassNotFoundException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = classLoader.getResources(path);
        List<Class<?>> classes = new ArrayList<>();
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            File directory = new File(resource.getFile());
            if (directory.exists()) {
                classes.addAll(findClasses(directory, packageName));
            }
        }
        return classes;
    }

    private List<Class<?>> findClasses(File directory, String packageName) throws ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        File[] files = directory.listFiles();
        if (files == null) return classes;
        for (File file : files) {
            if (file.isDirectory()) {
                classes.addAll(findClasses(file, packageName + "." + file.getName()));
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                classes.add(Class.forName(className));
            }
        }
        return classes;
    }
}
