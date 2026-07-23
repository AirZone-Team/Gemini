package net.minecraft.client.data.models.model;

import com.google.gson.JsonElement;
import java.util.function.Supplier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface ModelInstance extends Supplier<JsonElement> {
}
