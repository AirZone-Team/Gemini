package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public abstract class AbstractUUIDFix extends DataFix {
    protected final TypeReference typeReference;

    public AbstractUUIDFix(Schema outputSchema, TypeReference typeReference) {
        super(outputSchema, false);
        this.typeReference = typeReference;
    }

    protected Typed<?> updateNamedChoice(Typed<?> input, String name, Function<Dynamic<?>, Dynamic<?>> function) {
        Type<?> oldType = this.getInputSchema().getChoiceType(this.typeReference, name);
        Type<?> newType = this.getOutputSchema().getChoiceType(this.typeReference, name);
        return input.updateTyped(DSL.namedChoice(name, oldType), newType, typedTag -> typedTag.update(DSL.remainderFinder(), function));
    }

    protected static Optional<Dynamic<?>> replaceUUIDString(Dynamic<?> tag, String oldKey, String newKey) {
        return createUUIDFromString(tag, oldKey).map(uuidTag -> tag.remove(oldKey).set(newKey, (Dynamic<?>)uuidTag));
    }

    protected static Optional<Dynamic<?>> replaceUUIDMLTag(Dynamic<?> tag, String oldKey, String newKey) {
        return tag.get(oldKey).result().flatMap(AbstractUUIDFix::createUUIDFromML).map(uuidTag -> tag.remove(oldKey).set(newKey, (Dynamic<?>)uuidTag));
    }

    protected static Optional<Dynamic<?>> replaceUUIDLeastMost(Dynamic<?> tag, String oldKey, String newKey) {
        String mostKey = oldKey + "Most";
        String leastKey = oldKey + "Least";
        return createUUIDFromLongs(tag, mostKey, leastKey).map(uuidTag -> tag.remove(mostKey).remove(leastKey).set(newKey, (Dynamic<?>)uuidTag));
    }

    protected static Optional<Dynamic<?>> createUUIDFromString(Dynamic<?> tag, String oldKey) {
        return tag.get(oldKey).result().flatMap(uuidStringTag -> {
            String uuidString = uuidStringTag.asString(null);
            if (uuidString != null) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    return createUUIDTag(tag, uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
                } catch (IllegalArgumentException var4) {
                }
            }

            return Optional.empty();
        });
    }

    protected static Optional<Dynamic<?>> createUUIDFromML(Dynamic<?> tag) {
        return createUUIDFromLongs(tag, "M", "L");
    }

    protected static Optional<Dynamic<?>> createUUIDFromLongs(Dynamic<?> tag, String mostKey, String leastKey) {
        long mostSignificantBits = tag.get(mostKey).asLong(0L);
        long leastSignificantBits = tag.get(leastKey).asLong(0L);
        return mostSignificantBits != 0L && leastSignificantBits != 0L ? createUUIDTag(tag, mostSignificantBits, leastSignificantBits) : Optional.empty();
    }

    protected static Optional<Dynamic<?>> createUUIDTag(Dynamic<?> tag, long mostSignificantBits, long leastSignificantBits) {
        return Optional.of(
            tag.createIntList(
                Arrays.stream(
                    new int[]{(int)(mostSignificantBits >> 32), (int)mostSignificantBits, (int)(leastSignificantBits >> 32), (int)leastSignificantBits}
                )
            )
        );
    }
}
