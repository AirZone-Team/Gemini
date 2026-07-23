package com.mojang.realmsclient.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record PingResult(@SerializedName("pingResults") List<RegionPingResult> pingResults, @SerializedName("worldIds") List<Long> realmIds)
    implements ReflectionBasedSerialization {
}
