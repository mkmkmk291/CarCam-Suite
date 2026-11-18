package com.carcam.server;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * 轻量 HTTP 服务器：
 *   GET /             录像浏览首页（手机浏览器直接可用）
 *   GET /view.html?p= 视频播放页（<video> 标签，支持拖动进度）
 *   GET /api/ping     探活 {"ok":true,...}
 *   GET /api/scan     扫描车机存储中的录像目录
 *   GET /api/list?p=  列目录（JSON）
 *   GET /video/<b64>  视频文件流（支持 Range 断点/拖动）
 *   GET /thumb/<b64>  视频缩略图（JPEG）
 */
public class HttpServer implements Runnable {

    public interface Log {
        void log(String s);
    }

    private final Log logger;
    private final int port;
    private volatile ServerSocket server;
    private volatile boolean running;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Semaphore connSem = new Semaphore(8);
    private final Map<String, byte[]> thumbCache = new LinkedHashMap<String, byte[]>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > 128;
        }
    };

    public HttpServer(int port, Log logger) {
        this.port = port;
        this.logger = logger;
    }

    public boolean isRunning() {
        return running && server != null && !server.isClosed();
    }

    public int getPort() {
        return port;
    }

    public void start() throws IOException {
        server = new ServerSocket(port);
        running = true;
        Thread t = new Thread(this, "carcam-http");
        t.setDaemon(true);
        t.start();
        logger.log("HTTP 服务已启动，端口 " + port);
    }

    public void stop() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (Exception ignored) {}
        pool.shutdownNow();
    }

    @Override
    public void run() {
        while (running) {
            try {
                Socket s = server.accept();
                try {
                    connSem.acquire();
                    pool.execute(() -> {
                        try {
                            handle(s);
                        } catch (Exception ignored) {
                        } finally {
                            connSem.release();
                            try { s.close(); } catch (Exception ignored) {}
                        }
                    });
                } catch (Exception e) {
                    connSem.release();
                    try { s.close(); } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                if (running) logger.log("HTTP accept 异常: " + e.getMessage());
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            }
        }
    }

    // ---------------- 请求处理 ----------------

    private void handle(Socket s) throws IOException {
        s.setSoTimeout(20000);
        InputStream in = s.getInputStream();
        OutputStream out = s.getOutputStream();
        // 读请求行 + 头（最多 32KB）
        StringBuilder req = new StringBuilder();
        int total = 0;
        int c;
        while (total < 32768) {
            c = in.read();
            if (c == -1) break;
            req.append((char) c);
            total++;
            if (req.length() >= 4 && req.substring(req.length() - 4).equals("\r\n\r\n")) break;
        }
        String head = req.toString();
        if (head.isEmpty()) return;
        String[] lines = head.split("\r\n");
        if (lines.length == 0) return;
        String[] parts = lines[0].split(" ");
        if (parts.length < 2) return;
        String method = parts[0];
        String rawPath = parts[1];

        String rangeHeader = null;
        for (String l : lines) {
            if (l.toLowerCase(Locale.US).startsWith("range:")) {
                rangeHeader = l.substring(6).trim();
                break;
            }
        }

        int qIdx = rawPath.indexOf('?');
        String path = qIdx >= 0 ? rawPath.substring(0, qIdx) : rawPath;
        Map<String, String> query = new HashMap<>();
        if (qIdx >= 0) {
            String qs = rawPath.substring(qIdx + 1);
            for (String kv : qs.split("&")) {
                int eq = kv.indexOf('=');
                if (eq > 0) {
                    try {
                        query.put(URLDecoder.decode(kv.substring(0, eq), "UTF-8"),
                                URLDecoder.decode(kv.substring(eq + 1), "UTF-8"));
                    } catch (Exception ignored) {}
                }
            }
        }

        try {
            if (method.equals("GET")) {
                route(out, path, query, rangeHeader);
            } else {
                simple(out, 405, "text/plain; charset=utf-8", "Method Not Allowed".getBytes("UTF-8"));
            }
        } catch (Exception e) {
            try {
                simple(out, 500, "text/plain; charset=utf-8", ("Server error: " + e).getBytes("UTF-8"));
            } catch (Exception ignored) {}
        }
    }

    private void route(OutputStream out, String path, Map<String, String> query, String range) throws IOException {
        if (path.equals("/") || path.equals("/index.html")) {
            byte[] body = HtmlPages.index().getBytes("UTF-8");
            simple(out, 200, "text/html; charset=utf-8", body);
        } else if (path.equals("/view.html")) {
            String p = query.getOrDefault("p", "");
            if (p.isEmpty()) p = getDefaultDir();
            byte[] body = HtmlPages.view(p).getBytes("UTF-8");
            simple(out, 200, "text/html; charset=utf-8", body);
        } else if (path.equals("/api/ping")) {
            byte[] body = "{\"ok\":true,\"app\":\"CarCamServer\",\"ver\":\"1.0.0\"}".getBytes("UTF-8");
            simple(out, 200, "application/json; charset=utf-8", body);
        } else if (path.equals("/api/scan")) {
            StringBuilder sb = new StringBuilder("{\"roots\":[");
            List<VideoScanner.DirStat> list = VideoScanner.scanAll(30);
            boolean first = true;
            for (VideoScanner.DirStat d : list) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{\"dir\":").append(json(d.path))
                        .append(",\"count\":").append(d.count)
                        .append(",\"size\":").append(d.totalSize)
                        .append(",\"sample\":").append(json(d.lastVideo)).append("}");
            }
            sb.append("]}");
            simple(out, 200, "application/json; charset=utf-8", sb.toString().getBytes("UTF-8"));
        } else if (path.equals("/api/list")) {
            String p = query.getOrDefault("p", "");
            byte[] body = listJson(p).getBytes("UTF-8");
            simple(out, 200, "application/json; charset=utf-8", body);
        } else if (path.startsWith("/video/")) {
            String rel = Util.dec(path.substring("/video/".length()));
            serveFile(out, rel, range);
        } else if (path.startsWith("/thumb/")) {
            String rel = Util.dec(path.substring("/thumb/".length()));
            serveThumb(out, rel);
        } else {
            simple(out, 404, "text/plain; charset=utf-8", "Not Found".getBytes("UTF-8"));
        }
    }

    private String getDefaultDir() {
        List<VideoScanner.DirStat> list = VideoScanner.scanAll(1);
        if (!list.isEmpty()) return list.get(0).path;
        return android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    private static String json(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ---------------- 目录列表 JSON ----------------

    private String listJson(String relPath) {
        File dir;
        if (relPath == null || relPath.isEmpty()) {
            dir = new File(getDefaultDir());
        } else {
            dir = new File(relPath);
        }
        StringBuilder sb = new StringBuilder("{\"dir\":");
        sb.append(json(dir.getAbsolutePath())).append(",\"entries\":[");
        File[] files = dir.listFiles();
        List<File> list = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (f.getName().startsWith(".")) continue;
                list.add(f);
            }
        }
        list.sort((a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        boolean first = true;
        for (File f : list) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"name\":").append(json(f.getName()))
                    .append(",\"path\":").append(json(f.getAbsolutePath()))
                    .append(",\"isDir\":").append(f.isDirectory())
                    .append(",\"size\":").append(f.length())
                    .append(",\"video\":").append(f.isFile() && Util.isVideo(f.getName()))
                    .append(",\"mtime\":").append(f.lastModified()).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    // ---------------- 视频文件流（支持 Range） ----------------

    private void serveFile(OutputStream out, String absPath, String rangeHeader) throws IOException {
        File f = new File(absPath);
        if (!f.isFile() || !f.canRead()) {
            simple(out, 404, "text/plain; charset=utf-8", "File not found".getBytes("UTF-8"));
            return;
        }
        long size = f.length();
        long start = 0;
        long end = size - 1;
        boolean partial = false;
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String r = rangeHeader.substring(6).trim();
            int dash = r.indexOf('-');
            if (dash > 0) {
                try {
                    start = Long.parseLong(r.substring(0, dash).trim());
                    String e2 = r.substring(dash + 1).trim();
                    if (!e2.isEmpty()) end = Math.min(Long.parseLong(e2), size - 1);
                    partial = true;
                } catch (NumberFormatException ignored) {}
            }
        }
        if (start >= size || start > end) {
            byte[] b = ("{\"error\":\"range\"}").getBytes("UTF-8");
            out.write(("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Type: application/json\r\nContent-Length: " + b.length + "\r\n\r\n").getBytes("UTF-8"));
            out.write(b);
            out.flush();
            return;
        }
        String ct = Util.contentType(f.getName());
        StringBuilder sb = new StringBuilder();
        if (partial) {
            sb.append("HTTP/1.1 206 Partial Content\r\n");
            sb.append("Content-Range: bytes ").append(start).append('-').append(end).append('/').append(size).append("\r\n");
        } else {
            sb.append("HTTP/1.1 200 OK\r\n");
        }
        sb.append("Content-Type: ").append(ct).append("\r\n")
          .append("Accept-Ranges: bytes\r\n")
          .append("Content-Length: ").append(end - start + 1).append("\r\n")
          .append("Connection: close\r\n")
          .append("Cache-Control: no-store\r\n\r\n");
        out.write(sb.toString().getBytes("UTF-8"));
        out.flush();
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "r")) {
            raf.seek(start);
            byte[] buf = new byte[64 * 1024];
            long remaining = end - start + 1;
            while (remaining > 0) {
                int n = raf.read(buf, 0, (int) Math.min(buf.length, remaining));
                if (n <= 0) break;
                out.write(buf, 0, n);
                remaining -= n;
            }
            out.flush();
        }
    }

    // ---------------- 缩略图 ----------------

    private void serveThumb(OutputStream out, String absPath) throws IOException {
        File f = new File(absPath);
        if (!f.isFile()) {
            simple(out, 404, "text/plain; charset=utf-8", "Not found".getBytes("UTF-8"));
            return;
        }
        byte[] jpg = thumbCache.get(absPath);
        if (jpg == null) {
            jpg = makeThumb(absPath);
            if (jpg != null) thumbCache.put(absPath, jpg);
        }
        if (jpg == null) {
            simple(out, 204, "image/jpeg", new byte[0]);
            return;
        }
        simple(out, 200, "image/jpeg", jpg);
    }

    private byte[] makeThumb(String absPath) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(absPath);
            Bitmap bmp = mmr.getScaledFrameAtTime(1_000_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 180);
            if (bmp == null) return null;
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 60, bos);
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            try { mmr.release(); } catch (Exception ignored) {}
        }
    }

    // ---------------- 简单响应 ----------------

    private void simple(OutputStream out, int code, String ct, byte[] body) throws IOException {
        String status = code == 200 ? "OK" : (code == 404 ? "Not Found" : (code == 405 ? "Method Not Allowed" : (code == 416 ? "Range Not Satisfiable" : (code == 204 ? "No Content" : "Internal Error"))));
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(code).append(' ').append(status).append("\r\n");
        sb.append("Content-Type: ").append(ct).append("\r\n");
        sb.append("Content-Length: ").append(body.length).append("\r\n");
        sb.append("Connection: close\r\n");
        sb.append("Access-Control-Allow-Origin: *\r\n\r\n");
        out.write(sb.toString().getBytes("UTF-8"));
        out.write(body);
        out.flush();
    }
}
