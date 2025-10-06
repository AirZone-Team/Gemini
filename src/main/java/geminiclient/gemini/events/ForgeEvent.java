package geminiclient.gemini.events;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.events.impl.ChatEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientChatEvent;

public class ForgeEvent {
    @SubscribeEvent
    public void onClientEvent(ClientChatEvent event) {
        ChatEvent chatEvent = new ChatEvent(event.getMessage());
        Gemini.eventManager.call(chatEvent);
        if (chatEvent.isCancelled()) {
            event.setCanceled(true);
        }
    }
}
