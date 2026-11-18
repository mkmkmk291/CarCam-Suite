package com.carcam.server;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * 自动扫描新录像并上传到蓝奏云。
 *
 * 登录态：Cookie 只有约 3 天（phpdisk_info / lanzou_ifo 不滚动续期），
 * 所以保存加密凭据，到期（或 Cookie 读盘失败）时静默重走完整登录链路；
 * 失败后 5 分钟内不再重试，避免狂刷接口。
 */
public class AutoUploader {

    private static final String TAG = "AutoUploader";
    private static final String PREFS = "carcam_prefs";

    // 蓝奏云（保持与旧版一致的 key，升级后原有设置不丢）
    public static final String KEY_ENABLED = "lanzou_enabled";
    public static final String KEY_COOKIE = "lanzou_cookie";
    public static final String KEY_USER = "lanzou_username";
    /** 仅兼容旧版本明文密码，新版本读取走后 LanZouCreds 加密存储 */
    public static final String KEY_PASS = "lanzou_password";
    public static final String KEY_LAST_REFRESH = "lanzou_last_refresh";
    public static final String KEY_LAST_TRY = "lanzou_last_try";
    private static final String KEY_UPLOADED = "lanzou_uploaded";

    /** 3 天：phpdisk_info / lanzou_ifo / uag 的固定有效期 */
    private static final long REFRESH_INTERVAL = 3 * 24 * 60 * 60 * 1000L;
    /** 自动登录失败后的冷却时间 */
    private static final long RETRY_COOL_DOWN = 5 * 60 * 1000L;

    private final Context ctx;
    private Thread worker;
    private volatile boolean running;

    public AutoUploader(Context ctx) {
        this.ctx = ctx;
        migrateLegacyKeys();
    }

    public void start() {
        if (running) return;
        running = true;
        worker = new Thread(this::loop, "AutoUploader");
        worker.start();
    }

    public void stop() {
        running = false;
        if (worker != null) worker.interrupt();
    }

