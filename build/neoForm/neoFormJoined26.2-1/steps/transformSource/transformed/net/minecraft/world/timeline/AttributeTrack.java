package net.minecraft.world.timeline;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.util.KeyframeTrack;
import net.minecraft.util.Util;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.modifier.AttributeModifier;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.clock.WorldClock;

public record AttributeTrack<Value, Argument>(AttributeModifier<Value, Argument> modifier, KeyframeTrack<Argument> argumentTrack) {
    public static <Value> Codec<AttributeTrack<Value, ?>> createCodec(EnvironmentAttribute<Value> attribute) {
        MapCodec<AttributeModifier<Value, ?>> modifierCodec = attribute.type().modifierCodec().optionalFieldOf("modifier", AttributeModifier.override());
        return modifierCodec.dispatch(
            AttributeTrack::modifier, Util.memoize(modifier -> createCodecWithModifier(attribute, (AttributeModifier<Value, ?>)modifier))
        );
    }

    private static <Value, Argument> MapCodec<AttributeTrack<Value, Argument>> createCodecWithModifier(
        EnvironmentAttribute<Value> attribute, AttributeModifier<Value, Argument> modifier
    ) {
        return KeyframeTrack.mapCodec(modifier.argumentCodec(attribute))
            .xmap(track -> new AttributeTrack<>(modifier, (KeyframeTrack<Argument>)track), AttributeTrack::argumentTrack);
    }

    public AttributeTrackSampler<Value, Argument> bakeSampler(
        EnvironmentAttribute<Value> attribute, Holder<WorldClock> clock, Optional<Integer> periodTicks, ClockManager clockManager
    ) {
        return new AttributeTrackSampler<>(clock, periodTicks, this.modifier, this.argumentTrack, this.modifier.argumentKeyframeLerp(attribute), clockManager);
    }

    public static DataResult<AttributeTrack<?, ?>> validatePeriod(AttributeTrack<?, ?> track, int periodTicks) {
        return KeyframeTrack.validatePeriod(track.argumentTrack(), periodTicks).map(ignored -> track);
    }
}
