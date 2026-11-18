package com.carcam.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 轻量 WebDAV 客户端（仅 PUT 上传，适配 123 云盘） */
public class WebDavClient {

    private final String host;      // e.g. dav.123pan.com
    private final String basePath;  // e.g. /webdav
    private final String user;
    private final String password;  // 应用密码
    private final ProgressCallback cb;

    public interface ProgressCallback {
        void onProgress(int percent, long downloaded, long total);
    }

    public WebDavClient(String host, String basePath, String user, String password, ProgressCallback cb) {
        this.host = host;
        this.basePath = basePath.endsWith("/") ? basePath : basePath + "/";
        this.user = user;
        this.password = password;
        this.cb = cb;
    }

    /** 上传文件到远程路径（远程路径以 / 开头，相对 basePath） */
    public boolean upload(String remotePath, File localFile) {
        String url = "https://" + host + basePath + remotePath.replaceAll("^/+", "");
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("PUT");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", basicAuth());
            conn.setRequestProperty("Content-Type", "video/mp4");
            conn.setRequestProperty("Content-Length", String.valueOf(localFile.length()));

            long total = localFile.length();
            long uploaded = 0;
            byte[] buf = new byte[65536];
            try (OutputStream out = conn.getOutputStream();
                 InputStream in = new FileInputStream(localFile)) {
                int n;
                int lastPct = -1;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    uploaded += n;
                    if (cb != null) {
                        int pct = total > 0 ? (int)(uploaded * 100 / total) : 0;
                        if (pct != lastPct) {
                            lastPct = pct;
                            cb.onProgress(pct, uploaded, total);
                        }
                    }
                }
                out.flush();
            }
            int code = conn.getResponseCode();
            // 200/201/204 都算成功
            if (code == 200 || code == 201 || code == 204) {
                if (cb != null) cb.onProgress(100, total, total);
                return true;
            }
            VLog("upload failed: HTTP " + code + " " + conn.getResponseMessage());
            return false;
        } catch (Exception e) {
            VLog("upload error: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** 测试连接（PROPFIND 根目录） */
    public boolean testConnection() {
        String url = "https://" + host + basePath;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("PROPFIND");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Authorization", basicAuth());
            conn.setRequestProperty("Depth", "0");
            int code = conn.getResponseCode();
            return code == 207 || code == 200 || code == 401; // 401 也算通了（只是密码错）
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String basicAuth() {
        String cred = user + ":" + password;
        return "Basic " + android.util.Base64.encodeToString(cred.getBytes(StandardCharsets.UTF_8),
                android.util.Base64.NO_WRAP);
    }

    private static void VLog(String s) {
        android.util.Log.i("WebDavClient", s);
    }
}
