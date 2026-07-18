package geminiclient.gemini.base.alt;

import net.minecraft.util.Util;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Microsoft OAuth 认证服务（Java 移植版）。
 *
 * <p>完整实现 code → MS Token → XBL → XSTS → Minecraft Token → 档案 的
 * 登录链，参考 {@code clickgui/lib/services/microsoft_auth_service.dart}
 * 与 https://wiki.vg/Microsoft_Authentication_Scheme 。</p>
 *
 * <p>支持两种模式：</p>
 * <ul>
 *   <li>自动模式 —— 本地 HTTP 回调服务器（127.0.0.1:29116）+ 自动打开浏览器；</li>
 *   <li>手动模式 —— 用户自行打开授权链接，登录后把浏览器地址栏中的
 *       重定向 URL（或纯 code）粘贴回来。</li>
 * </ul>
 *
 * <p>所有流程都在虚拟线程上执行，通过 {@link CompletableFuture} 返回；
 * UI 侧用 {@link StatusListener} 接收中文进度提示。</p>
 */
public final class MicrosoftAuthService {

    // ========================
    // 常量
    // ========================

    /** Azure 应用注册的 Client ID（与参考实现一致）。 */
    private static final String CLIENT_ID = "288ec5dd-6736-4d4b-9b96-30e083a8cad2";
    /** 本地重定向 URI（自动模式下监听此地址）。 */
    private static final String REDIRECT_URI = "http://localhost:29116/authentication-response";
    private static final int CALLBACK_PORT = 29116;
    private static final String CALLBACK_PATH = "/authentication-response";
    private static final Duration LOGIN_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(25);
    private static final String USER_AGENT = "MinecraftLauncher/2.0.0";

    private static final Pattern CODE_PATTERN = Pattern.compile("code=([^&]+)");
    private static final Pattern ERROR_PATTERN = Pattern.compile("error=([^&]+)");
    private static final Pattern ERROR_DESC_PATTERN = Pattern.compile("error_description=([^&]+)");

    private MicrosoftAuthService() {}

    // ========================
    // 结果 / 回调 / 异常
    // ========================

    /** 登录成功结果：档案名、带横线 UUID、Minecraft 令牌、微软刷新令牌。 */
    public record AuthResult(String name, String uuid, String accessToken, String refreshToken) {}

    /** 进度回调（在登录线程上触发，参数为中文状态文本）。 */
    @FunctionalInterface
    public interface StatusListener {
        void onStatus(String status);
    }

    /** 登录流程专用异常 —— message 均为可直接展示的中文提示。 */
    public static final class AuthException extends Exception {
        public AuthException(String message) {
            super(message);
        }

        public AuthException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ========================
    // 取消支持
    // ========================

    private static volatile CompletableFuture<AuthResult> activeFuture;
    private static volatile ServerSocket activeServer;

    /**
     * 取消当前正在进行的登录（若有）。等待浏览器回调的线程会随
     * 服务器关闭而退出；进行中的 HTTP 请求完成后其结果会被忽略。
     */
    public static void cancelActive() {
        CompletableFuture<AuthResult> f = activeFuture;
        if (f != null && !f.isDone()) {
            f.completeExceptionally(new AuthException("已取消登录"));
        }
        closeQuietly(activeServer);
    }

    public static boolean isLoginInProgress() {
        CompletableFuture<AuthResult> f = activeFuture;
        return f != null && !f.isDone();
    }

    // ========================
    // 公开 API
    // ========================

    /** Microsoft OAuth 授权 URL（手动模式下也可给用户打开）。 */
    public static String authorizationUrl() {
        return "https://login.live.com/oauth20_authorize.srf"
                + "?client_id=" + CLIENT_ID
                + "&response_type=code"
                + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode("XboxLive.signin offline_access", StandardCharsets.UTF_8);
    }

    /** 在默认浏览器中打开授权页面（手动模式使用）。 */
    public static void openAuthorizationPage() throws AuthException {
        openBrowser(authorizationUrl());
    }

