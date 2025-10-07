package geminiclient;

import geminiclient.gemini.event.events.ForgeEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod("gemini")
@SuppressWarnings("unused")
public class geminiLoader {
    public geminiLoader(IEventBus modBus, ModContainer container) {
        NeoForge.EVENT_BUS.register(new ForgeEvent());
    }
}
