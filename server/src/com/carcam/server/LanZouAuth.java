package com.carcam.server;

import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * 蓝奏云账号密码登录（PC 端接口，纯 API）。
 *
 * 完整链路：
 *   ① GET  登录页 → 大概率返回 CDN 混淆 JS（含 arg1='40位HEX'）
 *   ② 算 acw_sc__v2 塞进 Cookie → 重放 ①，拿到真正的登录页
 *   ③ POST accounts.php  task=uselogin&username&password&ref=pc.woozooo.com
 *            → {"zt":1,"msgs":"https://pc.woozooo.com/acc.php?t=..."}
 *   ④ GET  msgs 里的地址 → 签发登录态（phpdisk_info / lanzou_ifo / ylogin / ylogins / uag）
 *   ⑤ 自检：Cookie 含 phpdisk_info 或 lanzou_ifo，并真调一次 task=47 目录接口
 *
 * 判断成败看 zt，不看 HTTP 码；zt != 1 时把服务端 msgs 原文抛给界面。
 */
public class LanZouAuth {

    private static final String TAG = "LanZouAuth";

    /** 全程统一 UA，登录态与上传共用，避免 UA 不一致被判非法 */
    public static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    private static final String ACCOUNTS = "https://accounts.woozooo.com";
    private static final String LOGIN_PAGE = ACCOUNTS + "/accounts.php?action=login&ref=pc.woozooo.com";
    private static final String LOGIN_POST = ACCOUNTS + "/accounts.php";
    private static final String PC = "https://pc.woozooo.com";
    private static final String MYDISK = PC + "/mydisk.php";

    /** 阿里 CDN 换位表（1 基下标：新串第 j 位取原串第 POS[j] 位） */
    private static final int[] POS = {
            15, 35, 29, 24, 33, 16, 1, 38, 10, 9,
            19, 31, 40, 27, 22, 23, 25, 13, 6, 11,
            39, 18, 20, 8, 14, 21, 32, 26, 2, 30,
            7, 4, 17, 5, 3, 28, 34, 37, 12, 36
    };
    private static final String MASK = "3000176000856006061501533003690027800375";
    private static final Pattern ARG1 = Pattern.compile("arg1\\s*=\\s*'([0-9A-Fa-f]{40})'");

    /** 登录结果 */
    public static class Result {
        public boolean ok;
        /** 成功时为合并后的 Cookie 头（accounts + pc 两个域） */
        public String cookie;
        /** 失败时为服务端原文/本地原因 */
        public String message;
    }

    private static Result ok(String cookie) {
        Result r = new Result();
        r.ok = true;
        r.cookie = cookie;
        r.message = "登录成功";
        return r;
    }

    private static Result fail(String msg) {
        Result r = new Result();
        r.ok = false;
        r.message = msg == null ? "登录失败" : msg;
        return r;
    }

    /** 用账号密码登录，返回合并 Cookie。每次调用独立 CookieJar，可并发。 */
    public static Result login(String username, String password) {
        if (username == null || username.trim().isEmpty()
                || password == null || password.isEmpty()) {
            return fail("账号或密码为空");
        }
        CookieJar jar = new CookieJar();
        try {
            // ① + ② 拿真正登录页（内部自动过 CDN 校验）
            httpGet(jar, LOGIN_PAGE, null);

            // ③ 提交表单
            String form = "task=" + URLEncoder.encode("uselogin", "UTF-8")
                    + "&username=" + URLEncoder.encode(username.trim(), "UTF-8")
                    + "&password=" + URLEncoder.encode(password, "UTF-8")
                    + "&ref=" + URLEncoder.encode("pc.woozooo.com", "UTF-8");
            String resp = httpPost(jar, LOGIN_POST, form, LOGIN_PAGE);

            JSONObject json;
            try {
                json = new JSONObject(resp);
            } catch (Exception e) {
                return fail("登录接口返回异常：" + snippet(resp));
            }
            int zt = json.optInt("zt", 0);
            if (zt != 1) {
                String msg = json.optString("msgs", "").trim();
                return fail(msg.isEmpty() ? ("账号或密码不正确（zt=" + zt + "）") : msg);
            }

            // ④ 二次跳转，签发登录态
            String redirect = unescape(json.optString("msgs", ""));
            if (redirect.trim().isEmpty()) {
                return fail("登录返回缺少跳转地址");
            }
            if (redirect.startsWith("/")) redirect = ACCOUNTS + redirect;
            httpGet(jar, redirect, LOGIN_PAGE);

            // ⑤ 自检一：鉴权 Cookie
            if (!jar.has("phpdisk_info") && !jar.has("lanzou_ifo")) {
                return fail("登录鉴权失败：未取到 phpdisk_info / lanzou_ifo");
            }

            // ⑤ 自检二：真调一次目录接口，防止"看着登上了其实读不了网盘"
            String check = diskCheck(jar);
            if (check != null) return fail(check);

            String cookie = jar.dump();
            Log.i(TAG, "登录成功，账号=" + username.trim() + "，Cookie 长度=" + cookie.length());
            return ok(cookie);
        } catch (Exception e) {
            Log.e(TAG, "登录异常: " + e);
            return fail("登录失败：" + e.getMessage());
        }
    }