    /**
     * 自动模式：启动本地回调服务器、打开浏览器、等待回调并完成全部令牌交换。
     */
    public static CompletableFuture<AuthResult> loginAuto(StatusListener listener) {
        if (isLoginInProgress()) {
            return failedFuture(new AuthException("已有登录流程进行中，请先完成或取消它"));
        }

        CompletableFuture<AuthResult> future = new CompletableFuture<>();
        activeFuture = future;

        Thread.startVirtualThread(() -> {
            ServerSocket server = null;
            try {
                listener.onStatus("正在启动本地回调服务器…");
                try {
                    server = new ServerSocket();
                    server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), CALLBACK_PORT), 8);
                    server.setSoTimeout(1000);
                } catch (IOException e) {
                    throw new AuthException("无法启动本地回调服务器（端口 " + CALLBACK_PORT
                            + " 被占用）。\n请关闭占用该端口的程序，或改用「手动粘贴链接」模式。", e);
                }
                activeServer = server;

                listener.onStatus("已在浏览器中打开登录页面，等待回调…");
                openBrowser(authorizationUrl());

                String code = awaitCallbackCode(server, future);
                if (code == null) {
                    // 未来已被取消/完成，直接退出
                    return;
                }

                listener.onStatus("收到回调，正在交换令牌…");
                AuthResult result = exchangeCodeForProfile(code, listener);
                future.complete(result);
            } catch (AuthException e) {
                future.completeExceptionally(e);
            } catch (Exception e) {
                future.completeExceptionally(wrapUnknown(e));
            } finally {
                closeQuietly(server);
                activeServer = null;
            }
        });
        return future;
    }

    /**
     * 手动模式：使用用户粘贴的重定向 URL 或纯 authorization code 完成登录。
     */
    public static CompletableFuture<AuthResult> loginManual(String urlOrCode, StatusListener listener) {
        if (isLoginInProgress()) {
            return failedFuture(new AuthException("已有登录流程进行中，请先完成或取消它"));
        }

        // 先检查 URL 中是否携带 OAuth 错误
        String error = extractError(urlOrCode);
        if (error != null) {
            return failedFuture(new AuthException("登录失败：" + error));
        }
        String code = extractCode(urlOrCode);
        if (code == null) {
            return failedFuture(new AuthException(
                    "无法从输入中识别授权码。\n请粘贴浏览器地址栏中的完整重定向 URL（应包含 ?code= 参数）。"));
        }

        CompletableFuture<AuthResult> future = new CompletableFuture<>();
        activeFuture = future;

        Thread.startVirtualThread(() -> {
            try {
                AuthResult result = exchangeCodeForProfile(code, listener);
                future.complete(result);
            } catch (AuthException e) {
                future.completeExceptionally(e);
            } catch (Exception e) {
                future.completeExceptionally(wrapUnknown(e));
            }
        });
        return future;
    }

    /**
     * 使用保存的 refreshToken 静默换取全新的 Minecraft 档案与令牌。
     * （应用已保存的 Microsoft 账号时调用，避免本地令牌过期。）
     */
    public static CompletableFuture<AuthResult> refresh(String refreshToken, StatusListener listener) {
        CompletableFuture<AuthResult> future = new CompletableFuture<>();
        Thread.startVirtualThread(() -> {
            try {
                listener.onStatus("正在刷新微软令牌…");
                Map<String, String> form = new LinkedHashMap<>();
                form.put("client_id", CLIENT_ID);
                form.put("grant_type", "refresh_token");
                form.put("refresh_token", refreshToken);
                form.put("redirect_uri", REDIRECT_URI);

                JSONObject tokenJson = new JSONObject(postForm("https://login.live.com/oauth20_token.srf", form));
                checkOAuthError(tokenJson, "刷新令牌失败");

                String msToken = tokenJson.getString("access_token");
                String newRefresh = tokenJson.optString("refresh_token", refreshToken);
                future.complete(profileFromMsToken(msToken, newRefresh, listener));
            } catch (AuthException e) {
                future.completeExceptionally(e);
            } catch (Exception e) {
                future.completeExceptionally(wrapUnknown(e));
            }
        });
        return future;
    }

    // ========================
    // 输入解析（移植自 Dart 实现）
    // ========================

    /**
     * 从 URL 或纯文本中提取 authorization code。无法识别时返回 null。
     */
    public static String extractCode(String input) {
        String trimmed = input.trim();
        Matcher m = CODE_PATTERN.matcher(trimmed);
        if (m.find()) {
            return urlDecode(m.group(1));
        }
        // 非 URL 格式且长度足够，直接作为 code 使用
        if (!trimmed.contains(" ") && !trimmed.contains("://") && trimmed.length() > 20) {
            return trimmed;
        }
        return null;
    }

    /**
     * 从 URL 中提取 OAuth 错误描述（error / error_description），无错误返回 null。
     */
    public static String extractError(String input) {
        String trimmed = input.trim();
        Matcher m = ERROR_PATTERN.matcher(trimmed);
        if (m.find()) {
            Matcher desc = ERROR_DESC_PATTERN.matcher(trimmed);
            if (desc.find()) {
                return urlDecode(desc.group(1));
            }
            return urlDecode(m.group(1));
        }
        return null;
    }

    // ========================
    // 回调服务器（纯 ServerSocket，避免依赖 jdk.httpserver 模块）
    // ========================

    /**
     * 阻塞等待浏览器回调中的 code；返回 null 表示外部已取消。
     */
    private static String awaitCallbackCode(ServerSocket server, CompletableFuture<AuthResult> future)
            throws AuthException, IOException {
        long deadline = System.nanoTime() + LOGIN_TIMEOUT.toNanos();

        while (System.nanoTime() < deadline) {
            if (future.isDone()) {
                return null; // 已被 cancelActive() 完成
            }
            Socket socket;
            try {
                socket = server.accept();
            } catch (SocketTimeoutException e) {
                continue; // 每秒醒一次检查取消与超时
            } catch (IOException e) {
                if (future.isDone() || server.isClosed()) return null;
                throw e;
            }

            try (socket) {
                socket.setSoTimeout(5000);
                String requestLine = readRequestLine(socket.getInputStream());
                if (requestLine == null) continue;

                // 形如: GET /authentication-response?code=xxx HTTP/1.1
                String[] parts = requestLine.split(" ");
                if (parts.length < 2) continue;
                String target = parts[1];

                if (!target.startsWith(CALLBACK_PATH)) {
                    writeHtmlResponse(socket.getOutputStream(), 404,
                            "404", "Not Found", false);
                    continue;
                }

                String query = target.contains("?")
                        ? target.substring(target.indexOf('?') + 1) : "";
                String error = extractError(query);
                if (error != null) {
                    writeHtmlResponse(socket.getOutputStream(), 400,
                            "登录失败", "错误：" + error, false);
                    throw new AuthException("Microsoft 登录错误：" + error);
                }

                String code = extractCode(query);
                if (code == null) {
                    writeHtmlResponse(socket.getOutputStream(), 400,
                            "认证失败", "重定向中缺少授权码参数。", false);
                    throw new AuthException("重定向中未找到 authorization code");
                }

                writeHtmlResponse(socket.getOutputStream(), 200,
                        "认证成功", "你可以关闭此页面并返回 Gemini 客户端。", true);
                return code;
            }
        }
        throw new AuthException("登录超时：请在 5 分钟内完成浏览器登录");
    }

    /** 读取 HTTP 请求行（到 CRLF 为止），带 4KB 上限。 */
    private static String readRequestLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(256);
        int prev = -1;
        int b;
        while ((b = in.read()) != -1) {
            if (prev == '\r' && b == '\n') {
                byte[] data = buf.toByteArray();
                if (data.length == 0) return null;
                return new String(data, 0, data.length - 1, StandardCharsets.ISO_8859_1);
            }
            buf.write(b);
            prev = b;
            if (buf.size() > 4096) return null;
        }
        return null;
    }

    /** 向浏览器返回一个与 Gemini 风格一致的暗色结果页。 */
    private static void writeHtmlResponse(OutputStream out, int status,
                                          String title, String message, boolean success) throws IOException {
        String color = success ? "#89DDFF" : "#FF8080";
        String html = "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">"
                + "<title>" + title + "</title><style>"
                + "body{font-family:'Segoe UI',sans-serif;display:flex;justify-content:center;"
                + "align-items:center;height:100vh;background:#0B0B0E;margin:0;}"
                + ".card{padding:48px 64px;border:1px solid #232328;border-radius:14px;text-align:center;}"
                + "h1{font-size:22px;color:" + color + ";font-weight:600;margin:0 0 12px;}"
                + "p{color:#8A8A8A;font-size:14px;margin:0;}"
                + "</style></head><body><div class=\"card\"><h1>" + title
                + "</h1><p>" + message + "</p></div></body></html>";
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + status + " " + (status == 200 ? "OK" : "Bad Request") + "\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    // ========================
    // 令牌交换链（code / refresh → Minecraft 档案）
    // ========================

    /** authorization code → 微软令牌 → 完整档案。 */
    private static AuthResult exchangeCodeForProfile(String code, StatusListener listener)
            throws AuthException {
        listener.onStatus("正在交换微软令牌…");
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", CLIENT_ID);
        form.put("code", code);
        form.put("grant_type", "authorization_code");
        form.put("redirect_uri", REDIRECT_URI);

        JSONObject tokenJson;
        try {
            tokenJson = new JSONObject(postForm("https://login.live.com/oauth20_token.srf", form));
        } catch (AuthException e) {
            throw new AuthException("微软令牌交换失败：" + e.getMessage(), e);
        }
        checkOAuthError(tokenJson, "微软令牌交换失败");

        String msToken = tokenJson.getString("access_token");
        String refreshToken = tokenJson.optString("refresh_token", "");
        return profileFromMsToken(msToken, refreshToken, listener);
    }

    /** 微软令牌 → XBL → XSTS → Minecraft 令牌 → 档案。 */
    private static AuthResult profileFromMsToken(String msToken, String refreshToken,
                                                 StatusListener listener) throws AuthException {
        // ---- 第 2 步：Xbox Live ----
        listener.onStatus("正在进行 Xbox Live 认证…");
        JSONObject xbl = tryGetXblToken(msToken, true);
        if (xbl == null) xbl = tryGetXblToken(msToken, false);
        if (xbl == null) {
            throw new AuthException("Xbox Live 认证失败：令牌格式不被接受。");
        }
        String xblToken = xbl.getString("Token");
        String uhs = xbl.getJSONObject("DisplayClaims")
                .getJSONArray("xui").getJSONObject(0).getString("uhs");

        // ---- 第 3 步：XSTS ----
        listener.onStatus("正在进行 XSTS 认证…");
        JSONObject xstsReq = new JSONObject()
                .put("Properties", new JSONObject()
                        .put("SandboxId", "RETAIL")
                        .put("UserTokens", new org.json.JSONArray().put(xblToken)))
                .put("RelyingParty", "rp://api.minecraftservices.com/")
                .put("TokenType", "JWT");
        JSONObject xsts = new JSONObject(
                postJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsReq));
        if (xsts.has("XErr")) {
            throw new AuthException(xerrToMessage(xsts.getLong("XErr")));
        }
        String xstsToken = xsts.getString("Token");

        // ---- 第 4 步：Minecraft 令牌 ----
        listener.onStatus("正在获取 Minecraft 令牌…");
        JSONObject mcReq = new JSONObject()
                .put("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
        JSONObject mc = new JSONObject(
                postJson("https://api.minecraftservices.com/authentication/login_with_xbox", mcReq));
        if (!mc.has("access_token")) {
            throw new AuthException("Minecraft 令牌获取失败：" + mc.optString("errorMessage",
                    mc.optString("error", "未知错误")));
        }
        String mcToken = mc.getString("access_token");

        // ---- 第 5 步：Minecraft 档案 ----
        listener.onStatus("正在获取游戏档案…");
        JSONObject profile = new JSONObject(
                getWithBearer("https://api.minecraftservices.com/minecraft/profile", mcToken));
        if (!profile.has("name") || !profile.has("id")) {
            String err = profile.optString("error", "");
            if ("NOT_FOUND".equals(err)) {
                throw new AuthException("该微软账号尚未购买 Minecraft（未找到游戏档案）。");
            }
            throw new AuthException("获取游戏档案失败：" + profile.optString("errorMessage",
                    err.isEmpty() ? "未知错误" : err));
        }

        return new AuthResult(
                profile.getString("name"),
                AltAccount.parseUuid(profile.getString("id")).toString(),
                mcToken,
                refreshToken);
    }

    /** 尝试获取 XBL Token；{@code usePrefix} 控制 RpsTicket 是否加 "d=" 前缀。 */
    private static JSONObject tryGetXblToken(String msToken, boolean usePrefix) {
        try {
            JSONObject req = new JSONObject()
                    .put("Properties", new JSONObject()
                            .put("AuthMethod", "RPS")
                            .put("SiteName", "user.auth.xboxlive.com")
                            .put("RpsTicket", (usePrefix ? "d=" : "") + msToken))
                    .put("RelyingParty", "http://auth.xboxlive.com")
                    .put("TokenType", "JWT");
            JSONObject resp = new JSONObject(
                    postJson("https://user.auth.xboxlive.com/user/authenticate", req));
            if (!resp.has("Token") || !resp.has("DisplayClaims")) return null;
            return resp;
        } catch (Exception e) {
            return null;
        }
    }

    /** XSTS XErr 错误码 → 中文提示。 */
    private static String xerrToMessage(long xerr) {
        if (xerr == 2148916233L) return "该微软账号没有 Xbox 档案，请先前往 xbox.com 创建。";
        if (xerr == 2148916235L) return "所在地区暂不支持 Xbox Live。";
        if (xerr == 2148916238L) return "该账号为未成年账号，需要加入家庭组并由家长授权。";
        return "XSTS 认证错误（代码 " + xerr + "）。";
    }

    /** OAuth 错误响应检查（token 端点返回 200 但带 error 字段的情况）。 */
    private static void checkOAuthError(JSONObject json, String prefix) throws AuthException {
        if (json.has("error")) {
            String desc = json.optString("error_description", json.getString("error"));
            // error_description 中的 URL 编码（如 "+"）需要解码
            throw new AuthException(prefix + "：" + desc.replace('+', ' '));
        }
    }

    // ========================
    // HTTP 辅助
    // ========================

    private static HttpClient newClient() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    private static String postForm(String url, Map<String, String> form) throws AuthException {
        StringBuilder body = new StringBuilder();
        form.forEach((k, v) -> {
            if (body.length() > 0) body.append('&');
            body.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return send(req);
    }

    private static String postJson(String url, JSONObject json) throws AuthException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();
        return send(req);
    }

    private static String getWithBearer(String url, String token) throws AuthException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        return send(req);
    }

    private static String send(HttpRequest req) throws AuthException {
        HttpResponse<String> resp;
        try {
            resp = newClient().send(req, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.http.HttpConnectTimeoutException e) {
            throw new AuthException("连接服务器超时，请检查网络连接后重试。", e);
        } catch (java.net.http.HttpTimeoutException e) {
            throw new AuthException("网络请求超时，请检查网络连接后重试。", e);
        } catch (java.net.ConnectException | java.net.UnknownHostException e) {
            throw new AuthException("无法连接服务器，请检查网络 / DNS 设置。", e);
        } catch (javax.net.ssl.SSLException e) {
            throw new AuthException("SSL 连接错误，请检查系统时间或网络环境。", e);
        } catch (IOException e) {
            throw new AuthException("网络错误：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthException("登录被中断。", e);
        }
        if (resp.statusCode() != 200) {
            String snippet = resp.body() == null ? "" : resp.body();
            if (snippet.length() > 200) snippet = snippet.substring(0, 200) + "…";
            throw new AuthException("HTTP " + resp.statusCode() + "：" + snippet);
        }
        return resp.body();
    }

    // ========================
    // 浏览器 / 杂项
    // ========================

    private static void openBrowser(String url) throws AuthException {
        try {
            Util.getPlatform().openUri(url);
        } catch (Exception e) {
            throw new AuthException("无法打开浏览器：" + e.getMessage()
                    + "\n请手动打开以下链接：\n" + url, e);
        }
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private static void closeQuietly(ServerSocket server) {
        if (server != null) {
            try {
                server.close();
            } catch (IOException ignored) {}
        }
    }

    private static CompletableFuture<AuthResult> failedFuture(AuthException e) {
        CompletableFuture<AuthResult> f = new CompletableFuture<>();
        f.completeExceptionally(e);
        return f;
    }

    private static AuthException wrapUnknown(Exception e) {
        if (e instanceof AuthException ae) return ae;
        return new AuthException("未知错误：" + e.getClass().getSimpleName()
                + (e.getMessage() != null ? "（" + e.getMessage() + "）" : ""), e);
    }
}
