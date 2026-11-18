package com.carcam.server;

import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** 蓝奏云官方上传客户端（Cookie 认证，pc.woozooo.com） */
public class LanZouClient {

    private static final String TAG = "LanZouClient";
    private static final String PC = "https://pc.woozooo.com";

    private final String cookie;
    /** 上传端点，蓝奏云 PC 端 HTML5 上传为 /html5up.php，兜底 /doupload.php */
    public String endpoint = "/html5up.php";
    /** 上传目标文件夹 fol_id，"-1" 表示根目录 */
    public String folderId = "-1";
    public volatile String lastError = "";
    public volatile String lastResponse = "";
    private final ProgressCallback cb;
    /** 上一次上传是否因登录态过期失败（供自动重登用） */
    public volatile boolean authExpired = false;

    public interface ProgressCallback {
        void onProgress(int percent, long uploaded, long total);
    }

    public LanZouClient(String cookie, ProgressCallback cb) {
        this.cookie = cookie;
        this.cb = cb;
    }

    /** 测试 Cookie 是否真的能读网盘（task=47 目录接口，比看网页可靠） */
    public boolean testCookie() {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(PC + "/doupload.php").openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Cookie", cookie);
            conn.setRequestProperty("User-Agent", LanZouAuth.UA);
            conn.setRequestProperty("Referer", PC + "/mydisk.php");
            conn.setRequestProperty("Origin", PC);
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Accept-Encoding", "identity");
            OutputStream out = conn.getOutputStream();
            out.write("task=47&folder_id=-1".getBytes("UTF-8"));
            out.flush();
            out.close();
            String resp = readAll(conn);
            conn.disconnect();
            String t = resp.trim();
            if (t.startsWith("<")) return false;   // 返回网页 = 登录态失效
            JSONObject json = new JSONObject(t);
            int zt = json.optInt("zt", 0);
            return zt == 1 || zt == 2;
        } catch (Exception e) {
            return false;
        }
    }

    /** 在根目录按名字查找子文件夹，返回 fol_id；找不到返回 null */
    public String resolveFolderId(String folderName) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(PC + "/doupload.php").openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Cookie", cookie);
            conn.setRequestProperty("User-Agent", LanZouAuth.UA);
            conn.setRequestProperty("Referer", PC + "/mydisk.php");
            conn.setRequestProperty("Origin", PC);
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Accept-Encoding", "identity");
            OutputStream out = conn.getOutputStream();
            out.write("task=47&folder_id=-1".getBytes("UTF-8"));
            out.flush();
            out.close();
            String resp = readAll(conn);
            conn.disconnect();

            if (resp.trim().startsWith("<")) {
                authExpired = true;
                fail("读取目录返回网页（登录态失效）");
                return null;
            }
            JSONObject json = new JSONObject(resp);
            org.json.JSONArray text = json.optJSONArray("text");
            if (text == null) {
                fail("目录列表为空：" + snippet(resp));
                return null;
            }
            for (int i = 0; i < text.length(); i++) {
                JSONObject o = text.optJSONObject(i);
                if (o == null) continue;
                String fid = o.optString("fol_id", "");
                String nm = o.optString("name", "");
                if (fid.isEmpty()) continue;
                if (nm != null && nm.trim().equals(folderName == null ? "" : folderName.trim())) return fid;
            }
            return null;
        } catch (Exception e) {
            fail("读取目录异常：" + e.getClass().getSimpleName() + " " + e.getMessage());
            return null;
        }
    }

    /** 上传文件到蓝奏云根目录（接口：pc.woozooo.com/fileup.php） */
    public boolean upload(File file) {
        authExpired = false;
        lastError = "";
        try {
            String filename = safeName(file.getName());
            long size = file.length();

            // 蓝奏云免费版单文件限制 100MB
            if (size > 100L * 1024 * 1024) {
                fail("文件超过 100MB，蓝奏云免费版不支持: " + filename + " (" + size + " bytes)");
                return false;
            }

            String boundary = "----WebKitFormBoundary" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String url = PC + endpoint;
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(120000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Cookie", cookie);
            conn.setRequestProperty("User-Agent", LanZouAuth.UA);
            conn.setRequestProperty("Referer", PC + "/mydisk.php");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setRequestProperty("Accept-Encoding", "identity");

            OutputStream out = conn.getOutputStream();
            String dash = "--" + boundary;

            // 表单字段（顺序与网页端一致）
            writeLine(out, dash);
            writeLine(out, "Content-Disposition: form-data; name=\"task\"");
            writeLine(out, "");
            writeLine(out, "1");

            writeLine(out, dash);
            writeLine(out, "Content-Disposition: form-data; name=\"folder_id\"");
            writeLine(out, "");
            writeLine(out, folderId);      // "-1"=根目录，否则为子文件夹 fol_id

            writeLine(out, dash);
            writeLine(out, "Content-Disposition: form-data; name=\"id\"");
            writeLine(out, "");
            writeLine(out, "WU_FILE_0");

            writeLine(out, dash);
            writeLine(out, "Content-Disposition: form-data; name=\"name\"");
            writeLine(out, "");
            writeLine(out, filename);

            // 文件字段名必须是 upload_file（不是 upload_file[]）
            writeLine(out, dash);
            writeLine(out, "Content-Disposition: form-data; name=\"upload_file\"; filename=\"" + filename + "\"");
            writeLine(out, "Content-Type: application/octet-stream");
            writeLine(out, "");

            try (FileInputStream in = new FileInputStream(file)) {
                byte[] buf = new byte[65536];
                int n;
                long uploaded = 0;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    uploaded += n;
                    if (cb != null) cb.onProgress((int) (uploaded * 90 / size), uploaded, size);
                }
            }

            writeLine(out, "");
            writeLine(out, dash + "--");
            out.flush();

            int code = conn.getResponseCode();
            String resp = readAll(conn);
            conn.disconnect();

            if (code == 404) {
                fail("接口不存在（404）" + endpoint + "：" + snippet(resp));
                return false;
            }
            if (code != 200) {
                fail("上传失败 HTTP " + code + "：" + snippet(resp));
                return false;
            }

            lastResponse = snippet(resp);
            if (resp.trim().startsWith("<")) {
                authExpired = true;
                fail("返回的是网页而不是 JSON（登录态失效或被 CDN 拦）：" + snippet(resp));
                return false;
            }

            JSONObject json = new JSONObject(resp);
            int zt = json.optInt("zt", -1);
            if (zt == 1) {
                String id = "";
                org.json.JSONArray text = json.optJSONArray("text");
                if (text != null && text.length() > 0 && text.optJSONObject(0) != null) {
                    id = text.optJSONObject(0).optString("id", "");
                }
                Log.i(TAG, "上传成功: " + filename + (id.isEmpty() ? "" : "，file_id=" + id));
                if (cb != null) cb.onProgress(100, size, size);
                return true;
            } else if (zt == 9) {
                authExpired = true;
                fail("登录已过期（zt=9）：" + snippet(resp));
                return false;
            } else if (zt == 4) {
                fail("请求频繁（zt=4），稍后重试：" + snippet(resp));
                return false;
            } else {
                fail("上传失败 zt=" + zt + "：" + snippet(resp));
                return false;
            }
        } catch (Exception e) {
            fail("上传异常：" + e.getClass().getSimpleName() + " " + e.getMessage());
            return false;
        }
    }

    /** 去掉蓝奏云不接受的非法字符，保留中文与数字 */
    private static String safeName(String name) {
        if (name == null) return "unnamed";
        String s = name.replace("\u00a0", " ").replace("\u3000", " ");
        return s.replaceAll("[\\$%^!*<>)(+=`\'\"/:;,?]", "").trim();
    }

    private void fail(String reason) {
        lastError = reason;
        Log.e(TAG, reason);
    }

    private static String snippet(String s) {
        if (s == null) return "(空)";
        s = s.replace("\n", " ").replace("\r", " ").trim();
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }

    private String extractCookie(String name) {
        for (String part : cookie.split(";")) {
            part = part.trim();
            if (part.startsWith(name + "=")) {
                return part.substring(name.length() + 1);
            }
        }
        return null;
    }

    private static void writeLine(OutputStream out, String s) throws Exception {
        out.write(s.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String readAll(HttpURLConnection conn) throws Exception {
        java.io.InputStream in = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (in == null) return "";
        // 服务端可能无视 identity 返回 gzip，直接按文本解析会得到乱码/失败
        String enc = conn.getHeaderField("Content-Encoding");
        if (enc != null && enc.toLowerCase().contains("gzip")) {
            in = new java.util.zip.GZIPInputStream(in);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toString("UTF-8");
    }
}
