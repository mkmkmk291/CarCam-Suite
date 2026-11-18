package com.carcam.server;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 123云盘网页版 API 上传客户端（Cookie 认证） */
public class Pan123Client {

    private static final String TAG = "Pan123Client";
    private static final String BASE = "https://yun.123pan.cn";

    private final String cookie;
    private final String parentFileId; // 目标文件夹 ID，0 为根目录
    private final ProgressCallback cb;

    public interface ProgressCallback {
        void onProgress(int percent, long uploaded, long total);
    }

    public Pan123Client(String cookie, String parentFileId, ProgressCallback cb) {
        this.cookie = cookie;
        this.parentFileId = parentFileId == null || parentFileId.isEmpty() ? "0" : parentFileId;
        this.cb = cb;
    }

    /** 上传文件，返回 true 成功 */
    public boolean upload(File file) {
        try {
            String filename = file.getName();
            String md5 = md5(file);
            long size = file.length();

            // 1. 创建文件
            JSONObject createBody = new JSONObject();
            createBody.put("driveId", 0);
            createBody.put("parentFileId", Long.parseLong(parentFileId));
            createBody.put("filename", filename);
            createBody.put("etag", md5);
            createBody.put("size", size);
            createBody.put("duplicate", 1);

            JSONObject resp = postJson(BASE + "/b/api/file/upload_request", createBody);
            int code = resp.optInt("code", -1);
            if (code != 0) {
                Log.e(TAG, "create failed: " + resp);
                return false;
            }

            JSONObject data = resp.optJSONObject("data");
            if (data == null) return false;

            // 秒传
            if (data.optBoolean("reuse", false)) {
                Log.i(TAG, "秒传成功: " + filename);
                if (cb != null) cb.onProgress(100, size, size);
                return true;
            }

            String preuploadId = data.optString("preuploadID", "");
            int sliceSize = data.optInt("sliceSize", 5 * 1024 * 1024);
            if (preuploadId.isEmpty()) {
                Log.e(TAG, "no preuploadID: " + data);
                return false;
            }

            // 2. 分片上传
            int totalParts = (int) Math.ceil((double) size / sliceSize);
            for (int i = 1; i <= totalParts; i++) {
                // 获取分片上传地址
                JSONObject urlBody = new JSONObject();
                urlBody.put("preuploadID", preuploadId);
                urlBody.put("sliceNo", i);
                JSONObject urlResp = postJson(BASE + "/b/api/file/get_upload_url", urlBody);
                if (urlResp.optInt("code", -1) != 0) {
                    Log.e(TAG, "get_upload_url failed: " + urlResp);
                    return false;
                }
                String presignedUrl = urlResp.optJSONObject("data").optString("presignedURL", "");
                if (presignedUrl.isEmpty()) {
                    Log.e(TAG, "no presignedURL");
                    return false;
                }

                // PUT 上传分片
                long offset = (long) (i - 1) * sliceSize;
                int partLen = (int) Math.min(sliceSize, size - offset);
                if (!putPart(presignedUrl, file, offset, partLen)) {
                    Log.e(TAG, "put part failed: " + i);
                    return false;
                }

                if (cb != null) {
                    long uploaded = Math.min(size, (long) i * sliceSize);
                    cb.onProgress((int)(uploaded * 100 / size), uploaded, size);
                }
            }

            // 3. 完成上传
            JSONObject doneBody = new JSONObject();
            doneBody.put("preuploadID", preuploadId);
            JSONObject doneResp = postJson(BASE + "/b/api/file/upload_complete", doneBody);
            if (doneResp.optInt("code", -1) != 0) {
                Log.e(TAG, "upload_complete failed: " + doneResp);
                return false;
            }

            Log.i(TAG, "上传成功: " + filename);
            if (cb != null) cb.onProgress(100, size, size);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "upload error: " + e.getMessage());
            return false;
        }
    }

    /** 测试 Cookie 是否有效（获取用户信息） */
    public boolean testCookie() {
        try {
            JSONObject resp = getJson(BASE + "/b/api/user/info");
            return resp.optInt("code", -1) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private JSONObject postJson(String url, JSONObject body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Cookie", cookie);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));
        int code = conn.getResponseCode();
        String resp = readAll(conn);
        conn.disconnect();
        return new JSONObject(resp);
    }

    private JSONObject getJson(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("Cookie", cookie);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        int code = conn.getResponseCode();
        String resp = readAll(conn);
        conn.disconnect();
        return new JSONObject(resp);
    }

    private boolean putPart(String url, File file, long offset, int length) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("PUT");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/octet-stream");
        conn.setRequestProperty("Content-Length", String.valueOf(length));

        try (FileInputStream in = new FileInputStream(file);
             OutputStream out = conn.getOutputStream()) {
            in.skip(offset);
            byte[] buf = new byte[65536];
            int remaining = length;
            while (remaining > 0) {
                int n = in.read(buf, 0, Math.min(buf.length, remaining));
                if (n < 0) break;
                out.write(buf, 0, n);
                remaining -= n;
            }
            out.flush();
        }
        int code = conn.getResponseCode();
        conn.disconnect();
        return code >= 200 && code < 300;
    }

    private static String md5(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String readAll(HttpURLConnection conn) throws Exception {
        java.io.InputStream in = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (in == null) return "{}";
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toString("UTF-8");
    }
}
