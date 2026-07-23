package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.function.Function;
import java.util.stream.Stream;

public class PoiTypeRenameFix extends AbstractPoiSectionFix {
    private final Function<String, String> renamer;

    public PoiTypeRenameFix(Schema outputSchema, String name, Function<String, String> renamer) {
        super(outputSchema, name);
        this.renamer = renamer;
    }

    @Override
    protected <T> Stream<Dynamic<T>> processRecords(Stream<Dynamic<T>> stream) {
        return stream.map(
            element -> element.update("type", type -> DataFixUtils.orElse(type.asString().map(this.renamer).map(type::createString).result(), type))
        );
    }
}
