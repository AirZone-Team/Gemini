package net.minecraft.network;

public enum ConnectionProtocol {
    HANDSHAKING("handshake"),
    PLAY("play"),
    STATUS("status"),
    LOGIN("login"),
    CONFIGURATION("configuration");

    private final String id;

    ConnectionProtocol(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public boolean isPlay() {
        return this == PLAY;
    }
    public boolean isConfiguration() {
        return this == CONFIGURATION;
    }
}
