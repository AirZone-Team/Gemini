package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.base.JavaToCSharpIPC;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;

public class ExpandClickGui extends Module {
    public ExpandClickGui() {
        super("ExpandClickGui", ModuleEnum.Visual);
    }

    @Override
    public void onEnabled() {
        JavaToCSharpIPC.startExe();
        JavaToCSharpIPC.toCSAccount();
        JavaToCSharpIPC.toCS(Gemini.moduleManager);
        JavaToCSharpIPC.startReceive();
    }
    @Override
    public void onDisabled() {
        JavaToCSharpIPC.shutdown();
    }
}
