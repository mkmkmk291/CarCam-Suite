package com.carcam.server;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Debug;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.RandomAccessFile;
import java.security.MessageDigest;

/**
 * 反调试 / 反脱壳自检。
 * 检测到调试器附加、ptrace 跟踪、注入框架（Frida/Xposed）或签名被替换，返回 false，调用方应停止运行。
 * 说明：这是 Java 层防线，能挡住"直接 jadx + 动态调试 + 重打包"这类低成本破解，
 * 挡不住专业脱壳（内存 dump / 定制 VMP），真正的高强度加固要接 360/腾讯的壳。
 */
public final class Guard {

    /** 平台签名证书 SHA-256（与原厂记录仪 APK 同一把 AOSP 测试平台密钥） */
    private static final String EXPECT_SHA256 =
            "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8";

    private static volatile boolean checked;

    /** @return true=环境干净；false=检测到异常，调用方应停止服务/退出界面 */
    public static boolean install(Context ctx) {
        if (checked) return true;
        checked = true;
        try {
            if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
                return die("调试器已连接");
            }
            if (tracerPid() != 0) {
                return die("进程被 ptrace 跟踪");
            }
            if (!signatureOk(ctx)) {
                return die("签名校验失败（APK 被重新打包或换了签名）");
            }
            if (injected()) {
                return die("检测到注入框架（Frida/Xposed）");
            }
            return true;
        } catch (Throwable t) {
            return die("自检异常: " + t);
        }
    }

    /** 运行期复检（不重复做签名校验）：每轮上传前调一次，抓"先运行再附加调试器"的脱壳方式 */
    public static boolean recheck() {
        try {
            if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) return die("调试器已连接");
            if (tracerPid() != 0) return die("进程被 ptrace 跟踪");
            if (injected()) return die("检测到注入框架（Frida/Xposed）");
            return true;
        } catch (Throwable t) {
            return true;   // 复检异常不误杀，交给启动时那次严格校验
        }
    }

    private static boolean die(String why) {
        Log.e("Guard", "安全检查失败: " + why);
        return false;
    }

    private static int tracerPid() {
        try {
            RandomAccessFile f = new RandomAccessFile("/proc/self/status", "r");
            try {
                String line;
                while ((line = f.readLine()) != null) {
                    if (line.startsWith("TracerPid:")) {
                        return Integer.parseInt(line.substring(10).trim());
                    }
                }
            } finally {
                f.close();
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static boolean injected() {
        // 1) 进程内存映射里出现注入框架的库
        try {
            RandomAccessFile f = new RandomAccessFile("/proc/self/maps", "r");
            try {
                String line;
                while ((line = f.readLine()) != null) {
                    String l = line.toLowerCase();
                    if (l.contains("frida") || l.contains("xposed") || l.contains("substrate")
                            || l.contains("libhook") || l.contains("lief")) return true;
                }
            } finally {
                f.close();
            }
        } catch (Throwable ignored) {
        }
        // 2) 常见注入文件落地位置
        String[] paths = {
                "/data/local/tmp/frida-server",
                "/data/local/tmp/re.frida.server",
                "/data/local/tmp/libfrida-gadget.so",
                "/sdcard/frida-server"
        };
        for (String p : paths) {
            try {
                if (new File(p).exists()) return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static boolean signatureOk(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), PackageManager.GET_SIGNATURES);
            if (pi == null || pi.signatures == null || pi.signatures.length == 0) return false;
            for (Signature s : pi.signatures) {
                if (EXPECT_SHA256.equalsIgnoreCase(sha256(s.toByteArray()))) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(data);
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
