package com.carcam.viewer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 网络与工具方法 */
public final class Util {

    public static class ServerInfo {
        public String ip;
        public int port;
        public String name;
    }

    private Util() {}

    /** 当前 Wi-Fi 下的本机 IPv4（用于推导子网） */
    public static String getLocalIpv4() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp()) continue;
                if (!ni.getName().toLowerCase().startsWith("wlan")) continue;
                for (java.net.InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
            // 兜底：任何非回环 IPv4
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp()) continue;
                for (java.net.InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    /** 扫描多个子网前缀（如 "192.168.43."、"192.168.31."）下监听 port 端口的服务器 */
    public static List<ServerInfo> scanServers(List<String> prefixes, int port, final Util.Callback cb) {
        final List<ServerInfo> found = Collections.synchronizedList(new ArrayList<ServerInfo>());
        String local = getLocalIpv4();
        final String localHost = local.isEmpty() ? "" : local.substring(local.lastIndexOf('.') + 1);
        // 汇总所有待探测主机并去重
        java.util.LinkedHashSet<String> hostSet = new java.util.LinkedHashSet<>();
        for (String pre : prefixes) {
            for (int i = 1; i <= 254; i++) hostSet.add(pre + i);
        }
        final List<String> hosts = new ArrayList<>(hostSet);
        ExecutorService pool = Executors.newFixedThreadPool(64);
        final CountDownLatch latch = new CountDownLatch(hosts.size());
        for (final String host : hosts) {
            pool.execute(() -> {
                try {
                    if (!localHost.isEmpty() && host.endsWith("." + localHost)) return;
                    URL u = new URL("http://" + host + ":" + port + "/api/ping");
                    HttpURLConnection c = (HttpURLConnection) u.openConnection();
                    c.setConnectTimeout(500);
                    c.setReadTimeout(500);
                    c.setRequestMethod("GET");
                    int code = c.getResponseCode();
                    if (code == 200) {
                        InputStream in = c.getInputStream();
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        byte[] buf = new byte[512];
                        int n;
                        int total = 0;
                        while ((n = in.read(buf)) > 0 && total < 4096) {
                            bos.write(buf, 0, n);
                            total += n;
                        }
                        String body = new String(bos.toByteArray(), "UTF-8");
                        ServerInfo si = new ServerInfo();
                        int idx = host.lastIndexOf(':');
                        si.ip = host;
                        si.port = port;
                        if (body.contains("CarCamServer")) {
                            si.name = "车机录像服务器";
                        } else {
                            si.name = "HTTP 服务";
                        }
                        found.add(si);
                        if (cb != null) cb.onFound(si);
                    }
                    c.disconnect();
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}
        pool.shutdownNow();
        List<ServerInfo> res = new ArrayList<>(found);
        Collections.sort(res, (a, b) -> a.ip.compareTo(b.ip));
        return res;
    }

    public interface Callback {
        void onFound(ServerInfo si);
    }

    /** GET 返回文本，失败返回 null */
    public static String httpGet(String url, int timeoutMs) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            int code = c.getResponseCode();
            if (code != 200) return null;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    /** 下载文件到目标路径，返回 true 表示成功（带进度回调） */
    public interface ProgressCallback {
        void onProgress(int percent, long downloaded, long total);
    }

    public static boolean download(String url, File dest, ProgressCallback cb) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(30000);
            int code = c.getResponseCode();
            if (code != 200) return false;
            int total = c.getContentLength();
            InputStream in = c.getInputStream();
            OutputStream out = new FileOutputStream(dest);
            byte[] buf = new byte[65536];
            int n;
            long downloaded = 0;
            int lastPercent = -1;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                downloaded += n;
                if (cb != null) {
                    int pct = total > 0 ? (int)(downloaded * 100 / total) : 0;
                    if (pct != lastPercent) {
                        lastPercent = pct;
                        cb.onProgress(pct, downloaded, total);
                    }
                }
            }
            out.close();
            in.close();
            c.disconnect();
            if (cb != null) cb.onProgress(100, downloaded, total > 0 ? total : downloaded);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 下载文件（无进度回调，兼容旧调用） */
    public static boolean download(String url, File dest) {
        return download(url, dest, null);
    }

    /** 视频 URL 编码 */
    public static String videoUrl(String base, String path) {
        String b64 = android.util.Base64.encodeToString(path.getBytes(),
                android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
        return base + "/video/" + b64;
    }

    public static String formatSize(long s) {
        if (s < 1024) return s + " B";
        if (s < 1048576) return String.format(java.util.Locale.US, "%.1f KB", s / 1024.0);
        if (s < 1073741824L) return String.format(java.util.Locale.US, "%.1f MB", s / 1048576.0);
        return String.format(java.util.Locale.US, "%.2f GB", s / 1073741824.0);
    }
}
