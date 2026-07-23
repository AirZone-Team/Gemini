package com.mojang.realmsclient.dto;

import com.google.gson.annotations.SerializedName;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class ValueObject {
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");

        for (Field f : this.getClass().getFields()) {
            if (!isStatic(f)) {
                try {
                    sb.append(getName(f)).append("=").append(f.get(this)).append(" ");
                } catch (IllegalAccessException var7) {
                }
            }
        }

        sb.deleteCharAt(sb.length() - 1);
        sb.append('}');
        return sb.toString();
    }

    private static String getName(Field f) {
        SerializedName override = f.getAnnotation(SerializedName.class);
        return override != null ? override.value() : f.getName();
    }

    private static boolean isStatic(Field f) {
        return Modifier.isStatic(f.getModifiers());
    }
}
