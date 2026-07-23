package net.minecraft.server.players;

import com.google.gson.JsonObject;

public class UserWhiteListEntry extends StoredUserEntry<NameAndId> {
    public UserWhiteListEntry(NameAndId user) {
        super(user);
    }

    public UserWhiteListEntry(JsonObject object) {
        super(NameAndId.fromJson(object));
    }

    @Override
    protected void serialize(JsonObject object) {
        if (this.getUser() != null) {
            this.getUser().appendTo(object);
        }
    }
}
