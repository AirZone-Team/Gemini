package geminiclient;

import geminiclient.gemini.customRenderer.glsl.CustomRendererRegistry;
import geminiclient.gemini.customRenderer.glsl.UiShaderWarmup;
import geminiclient.gemini.event.events.ForgeEvent;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.common.NeoForge;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod("gemini")
@SuppressWarnings("unused")
public class geminiLoader {
    public geminiLoader(IEventBus modBus, ModContainer container) {
        NeoForge.EVENT_BUS.register(new ForgeEvent());
        modBus.addListener(RegisterRenderPipelinesEvent.class, event ->
                CustomRendererRegistry.registerAll(event::registerPipeline));
        // Pre-compile ClickGui shaders/fonts during the loading screen (runs
        // after the vanilla ShaderManager) so the first GUI open is hitch-free.
        modBus.addListener(AddClientReloadListenersEvent.class, event ->
                event.addListener(Identifier.fromNamespaceAndPath("gemini", "ui_shader_warmup"),
                        UiShaderWarmup.createReloadListener()));
    }
}
