package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;

public class EntityGoatMissingStateFix extends NamedEntityFix {
    public EntityGoatMissingStateFix(Schema outputSchema) {
        super(outputSchema, false, "EntityGoatMissingStateFix", References.ENTITY, "minecraft:goat");
    }

    @Override
    protected Typed<?> fix(Typed<?> entity) {
        return entity.update(DSL.remainderFinder(), tag -> tag.set("HasLeftHorn", tag.createBoolean(true)).set("HasRightHorn", tag.createBoolean(true)));
    }
}