    /** 老版本（123 云盘方案遗留 / 早期蓝奏云 key）设置迁移 */
    private void migrateLegacyKeys() {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (!sp.getString(KEY_USER, "").isEmpty()) return;
            String user = sp.getString("pan123_username", "");
            if (user.isEmpty()) return;
            sp.edit()
                    .putString(KEY_USER, user)
                    .putString(KEY_PASS, sp.getString("pan123_password", ""))
                    .putString(KEY_COOKIE, sp.getString("pan123_cookie", ""))
                    .putLong(KEY_LAST_REFRESH, sp.getLong("pan123_last_refresh", 0))
                    .putBoolean(KEY_ENABLED, sp.getBoolean("pan123_enabled", false))
                    .apply();
            Log.i(TAG, "已迁移旧设置 key");
        } catch (Throwable ignored) {
        }
    }

    private void loop() {
        while (running) {
            try {
                Thread.sleep(120_000); // 每 2 分钟扫一次
            } catch (InterruptedException e) {
                return;
            }
            if (!running) return;
            try {
                tick();
            } catch (Exception e) {
                Log.e(TAG, "tick error: " + e.getMessage());
            }
        }
    }

    /** 到期或 Cookie 失效时静默重新登录；成功返回 true */
    public boolean refreshCookieIfNeeded(boolean force) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        String user = sp.getString(KEY_USER, "");
        String pass = "";
        String[] cred = LanZouCreds.load(ctx);
        if (cred != null) {
            if (user.isEmpty()) user = cred[0];
            pass = cred[1];
        } else {
            pass = sp.getString(KEY_PASS, ""); // 旧版明文兜底
        }
        if (user.isEmpty() || pass.isEmpty()) return false;

        long last = sp.getLong(KEY_LAST_REFRESH, 0);
        long lastTry = sp.getLong(KEY_LAST_TRY, 0);
        long now = System.currentTimeMillis();

        if (!force && now - last < REFRESH_INTERVAL) return false;
        // 失败冷却，避免每 2 分钟狂刷登录接口
        if (now - lastTry < RETRY_COOL_DOWN) return false;

        sp.edit().putLong(KEY_LAST_TRY, now).apply();
        Log.i(TAG, (force ? "Cookie 失效，立即重登…" : "Cookie 到期，自动重登…"));

        LanZouAuth.Result r = LanZouAuth.login(user, pass);
        if (r.ok) {
            sp.edit()
                    .putString(KEY_COOKIE, r.cookie)
                    .putLong(KEY_LAST_REFRESH, System.currentTimeMillis())
                    .apply();
            Log.i(TAG, "Cookie 刷新成功");
            return true;
        }
        Log.w(TAG, "自动登录失败: " + r.message);
        return false;
    }

    private void tick() {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!sp.getBoolean(KEY_ENABLED, false)) return;

        String cookie = sp.getString(KEY_COOKIE, "");
        if (cookie.isEmpty()) {
            // 从没登过 / 被清空：先登一次
            if (!refreshCookieIfNeeded(true)) return;
            cookie = sp.getString(KEY_COOKIE, "");
            if (cookie.isEmpty()) return;
        } else {
            refreshCookieIfNeeded(false);
            cookie = sp.getString(KEY_COOKIE, "");
        }

        if (!Guard.recheck()) {
            Log.e(TAG, "运行环境异常（调试/注入），本轮停止上传");
            return;
        }
        int uid = android.os.Process.myUid();
        Log.i(TAG, "运行身份 uid=" + uid + (uid == 1000 ? "（system，可读厂商私有目录）" : "（非 system，/dvr/Video 可能读不到）"));

        Set<String> uploaded = sp.getStringSet(KEY_UPLOADED, new HashSet<>());
        Set<String> newUploaded = new HashSet<>(uploaded);

        List<File> candidates = collectCandidates(sp);
        // 清理"文件已不存在"的记录，避免 prefs 无限膨胀。
        // 注意：扫描结果为空时一律不清 —— 目录暂时读不到、权限未授予时会误清，
        // 第二天再扫就会把历史录像全部重传一遍。
        if (!candidates.isEmpty()) {
            int pruned = 0;
            Iterator<String> it = newUploaded.iterator();
            while (it.hasNext()) {
                String k = it.next();
                String path = k.split("\\|", 2)[0];
                if (!new File(path).exists()) {
                    it.remove();
                    pruned++;
                }
            }
            if (pruned > 0) Log.i(TAG, "清理已删除文件的记录 " + pruned + " 条");
        }

        Log.i(TAG, "扫描到 " + candidates.size() + " 个可上传的录像文件");
        if (candidates.isEmpty()) return;

        LanZouClient client = new LanZouClient(sp.getString(KEY_COOKIE, ""), (pct, done, total) ->
                Log.d(TAG, "上传进度 " + pct + "% (" + done + "/" + total + ")"));
        client.endpoint = sp.getString(KEY_ENDPOINT, "/html5up.php");
        client.folderId = ensureFolderId(sp, client);

        for (File f : candidates) {
            String k = key(f);
            if (uploaded.contains(k)) continue;

            // 蓝奏云免费版单文件 100MB：超了就记下来，避免每 2 分钟重试一次
            if (f.length() > 100L * 1024 * 1024) {
                Log.w(TAG, "跳过（超过 100MB 免费版限制）: " + f.getName() + " " + f.length() + " bytes");
                newUploaded.add(k);
                sp.edit().putStringSet(KEY_UPLOADED, newUploaded).apply();
                continue;
            }

            Log.i(TAG, "上传: " + f.getAbsolutePath() + " (" + f.length() + " bytes)");
            boolean ok = client.upload(f);
            if (ok) {
                newUploaded.add(k);
                sp.edit().putStringSet(KEY_UPLOADED, newUploaded).apply();
                Log.i(TAG, "上传成功: " + f.getName());
                try { Thread.sleep(10000); } catch (InterruptedException ignored) { return; }
            } else {
                Log.w(TAG, "上传失败: " + f.getName() + "，下次重试");
                // 端点不存在时自动切到另一个（html5up.php <-> doupload.php）
                // 目标文件夹可能被改名/删除：清掉缓存，下次重新按名字定位
                if (client.lastError != null && client.lastError.contains("zt=0")
                        && !"-1".equals(client.folderId)) {
                    sp.edit().remove(KEY_FOLDER_ID).apply();
                    Log.w(TAG, "目标文件夹可能已变动（fol_id=" + client.folderId + "），下次重新定位");
                }
                if (client.lastError != null && client.lastError.contains("404")) {
                    String other = "/html5up.php".equals(client.endpoint) ? "/doupload.php" : "/html5up.php";
                    sp.edit().putString(KEY_ENDPOINT, other).apply();
                    client.endpoint = other;
                    Log.w(TAG, "上传端点 404，下次改用: " + other);
                }
                if (client.authExpired) {
                    // 登录态过期：立刻重登一次，本次不占用冷却
                    sp.edit().putLong(KEY_LAST_REFRESH, 0).putLong(KEY_LAST_TRY, 0).apply();
                    if (refreshCookieIfNeeded(true)) {
                        client = new LanZouClient(sp.getString(KEY_COOKIE, ""),
                                (pct, done, total) -> Log.d(TAG, "上传进度 " + pct + "%"));
                        client.endpoint = sp.getString(KEY_ENDPOINT, "/html5up.php");
                        client.folderId = ensureFolderId(sp, client);
                        if (client.upload(f)) {
                            newUploaded.add(k);
                            sp.edit().putStringSet(KEY_UPLOADED, newUploaded).apply();
                            Log.i(TAG, "重登后上传成功: " + f.getName());
                            continue;
                        }
                    }
                }
                try { Thread.sleep(15000); } catch (InterruptedException ignored) { return; }
            }
        }
    }

    /** 记住上次上传成功的端点 */
    public static final String KEY_ENDPOINT = "lanzou_endpoint";
    /** 上传目标文件夹：默认传到根目录下名为「记录仪」的文件夹 */
    public static final String KEY_FOLDER_NAME = "lanzou_folder_name";
    public static final String KEY_FOLDER_ID = "lanzou_folder_id";

    /** 取目标文件夹 fol_id：命中缓存直接用，否则按名字查一次并缓存；查不到退回根目录 */
    public static String ensureFolderId(SharedPreferences sp, LanZouClient client) {
        String cached = sp.getString(KEY_FOLDER_ID, "");
        if (!cached.isEmpty()) {
            client.folderId = cached;
            return cached;
        }
        String name = sp.getString(KEY_FOLDER_NAME, "记录仪");
        String fid = client.resolveFolderId(name);
        if (fid != null && !fid.isEmpty()) {
            sp.edit().putString(KEY_FOLDER_ID, fid).apply();
            client.folderId = fid;
            Log.i(TAG, "上传目标文件夹: " + name + " (fol_id=" + fid + ")");
            return fid;
        }
        Log.w(TAG, "根目录未找到文件夹 [" + name + "]，本次上传到根目录");
        client.folderId = "-1";
        return "-1";
    }

    /** 上传去重 key：路径 + 大小 + 修改时间，避免同名文件互相顶掉 */
    private static String key(File f) {
        return f.getAbsolutePath() + "|" + f.length() + "|" + f.lastModified();
    }

    /**
     * 收集候选文件：
     * 优先扫用户实际设置的录像目录（rootDir），再兜底厂商/常见路径；
     * 递归进日期子目录（车机录像通常按天分文件夹）。
     */
    private List<File> collectCandidates(SharedPreferences sp) {
        List<String> roots = new ArrayList<>();
        String cfg = CamServerService.defaultDir;
        if (cfg == null || cfg.isEmpty()) cfg = sp.getString("rootDir", null);
        if (cfg != null && !cfg.isEmpty()) {
            // 用户/服务里指定了录像目录，只扫这一处
            roots.add(cfg);
        } else {
            // 同时扫大小写两种目录（Linux 大小写敏感）
            roots.add("/dvr/Video");
            roots.add("/DVR/Video");
        }
        // 兜底目录（手机、或录像放 DCIM 的机型）
        File ext = Environment.getExternalStorageDirectory();
        roots.add("/dvr");
        roots.add("/DVR");
        roots.add(new File(ext, "DCIM/DVR/Record").getAbsolutePath());
        roots.add(new File(ext, "DCIM/DVR/Video").getAbsolutePath());
        roots.add(new File(ext, "DCIM/DVR").getAbsolutePath());
        Log.i(TAG, "扫描目录: " + roots);

        Set<String> seen = new HashSet<>();
        List<File> out = new ArrayList<>();
        for (String r : roots) {
            walk(new File(r), 0, seen, out);
        }
        out.sort((a, b) -> Long.compare(a.lastModified(), b.lastModified())); // 老的先传
        return out;
    }

    private void walk(File dir, int depth, Set<String> seen, List<File> out) {
        if (depth > 4) return;
        if (!dir.isDirectory() || !dir.canRead()) {
            if (depth == 0) Log.w(TAG, "目录不存在或不可读: " + dir.getAbsolutePath());
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            if (depth == 0) Log.w(TAG, "目录读取失败（权限受限）: " + dir.getAbsolutePath());
            return;
        }
        long now = System.currentTimeMillis();
        for (File f : files) {
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".")) walk(f, depth + 1, seen, out);
                continue;
            }
            if (!f.isFile()) continue;
            String n = f.getName().toLowerCase();
            if (!(n.endsWith(".mp4") || n.endsWith(".jpg"))) continue;
            if (f.length() <= 0) continue;
            // 正在录制的文件（最后修改时间 5 分钟内）跳过，避免传半截文件
            if (now - f.lastModified() < 5 * 60 * 1000L) continue;
            if (!seen.add(f.getAbsolutePath())) continue;
            out.add(f);
        }
    }
}
