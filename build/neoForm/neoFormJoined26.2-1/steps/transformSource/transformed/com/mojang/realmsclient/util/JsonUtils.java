package com.mojang.realmsclient.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.util.UndashedUuid;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class JsonUtils {
    public static <T> T getRequired(String key, JsonObject node, Function<JsonObject, T> parser) {
        JsonElement property = node.get(key);
        if (property == null || property.isJsonNull()) {
            throw new IllegalStateException("Missing required property: " + key);
        } else if (!property.isJsonObject()) {
            throw new IllegalStateException("Required property " + key + " was not a JsonObject as espected");
        } else {
            return parser.apply(property.getAsJsonObject());
        }
    }

    public static <T> @Nullable T getOptional(String key, JsonObject node, Function<JsonObject, T> parser) {
        JsonElement property = node.get(key);
        if (property == null || property.isJsonNull()) {
            return null;
        } else if (!property.isJsonObject()) {
            throw new IllegalStateException("Required property " + key + " was not a JsonObject as espected");
        } else {
            return parser.apply(property.getAsJsonObject());
        }
    }

    public static String getRequiredString(String key, JsonObject node) {
        String result = getStringOr(key, node, null);
        if (result == null) {
            throw new IllegalStateException("Missing required property: " + key);
        } else {
            return result;
        }
    }

    @Contract("_,_,!null->!null;_,_,null->_")
    public static @Nullable String getStringOr(String key, JsonObject node, @Nullable String defaultValue) {
        JsonElement element = node.get(key);
        if (element != null) {
            return element.isJsonNull() ? defaultValue : element.getAsString();
        } else {
            return defaultValue;
        }
    }

    @Contract("_,_,!null->!null;_,_,null->_")
    public static @Nullable UUID getUuidOr(String key, JsonObject node, @Nullable UUID defaultValue) {
        String uuidAsString = getStringOr(key, node, null);
        return uuidAsString == null ? defaultValue : UndashedUuid.fromStringLenient(uuidAsString);
    }

    public static int getIntOr(String key, JsonObject node, int defaultValue) {
        JsonElement element = node.get(key);
        if (element != null) {
            return element.isJsonNull() ? defaultValue : element.getAsInt();
        } else {
            return defaultValue;
        }
    }

    public static long getLongOr(String key, JsonObject node, long defaultValue) {
        JsonElement element = node.get(key);
        if (element != null) {
            return element.isJsonNull() ? defaultValue : element.getAsLong();
        } else {
            return defaultValue;
        }
    }

    public static boolean getBooleanOr(String key, JsonObject node, boolean defaultValue) {
        JsonElement element = node.get(key);
        if (element != null) {
            return element.isJsonNull() ? defaultValue : element.getAsBoolean();
        } else {
            return defaultValue;
        }
    }

    public static Instant getDateOr(String key, JsonObject node) {
        JsonElement element = node.get(key);
        return element != null ? Instant.ofEpochMilli(Long.parseLong(element.getAsString())) : Instant.EPOCH;
    }
}
