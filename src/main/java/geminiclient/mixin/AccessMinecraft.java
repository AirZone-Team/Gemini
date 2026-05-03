package geminiclient.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.server.Services;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public interface AccessMinecraft {
    @Accessor("services")
    @Mutable
    void services(Services services);

    @Accessor("user")
    @Mutable
    void user(User user);

    @Accessor("profileFuture")
    @Mutable
    void profileFuture(CompletableFuture<ProfileResult> future);

    /**
     * Sets the user API service.
     *
     * @param service New user API service
     */
    @Accessor("userApiService")
    @Mutable
    void userApiService(UserApiService service);

    /**
     * Sets the user properties future.
     *
     * @param future New user properties future
     */
    @Accessor("userPropertiesFuture")
    @Mutable
    void userPropertiesFuture(CompletableFuture<UserApiService.UserProperties> future);

    @Accessor("playerSocialManager")
    @Mutable
    void playerSocialManager(PlayerSocialManager manager);

    @Accessor("telemetryManager")
    @Mutable
    void telemetryManager(ClientTelemetryManager manager);

    @Accessor("profileKeyPairManager")
    @Mutable
    void profileKeyPairManager(ProfileKeyPairManager manager);

    @Accessor("reportingContext")
    @Mutable
    void reportingContext(ReportingContext context);
}
