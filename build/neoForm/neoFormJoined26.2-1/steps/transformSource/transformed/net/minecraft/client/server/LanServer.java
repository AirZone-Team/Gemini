package net.minecraft.client.server;

import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LanServer {
    private final String motd;
    private final String address;
    private long pingTime;

    public LanServer(String motd, String address) {
        this.motd = motd;
        this.address = address;
        this.pingTime = Util.getMillis();
    }

    public String getMotd() {
        return this.motd;
    }

    public String getAddress() {
        return this.address;
    }

    public void updatePingTime() {
        this.pingTime = Util.getMillis();
    }
}
