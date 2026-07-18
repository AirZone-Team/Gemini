package geminiclient.gemini.base.alt;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 账号数据模型。
 *
 * <p>字段与 {@code gemini/configs/alts.json} 中的 JSON 对象一一对应：
 * 类型（microsoft / offline）、用户名、UUID、accessToken、refreshToken，
 * 以及「是否当前激活」标记（用于重启后仍能显示上次使用的账号）。</p>
 */
public final class AltAccount {

    /** 账号类型。{@code loginType} 与 {@link geminiclient.gemini.base.AltHelper} 的约定一致。 */
    public enum Type {
        MICROSOFT("microsoft", "Microsoft"),
        OFFLINE("offline", "Offline");

        private final String id;
        private final String loginType;

        Type(String id, String loginType) {
            this.id = id;
            this.loginType = loginType;
        }

        /** JSON 中的序列化标识。 */
        public String id() {
            return id;
        }

        /** 传给 AltHelper 的登录类型（"Microsoft" = 在线验证，其余 = 离线）。 */
        public String loginType() {
            return loginType;
        }

        public static Type fromId(String id) {
            for (Type t : values()) {
                if (t.id.equalsIgnoreCase(id)) return t;
            }
            return OFFLINE;
        }
    }

    private final Type type;
    private String name;
    private UUID uuid;
    private String accessToken;
    private String refreshToken;
    /** 上次「应用」操作使用的账号（持久化，用于跨会话高亮）。 */
    private boolean active;

    public AltAccount(Type type, String name, UUID uuid, String accessToken, String refreshToken) {
        this.type = type;
        this.name = name;
        this.uuid = uuid;
        this.accessToken = accessToken == null ? "" : accessToken;
        this.refreshToken = refreshToken == null ? "" : refreshToken;
    }

    /**
     * 创建离线账号。UUID 由 {@code OfflinePlayer:<name>} 派生 —— 与原版
     * 离线模式服务端为玩家分配的 UUID 算法一致，因此对同一用户名是稳定的。
     */
    public static AltAccount offline(String name) {
        UUID uuid = UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        return new AltAccount(Type.OFFLINE, name, uuid, "", "");
    }

    // ========================
    // Accessors
    // ========================

    public Type getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken == null ? "" : accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken == null ? "" : refreshToken;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /** UI 显示用类型标签。 */
    public String typeLabel() {
        return type == Type.MICROSOFT ? "Microsoft" : "离线";
    }

    /** UI 显示用短 UUID（前 8 位，无横线形式的前 8 个字符）。 */
    public String shortUuid() {
        String s = uuid.toString().replace("-", "");
        return s.length() <= 8 ? s : s.substring(0, 8);
    }

    // ========================
    // JSON 序列化
    // ========================

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        o.put("type", type.id());
        o.put("name", name);
        o.put("uuid", uuid.toString());
        o.put("accessToken", accessToken);
        o.put("refreshToken", refreshToken);
        o.put("active", active);
        return o;
    }

    /**
     * 从 JSON 反序列化；字段缺失或格式错误时返回 {@code null}（跳过该条）。
     */
    public static AltAccount fromJson(JSONObject o) {
        try {
            Type type = Type.fromId(o.optString("type", "offline"));
            String name = o.getString("name");
            UUID uuid = parseUuid(o.getString("uuid"));
            AltAccount acc = new AltAccount(type, name, uuid,
                    o.optString("accessToken", ""),
                    o.optString("refreshToken", ""));
            acc.active = o.optBoolean("active", false);
            return acc;
        } catch (Exception e) {
            return null;
        }
    }

    /** 兼容无横线（32 位十六进制）与标准带横线两种 UUID 写法。 */
    public static UUID parseUuid(String raw) {
        String s = raw.trim();
        if (!s.contains("-") && s.length() == 32) {
            s = s.substring(0, 8) + "-" + s.substring(8, 12) + "-"
                    + s.substring(12, 16) + "-" + s.substring(16, 20) + "-"
                    + s.substring(20);
        }
        return UUID.fromString(s);
    }
}
