package net.minecraft.client.resources.server;

import java.util.UUID;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface PackLoadFeedback {
    void reportUpdate(UUID id, PackLoadFeedback.Update result);

    void reportFinalResult(UUID id, PackLoadFeedback.FinalResult result);

    enum FinalResult {
        DECLINED,
        APPLIED,
        DISCARDED,
        DOWNLOAD_FAILED,
        ACTIVATION_FAILED;
    }

    enum Update {
        ACCEPTED,
        DOWNLOADED;
    }
}
