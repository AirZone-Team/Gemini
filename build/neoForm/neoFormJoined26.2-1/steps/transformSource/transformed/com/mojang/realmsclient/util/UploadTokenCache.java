package com.mojang.realmsclient.util;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class UploadTokenCache {
    private static final Long2ObjectMap<String> TOKEN_CACHE = new Long2ObjectOpenHashMap<>();

    public static String get(long realmId) {
        return TOKEN_CACHE.get(realmId);
    }

    public static void invalidate(long realmId) {
        TOKEN_CACHE.remove(realmId);
    }

    public static void put(long realmId, @Nullable String token) {
        TOKEN_CACHE.put(realmId, token);
    }
}
