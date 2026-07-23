package com.mojang.realmsclient.util.task;

import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.WorldTemplate;
import com.mojang.realmsclient.exception.RealmsServiceException;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ResettingTemplateWorldTask extends ResettingWorldTask {
    private final WorldTemplate template;

    public ResettingTemplateWorldTask(WorldTemplate template, long serverId, Component title, Runnable callback) {
        super(serverId, title, callback);
        this.template = template;
    }

    @Override
    protected void sendResetRequest(RealmsClient client, long serverId) throws RealmsServiceException {
        client.resetWorldWithTemplate(serverId, this.template.id());
    }
}
