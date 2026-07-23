package net.minecraft.server.packs;

import net.minecraft.util.StringRepresentable;

public enum PackType implements StringRepresentable {
    CLIENT_RESOURCES("assets"),
    SERVER_DATA("data");

    private final String directory;

    PackType(String directory) {
        this.directory = directory;
    }

    public String getDirectory() {
        return this.directory;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
