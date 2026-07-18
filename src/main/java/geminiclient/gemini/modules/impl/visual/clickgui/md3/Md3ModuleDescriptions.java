package geminiclient.gemini.modules.impl.visual.clickgui.md3;

import java.util.Map;

/**
 * Optional one-line module descriptions shown in the MD3 module rows.
 * Modules without an entry simply omit the second supporting line.
 */
public final class Md3ModuleDescriptions {

    private Md3ModuleDescriptions() {
    }

    private static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
            Map.entry("KillAura", "Automatically attacks nearby entities"),
            Map.entry("Velocity", "Modifies knockback taken"),
            Map.entry("AutoThrow", "Throws projectiles automatically"),
            Map.entry("ClickGui", "Opens this configuration screen"),
            Map.entry("TargetDisplay", "Shows information about your target"),
            Map.entry("Notification", "Shows module toggle notifications"),
            Map.entry("SweepingAttackVFX", "Custom sweep attack visual effects")
    );

    public static String get(String moduleName) {
        return DESCRIPTIONS.getOrDefault(moduleName, "");
    }
}
