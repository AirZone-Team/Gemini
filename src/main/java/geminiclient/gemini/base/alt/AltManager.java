package geminiclient.gemini.base.alt;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.base.AltHelper;
import net.minecraft.client.User;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static geminiclient.gemini.base.MinecraftInstance.mc;

/**
 * 账号管理器：负责账号列表的读写持久化与「应用账号」操作。
 *
 * <p>持久化委托给 {@link geminiclient.gemini.base.FileSystem} 的
 * {@code loadAlts()} / {@code saveAlts(JSONArray)}，数据存放在
 * {@code gemini/configs/alts.json}。</p>
 *
 * <p>应用账号通过 {@link AltHelper} 把新的 {@link User} 及关联服务
 * 注入到 Minecraft 实例中（仅允许在主菜单等「未进入世界」的状态调用，
 * AltHelper 内部有防护检查）。</p>
 */
public final class AltManager {

    private static final Logger LOGGER = Logger.getLogger(AltManager.class.getName());

    private AltManager() {}

    // ========================
    // 持久化
    // ========================

    /** 从 alts.json 读取账号列表；损坏的条目会被跳过。 */
    public static List<AltAccount> load() {
        List<AltAccount> accounts = new ArrayList<>();
        JSONArray arr = Gemini.fileSystem.loadAlts();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            AltAccount acc = AltAccount.fromJson(o);
            if (acc != null) accounts.add(acc);
        }
        return accounts;
    }

    /** 把整个账号列表写回 alts.json。 */
    public static void save(List<AltAccount> accounts) {
        JSONArray arr = new JSONArray();
        for (AltAccount acc : accounts) {
            arr.put(acc.toJson());
        }
        Gemini.fileSystem.saveAlts(arr);
    }

    // ========================
    // 应用账号
    // ========================

    /**
     * 把指定账号设为当前会话账号：更新 active 标记、保存列表，
     * 并通过 AltHelper 注入 Minecraft。
     *
     * <p>注意：AltHelper 内部异步执行网络属性拉取并回主线程刷新引用，
     * 本方法立即返回。</p>
     *
     * @param account  目标账号
     * @param accounts 完整列表（用于清除其他账号的 active 标记）
     */
    public static void apply(AltAccount account, List<AltAccount> accounts) {
        for (AltAccount a : accounts) {
            a.setActive(a == account);
        }
        account.setActive(true);
        save(accounts);

        AltHelper.account(mc, "apply",
                account.getName(),
                account.getType().loginType(),
                account.getUuid(),
                account.getAccessToken());
        LOGGER.info("Applied account: " + account.getName()
                + " (" + account.getType().loginType() + ")");
    }

    // ========================
    // 状态查询
    // ========================

    /** 该账号是否就是 Minecraft 当前会话使用的账号。 */
    public static boolean isCurrent(AltAccount account) {
        User user = mc.getUser();
        return user != null && user.getProfileId() != null
                && user.getProfileId().equals(account.getUuid());
    }

    /** 当前会话的显示名（用于界面副标题）。 */
    public static String currentSessionName() {
        User user = mc.getUser();
        return user == null ? "未知" : user.getName();
    }

    /** 删除账号；若删除的是 active 账号则不改变当前会话，仅清除标记。 */
    public static void remove(AltAccount account, List<AltAccount> accounts) {
        accounts.remove(account);
        save(accounts);
    }

    /** 用新的认证结果更新账号（刷新令牌后名称/UUID/令牌可能变化）。 */
    public static void updateFromAuth(AltAccount account, MicrosoftAuthService.AuthResult result) {
        account.setName(result.name());
        account.setUuid(AltAccount.parseUuid(result.uuid()));
        account.setAccessToken(result.accessToken());
        if (!result.refreshToken().isEmpty()) {
            account.setRefreshToken(result.refreshToken());
        }
    }
}
