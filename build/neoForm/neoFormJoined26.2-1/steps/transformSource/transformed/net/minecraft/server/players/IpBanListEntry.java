package net.minecraft.server.players;

import com.google.gson.JsonObject;
import java.util.Date;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public class IpBanListEntry extends BanListEntry<String> {
    public IpBanListEntry(String address) {
        this(address, null, null, null, null);
    }

    public IpBanListEntry(String address, @Nullable Date created, @Nullable String source, @Nullable Date expires, @Nullable String reason) {
        super(address, created, source, expires, reason);
    }

    @Override
    public Component getDisplayName() {
        return Component.literal(String.valueOf(this.getUser()));
    }

    public IpBanListEntry(JsonObject object) {
        super(createIpInfo(object), object);
    }

    private static String createIpInfo(JsonObject object) {
        return object.has("ip") ? object.get("ip").getAsString() : null;
    }

    @Override
    protected void serialize(JsonObject object) {
        if (this.getUser() != null) {
            object.addProperty("ip", this.getUser());
            super.serialize(object);
        }
    }
}