    /** 用已有 Cookie 自检能否真正读网盘（task=47 目录接口） */
    public static boolean checkCookie(String cookie) {
        if (cookie == null || cookie.trim().isEmpty()) return false;
        CookieJar jar = CookieJar.fromHeader(cookie);
        return diskCheck(jar) == null;
    }

    // ---------------- 目录接口自检 ----------------

    private static String diskCheck(CookieJar jar) {
        try {
            String body = httpPost(jar, PC + "/doupload.php", "task=47&folder_id=-1", MYDISK);
            String t = body.trim();
            if (t.startsWith("<")) {
                return "登录后读取网盘失败：登录态未生效（返回了网页）";
            }
            JSONObject j = new JSONObject(t);
            int zt = j.optInt("zt", 0);
            if (zt == 1 || zt == 2) return null;
            String info = j.optString("info", "").trim();
            return "登录后读取网盘失败：" + (info.isEmpty() ? ("zt=" + zt) : info);
        } catch (Exception e) {
            return "登录后读取网盘失败：" + e.getMessage();
        }
    }

    // ---------------- HTTP ----------------

    private static String httpGet(CookieJar jar, String url, String referer) throws IOException {
        String current = url;
        for (int i = 0; i < 8; i++) {
            HttpURLConnection c = open(jar, "GET", current, referer);
            int code = c.getResponseCode();
            jar.store(current, c.getHeaderFields());
            String location = (code >= 300 && code < 400) ? c.getHeaderField("Location") : null;
            byte[] body = location != null ? new byte[0] : readBytes(c, code);
            c.disconnect();

            // 302/301 手动跟随，保证每一跳都带上当前 Cookie（Java 自动跟随会丢）
            if (location != null && !location.isEmpty()) {
                String next = absolutize(current, location);
                if (next == null) return "";
                current = next;
                continue;
            }

            String text = new String(body, "UTF-8");
            Matcher m = ARG1.matcher(text);
            if (m.find()) {
                // CDN 校验：算出 acw_sc__v2 后重放
                jar.set(hostOf(current), "acw_sc__v2", acw(m.group(1)));
                continue;
            }
            return text;
        }
        throw new IOException("CDN 校验未通过（连续 8 次被拦）");
    }

