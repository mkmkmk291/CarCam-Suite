package com.carcam.server;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 工具类：IP 获取、路径编码等 */
public final class Util {

    private Util() {}

    /** 获取本机所有非回环 IPv4 地址（热点开启后通常会有 192.168.43.1 之类的地址） */
    public static List<String> getAllIpv4() {
        List<String> ips = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp()) continue;
                for (java.net.InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        ips.add(a.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {}
        // 把热点常见网段排前面
        ips.sort((x, y) -> {
            boolean bx = x.startsWith("192.168.43.") || x.startsWith("192.168.44.");
            boolean by = y.startsWith("192.168.43.") || y.startsWith("192.168.44.");
            if (bx && !by) return -1;
            if (by && !bx) return 1;
            return x.compareTo(y);
        });
        return ips;
    }

    /** 路径 -> Base64 URL 安全编码（用于 URL 参数，避免路径分隔符冲突） */
    public static String enc(String path) {
        return android.util.Base64.encodeToString(path.getBytes(),
                android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
    }

    public static String dec(String b64) {
        try {
            byte[] b = android.util.Base64.decode(b64,
                    android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
            return new String(b, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    /** 根据扩展名返回 Content-Type */
    public static String contentType(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".avi")) return "video/x-msvideo";
        if (n.endsWith(".mov")) return "video/quicktime";
        if (n.endsWith(".mkv")) return "video/x-matroska";
        if (n.endsWith(".ts") || n.endsWith(".m2ts")) return "video/mp2t";
        if (n.endsWith(".3gp")) return "video/3gpp";
        if (n.endsWith(".flv")) return "video/x-flv";
        if (n.endsWith(".wmv")) return "video/x-ms-wmv";
        if (n.endsWith(".m4v")) return "video/mp4";
        if (n.endsWith(".h264") || n.endsWith(".264")) return "video/h264";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".gif")) return "image/gif";
        return "application/octet-stream";
    }

    public static boolean isVideo(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".mp4") || n.endsWith(".avi") || n.endsWith(".mov")
                || n.endsWith(".mkv") || n.endsWith(".ts") || n.endsWith(".m2ts")
                || n.endsWith(".3gp") || n.endsWith(".flv") || n.endsWith(".wmv")
                || n.endsWith(".m4v") || n.endsWith(".h264") || n.endsWith(".264")
                || n.endsWith(".dat");
    }

    public static String formatSize(long s) {
        if (s < 1024) return s + " B";
        if (s < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", s / 1024.0);
        if (s < 1024L * 1024 * 1024) return String.format(java.util.Locale.US, "%.1f MB", s / 1048576.0);
        return String.format(java.util.Locale.US, "%.2f GB", s / 1073741824.0);
    }
}
