package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.function.Function;

public class AbstractArrowPickupFix extends DataFix {
    public AbstractArrowPickupFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Schema inputSchema = this.getInputSchema();
        return this.fixTypeEverywhereTyped("AbstractArrowPickupFix", inputSchema.getType(References.ENTITY), this::updateProjectiles);
    }

    private Typed<?> updateProjectiles(Typed<?> input) {
        input = this.updateEntity(input, "minecraft:arrow", AbstractArrowPickupFix::updatePickup);
        input = this.updateEntity(input, "minecraft:spectral_arrow", AbstractArrowPickupFix::updatePickup);
        return this.updateEntity(input, "minecraft:trident", AbstractArrowPickupFix::updatePickup);
    }

    private static Dynamic<?> updatePickup(Dynamic<?> tag) {
        if (tag.get("pickup").result().isPresent()) {
            return tag;
        }

        boolean fromPlayer = tag.get("player").asBoolean(true);
        return tag.set("pickup", tag.createByte((byte)(fromPlayer ? 1 : 0))).remove("player");
    }

    private Typed<?> updateEntity(Typed<?> input, String name, Function<Dynamic<?>, Dynamic<?>> function) {
        Type<?> oldType = this.getInputSchema().getChoiceType(References.ENTITY, name);
        Type<?> newType = this.getOutputSchema().getChoiceType(References.ENTITY, name);
        return input.updateTyped(DSL.namedChoice(name, oldType), newType, entity -> entity.update(DSL.remainderFinder(), function));
    }
}
