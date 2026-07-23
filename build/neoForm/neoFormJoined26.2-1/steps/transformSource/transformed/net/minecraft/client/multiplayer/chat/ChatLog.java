package net.minecraft.client.multiplayer.chat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class ChatLog {
    private final LoggedChatEvent[] buffer;
    private int nextId;

    public static Codec<ChatLog> codec(int capacity) {
        return Codec.list(LoggedChatEvent.CODEC)
            .comapFlatMap(
                loggedChatEvents -> {
                    int parsedSize = loggedChatEvents.size();
                    return parsedSize > capacity
                        ? DataResult.error(
                            () -> "Expected: a buffer of size less than or equal to " + capacity + " but: " + parsedSize + " is greater than " + capacity
                        )
                        : DataResult.success(new ChatLog(capacity, (List<LoggedChatEvent>)loggedChatEvents));
                },
                ChatLog::loggedChatEvents
            );
    }

    public ChatLog(int capacity) {
        this.buffer = new LoggedChatEvent[capacity];
    }

    private ChatLog(int capacity, List<LoggedChatEvent> buffer) {
        this.buffer = buffer.toArray(LoggedChatEvent[]::new);
        this.nextId = buffer.size();
    }

    private List<LoggedChatEvent> loggedChatEvents() {
        List<LoggedChatEvent> loggedChatEvents = new ArrayList<>(this.size());

        for (int i = this.start(); i <= this.end(); i++) {
            loggedChatEvents.add(this.lookup(i));
        }

        return loggedChatEvents;
    }

    public void push(LoggedChatEvent event) {
        this.buffer[this.index(this.nextId++)] = event;
    }

    public @Nullable LoggedChatEvent lookup(int id) {
        return id >= this.start() && id <= this.end() ? this.buffer[this.index(id)] : null;
    }

    private int index(int id) {
        return id % this.buffer.length;
    }

    public int start() {
        return Math.max(this.nextId - this.buffer.length, 0);
    }

    public int end() {
        return this.nextId - 1;
    }

    private int size() {
        return this.end() - this.start() + 1;
    }
}