    /** 把 Location 拼成绝对地址 */
    private static String absolutize(String base, String location) {
        try {
            return new URL(new URL(base), location).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String httpPost(CookieJar jar, String url, String form, String referer) throws IOException {
        for (int i = 0; i < 4; i++) {
            HttpURLConnection c = open(jar, "POST", url, referer);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            OutputStream out = c.getOutputStream();
            out.write(form.getBytes("UTF-8"));
            out.flush();
            out.close();
            int code = c.getResponseCode();
            jar.store(url, c.getHeaderFields());
            byte[] body = readBytes(c, code);
            c.disconnect();
            String text = new String(body, "UTF-8");
            Matcher m = ARG1.matcher(text);
            if (m.find()) {
                jar.set(hostOf(url), "acw_sc__v2", acw(m.group(1)));
                continue;
            }
            return text;
        }
        throw new IOException("CDN 校验未通过（连续 4 次被拦）");
    }

    private static HttpURLConnection open(CookieJar jar, String method, String url, String referer)
            throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        // 不自动跟随跳转：跳转要自己带 Cookie 走，交给 httpGet
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
        // 必须 identity，否则拿到 gzip 二进制，JSON 解析全乱
        c.setRequestProperty("Accept-Encoding", "identity");
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        c.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        if (referer != null) c.setRequestProperty("Referer", referer);
        String origin = originOf(url);
        if (origin != null && "POST".equals(method)) c.setRequestProperty("Origin", origin);
        String cookie = jar.header(url);
        if (cookie != null) c.setRequestProperty("Cookie", cookie);
        return c;
    }

    private static byte[] readBytes(HttpURLConnection c, int code) throws IOException {
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in == null) return new byte[0];
        String enc = c.getHeaderField("Content-Encoding");
        // 服务端有时无视 identity 照样压缩，这里兜一下
        if (enc != null && enc.toLowerCase().contains("gzip")) {
            in = new GZIPInputStream(in);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return bos.toByteArray();
    }

    // ---------------- 工具 ----------------

    /**
     * acw_sc__v2：先把 arg1 按换位表重排（新串第 j 位取原串第 POS[j] 位），
     * 再与掩码两两一组异或，输出 40 位 HEX。
     */
    public static String acw(String arg1) {
        StringBuilder reordered = new StringBuilder(40);
        for (int j = 0; j < POS.length; j++) {
            reordered.append(arg1.charAt(POS[j] - 1));
        }
        String s = reordered.toString();
        StringBuilder sb = new StringBuilder(40);
        for (int i = 0; i < 40; i += 2) {
            sb.append(String.format("%02x", Integer.parseInt(MASK.substring(i, i + 2), 16)
                    ^ Integer.parseInt(s.substring(i, i + 2), 16)));
        }
        return sb.toString();
    }

    /** JSON 里的 URL 是转义的：https:\/\/pc.woozooo.com\/acc.php?t=... */
    public static String unescape(String s) {
        if (s == null) return "";
        return s.replace("\\/", "/").replace("\\u0026", "&").replace("\\u003d", "=");
    }

    private static String snippet(String s) {
        if (s == null) return "";
        s = s.replace('\n', ' ').replace('\r', ' ').trim();
        return s.length() > 160 ? s.substring(0, 160) + "…" : s;
    }

    private static String hostOf(String url) {
        try {
            return new URL(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private static String originOf(String url) {
        try {
            URL u = new URL(url);
            return u.getProtocol() + "://" + u.getHost();
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------- 自建 CookieJar ----------------

    /**
     * 自己的 CookieJar（host → name → value）。
     * 不用 java.net.CookieManager：它对 CDN 校验 Cookie 的存储/回放行为在安卓与桌面上不一致，
     * 实测会导致 acw_sc__v2 带不上或无效，校验永远过不去。
     */
    static final class CookieJar {
        private final Map<String, Map<String, String>> byHost = new HashMap<>();

        static CookieJar fromHeader(String header) {
            CookieJar jar = new CookieJar();
            if (header == null) return jar;
            for (String part : header.split(";")) {
                String kv = part.trim();
                int eq = kv.indexOf('=');
                if (eq <= 0) continue;
                String name = kv.substring(0, eq).trim();
                String value = kv.substring(eq + 1).trim();
                if (!name.isEmpty()) jar.set("pc.woozooo.com", name, value);
            }
            return jar;
        }

        synchronized void set(String host, String name, String value) {
            if (host == null || name == null || name.isEmpty() || value == null) return;
            String h = norm(host);
            Map<String, String> m = byHost.get(h);
            if (m == null) {
                m = new HashMap<>();
                byHost.put(h, m);
            }
            m.put(name, value);
        }

        synchronized void store(String url, Map<String, List<String>> headers) {
            if (headers == null) return;
            String host = hostOf(url);
            if (host == null) return;
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey() == null || !"Set-Cookie".equalsIgnoreCase(e.getKey())) continue;
                List<String> values = e.getValue();
                if (values == null) continue;
                for (String raw : values) {
                    if (raw == null || raw.trim().isEmpty()) continue;
                    String[] parts = raw.split(";");
                    String pair = parts[0].trim();
                    int eq = pair.indexOf('=');
                    if (eq <= 0) continue;
                    String name = pair.substring(0, eq).trim();
                    String value = pair.substring(eq + 1).trim();
                    if (value.length() > 1 && value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    String domain = host;
                    for (int i = 1; i < parts.length; i++) {
                        String p = parts[i].trim();
                        int d = p.indexOf('=');
                        if (d > 0 && "domain".equalsIgnoreCase(p.substring(0, d).trim())) {
                            String dv = p.substring(d + 1).trim();
                            if (!dv.isEmpty()) domain = dv;
                        }
                    }
                    set(domain, name, value);
                }
            }
        }

        /** 精确匹配或父域后缀匹配 */
        synchronized String header(String url) {
            String host = norm(hostOf(url));
            if (host == null) return null;
            StringBuilder sb = new StringBuilder();
            Set<String> seen = new HashSet<>();
            List<Map.Entry<String, Map<String, String>>> list = new ArrayList<>(byHost.entrySet());
            list.sort((a, b) -> norm(b.getKey()).length() - norm(a.getKey()).length());
            for (Map.Entry<String, Map<String, String>> e : list) {
                String d = norm(e.getKey());
                if (!(host.equals(d) || host.endsWith("." + d))) continue;
                for (Map.Entry<String, String> kv : e.getValue().entrySet()) {
                    if (!seen.add(kv.getKey())) continue;
                    if (sb.length() > 0) sb.append("; ");
                    sb.append(kv.getKey()).append('=').append(kv.getValue());
                }
            }
            return sb.length() == 0 ? null : sb.toString();
        }

        synchronized boolean has(String name) {
            for (Map<String, String> m : byHost.values()) {
                if (m.containsKey(name)) return true;
            }
            return false;
        }

        /** 合并成一条 Cookie 头（上传时直接用） */
        synchronized String dump() {
            LinkedHashSet<String> out = new LinkedHashSet<>();
            for (Map<String, String> m : byHost.values()) {
                for (Map.Entry<String, String> kv : m.entrySet()) {
                    out.add(kv.getKey() + "=" + kv.getValue());
                }
            }
            StringBuilder sb = new StringBuilder();
            for (String s : out) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(s);
            }
            return sb.toString();
        }

        private static String norm(String host) {
            if (host == null) return "";
            String h = host.toLowerCase();
            while (h.startsWith(".")) h = h.substring(1);
            return h;
        }
    }
}
