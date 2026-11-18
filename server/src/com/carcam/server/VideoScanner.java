package com.carcam.server;

import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 扫描车机存储，找出行车记录仪录像（视频文件） */
public final class VideoScanner {

    private static final int MAX_DEPTH = 7;
    private static final int MAX_FILES = 50000;

    public interface ScanLog {
        void log(String s);
    }

    private VideoScanner() {}

    /** 诊断厂商录像路径的存在性与可读性（用于排障日志） */
    public static String diagnoseVendorRoots() {
        StringBuilder sb = new StringBuilder();
        String[] vendor = {
                "/dvr",
                "/dvr/Video",
                "/DVR",
                "/DVR/Video",
                "/storage/emulated/0/DCIM/DVR",
                "/storage/emulated/0/DCIM/DVR/Record"
        };
        for (String c : vendor) {
            File f = new File(c);
            sb.append(c).append(" → ");
            if (!f.exists()) {
                sb.append("不存在");
            } else if (f.isDirectory()) {
                sb.append(f.canRead() ? "存在，可读" : "存在但【不可读 · 权限受限】");
            } else {
                sb.append("存在但不是目录");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /** 获取可作为扫描起点的根目录（内置存储 + 外置存储 + 厂商 DVR 路径 + 常见挂载点） */
    public static List<File> getStorageRoots() {
        Set<String> seen = new HashSet<>();
        List<File> roots = new ArrayList<>();
        // 厂商 DVR 路径（科鲁泽车机记录仪主录像在 /dvr/Video/，见反编译分析）
        String[] vendorRoots = {
                "/dvr",
                "/dvr/Video",
                "/DVR",
                "/DVR/Video",
                Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/DVR",
                "/storage/emulated/0/DCIM/DVR"
        };
        for (String c : vendorRoots) {
            File f = new File(c);
            if (f.isDirectory() && f.canRead() && seen.add(f.getAbsolutePath())) {
                roots.add(f);
            }
        }
        // 内置存储
        String[] candidates = {
                Environment.getExternalStorageDirectory().getAbsolutePath(), // /storage/emulated/0
                "/sdcard",
                "/storage/emulated/0",
                "/data/media/0",
                "/mnt/sdcard"
        };
        for (String c : candidates) {
            File f = new File(c);
            if (f.isDirectory() && f.canRead() && seen.add(f.getAbsolutePath())) {
                roots.add(f);
            }
        }
        // /storage 下的实际挂载点（外置 SD 卡等）
        try {
            File storage = new File("/storage");
            File[] subs = storage.listFiles();
            if (subs != null) {
                for (File s : subs) {
                    String n = s.getName();
                    if ("self".equals(n) || "emulated".equals(n)) continue;
                    if (s.isDirectory() && s.canRead() && seen.add(s.getAbsolutePath())) {
                        roots.add(s);
                    }
                }
            }
        } catch (Exception ignored) {}
        // /mnt 下的可读挂载点
        try {
            File mnt = new File("/mnt");
            File[] subs = mnt.listFiles();
            if (subs != null) {
                for (File s : subs) {
                    if (s.isDirectory() && s.canRead() && seen.add(s.getAbsolutePath())
                            && !s.getName().startsWith(".")) {
                        roots.add(s);
                    }
                }
            }
        } catch (Exception ignored) {}
        return roots;
    }

    /** 统计每个目录下视频文件数量与大小，返回按数量排序的目录列表 */
    public static List<DirStat> scanAll(int maxResults) {
        return scanAll(maxResults, null);
    }

    public static List<DirStat> scanAll(int maxResults, ScanLog logger) {
        List<File> roots = getStorageRoots();
        if (logger != null) {
            StringBuilder sb = new StringBuilder("扫描根目录: ");
            for (File r : roots) sb.append(r.getAbsolutePath()).append("  ");
            logger.log(sb.toString());
        }
        Map<String, DirStat> map = new HashMap<>();
        List<DirStat> all = new ArrayList<>();
        int[] counter = {0};
        for (File root : roots) {
            walk(root, 0, map, counter);
        }
        if (logger != null) {
            logger.log("扫描完成，共发现视频文件 " + counter[0] + " 个，涉及目录 " + map.size() + " 个");
            if (counter[0] == 0) {
                logger.log("未发现视频文件：可能录像存放在特殊目录/非标准格式，可尝试【浏览存储】手动查找");
            }
        }
        all.addAll(map.values());
        all.sort((a, b) -> {
            if (a.count != b.count) return Integer.compare(b.count, a.count);
            return Long.compare(b.totalSize, a.totalSize);
        });
        if (all.size() > maxResults) return all.subList(0, maxResults);
        return all;
    }

    private static void walk(File dir, int depth, Map<String, DirStat> map, int[] counter) {
        if (counter[0] >= MAX_FILES) return;
        if (depth > MAX_DEPTH) return;
        File[] files;
        try {
            files = dir.listFiles();
        } catch (Exception e) {
            return;
        }
        if (files == null) return;
        for (File f : files) {
            if (counter[0] >= MAX_FILES) return;
            String name = f.getName();
            if (name.startsWith(".")) continue;
            if (f.isDirectory()) {
                // 跳过系统/APP 数据目录与符号链接陷阱，避免扫无用文件
                if (depth <= 1 && (name.equals("Android") || name.equals("AppData")
                        || name.equals("self") || name.equals("emulated") || name.equals("obb"))) continue;
                walk(f, depth + 1, map, counter);
            } else if (f.isFile() && Util.isVideo(name)) {
                counter[0]++;
                File parent = f.getParentFile();
                DirStat st = map.get(parent.getAbsolutePath());
                if (st == null) {
                    st = new DirStat(parent.getAbsolutePath());
                    map.put(parent.getAbsolutePath(), st);
                }
                st.count++;
                st.totalSize += f.length();
                st.lastVideo = f.getAbsolutePath();
            }
        }
    }

    public static class DirStat {
        public final String path;
        public int count;
        public long totalSize;
        public String lastVideo;

        DirStat(String path) {
            this.path = path;
        }
    }
}
