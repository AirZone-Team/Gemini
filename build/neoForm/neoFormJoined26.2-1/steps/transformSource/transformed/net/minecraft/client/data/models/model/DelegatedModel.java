package net.minecraft.client.data.models.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DelegatedModel implements ModelInstance {
    private final Identifier parent;

    public DelegatedModel(Identifier parent) {
        this.parent = parent;
    }

    public JsonElement get() {
        JsonObject result = new JsonObject();
        result.addProperty("parent", this.parent.toString());
        return result;
    }
}
