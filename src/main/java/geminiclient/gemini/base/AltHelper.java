package geminiclient.gemini.base;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import geminiclient.mixin.access.AccessMinecraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.server.Services;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class AltHelper {
    public static void account(Minecraft minecraft, String action, String accountName, String loginType, UUID uuid, String token) {
        // 1. 安全检查：确保玩家不在游戏世界中（避免在联机/单机时切换导致崩溃）
        if (minecraft.player != null || minecraft.level != null || minecraft.getConnection() != null ||
                minecraft.getCameraEntity() != null || minecraft.gameMode != null || minecraft.isSingleplayer() || !action.equals("apply")) {
            return;
        }

        // 2. 异步处理：因为身份验证和属性获取涉及网络请求，会阻塞主线程
        CompletableFuture.runAsync(() -> {
            boolean online = loginType.equals("Microsoft");

            // 3. 创建 Minecraft 用户实例 (User Object)
            User user = new User(accountName, uuid, token, Optional.empty(), Optional.empty());

            // 4. 初始化验证服务与各种关联系统
            YggdrasilAuthenticationService service = online ? new YggdrasilAuthenticationService(minecraft.getProxy()) : YggdrasilAuthenticationService.createOffline(minecraft.getProxy());
            Services services = Services.create(service, minecraft.gameDirectory);

            // 获取用户 API 服务及属性
            UserApiService apiService = online ? service.createUserApiService(token) : UserApiService.OFFLINE;
            UserApiService.UserProperties properties;
            try {
                properties = apiService.fetchProperties();
            } catch (Throwable ignored) {
                properties = UserApiService.OFFLINE_PROPERTIES;
            }

            // 初始化社交、遥测、密钥对和举报上下文
            PlayerSocialManager social = new PlayerSocialManager(minecraft, apiService);
            ClientTelemetryManager telemetry = new ClientTelemetryManager(minecraft, apiService, user);
            ProfileKeyPairManager keyPair = ProfileKeyPairManager.create(apiService, user, minecraft.gameDirectory.toPath());
            ReportingContext reporting = ReportingContext.create(ReportEnvironment.local(), apiService);

            // 5. 回到主线程刷新 Minecraft 内部引用
            UserApiService.UserProperties finalProperties = properties;
            minecraft.execute(() -> {
                AccessMinecraft accessor = ((AccessMinecraft) minecraft);

                // 使用 Mixin 访问器 (Accessor) 将新实例注入到 Minecraft 类中
                accessor.services(services);
                accessor.user(user);
                accessor.profileFuture(CompletableFuture.completedFuture(online ? services.sessionService().fetchProfile(uuid, true) : null));
                accessor.userApiService(apiService);
                accessor.userPropertiesFuture(CompletableFuture.completedFuture(finalProperties));
                accessor.playerSocialManager(social);
                accessor.telemetryManager(telemetry);
                accessor.profileKeyPairManager(keyPair);
                accessor.reportingContext(reporting);
            });
        });
    }
}
