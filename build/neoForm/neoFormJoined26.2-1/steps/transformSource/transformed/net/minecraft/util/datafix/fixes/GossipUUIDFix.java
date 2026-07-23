package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class GossipUUIDFix extends NamedEntityFix {
    public GossipUUIDFix(Schema outputSchema, String entityName) {
        super(outputSchema, false, "Gossip for for " + entityName, References.ENTITY, entityName);
    }

    @Override
    protected Typed<?> fix(Typed<?> entity) {
        return entity.update(
            DSL.remainderFinder(),
            tag -> tag.update(
                "Gossips",
                gossips -> DataFixUtils.orElse(
                    gossips.asStreamOpt()
                        .result()
                        .map(s -> s.map(gossip -> AbstractUUIDFix.replaceUUIDLeastMost((Dynamic<?>)gossip, "Target", "Target").orElse((Dynamic<?>)gossip)))
                        .map(gossips::createList),
                    gossips
                )
            )
        );
    }
}
