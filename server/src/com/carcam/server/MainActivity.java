package com.carcam.server;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 车机端控制界面（手机开热点方案）：
 *  1) 启动/停止 HTTP 录像服务器
 *  2) 扫描并选择行车记录仪录像目录
 *  3) 显示当前已连接的网络与 IP（车机需先连接手机热点）
 */
public class MainActivity extends Activity {

    private static final Handler H = new Handler(Looper.getMainLooper());
    private static TextView logView;
    private static final StringBuilder logBuf = new StringBuilder();
    private TextView statusView;
    private Button btnStart, btnStop, btnScan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 反调试 / 反脱壳自检：环境异常直接退出，避免被调试器或注入框架脱壳
        if (!Guard.install(this)) {
            toast("运行环境异常（调试器/注入/签名不符），已停止");
            finish();
            return;
        }
        setTitle("车机录像服务器");
        buildUi();
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
        }
        // 启动时录像目录默认 /dvr/Video（未设置过则写入默认值，全流程统一）
        SharedPreferences sp0 = getSharedPreferences(CamServerService.PREFS, MODE_PRIVATE);
        if (sp0.getString("rootDir", "").isEmpty()) {
            sp0.edit().putString("rootDir", "/dvr/Video").apply();
        }
        CamServerService.defaultDir = sp0.getString("rootDir", "/dvr/Video");
        // 打开 App 即自动开启录像服务器（无需手动点启动）
        if (!CamServerService.running) {
            startServer();
            android.util.Log.i("MainActivity", "启动 App，自动开启录像服务器");
        }
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Guard.recheck()) {
            toast("运行环境异常（调试/注入），已停止");
            finish();
            return;
        }
        refreshStatus();
    }

    private void startServer() {
        Intent i = new Intent(this, CamServerService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView hint = new TextView(this);
        hint.setTextSize(13);
        hint.setText("使用步骤：\n① 手机开启热点（个人热点）\n② 车机在 Wi-Fi 设置中连接手机热点\n③ 打开 App 会自动启动服务器（也可手动点启动），确认下方显示热点分配的 IP\n④ 手机打开「车机录像查看器」→ 自动扫描即可查看\n\n（车机连接手机热点后双方在同一局域网；手机热点仅用于传输录像，不消耗流量）");
        hint.setTextColor(0xFFE2E8F0);
        root.addView(hint);

        statusView = new TextView(this);
        statusView.setTextSize(14);
        statusView.setPadding(0, dp(10), 0, dp(4));
        root.addView(statusView);

        btnStart = button("启动录像服务器");
        btnStop = button("停止录像服务器");
        btnScan = button("扫描录像目录");
        btnStart.setOnClickListener(v -> {
            if (CamServerService.running) {
                toast("服务已在运行");
                return;
            }
            startServer();
            refreshStatus();
        });
        btnStop.setOnClickListener(v -> {
            stopService(new Intent(this, CamServerService.class));
            refreshStatus();
        });
        btnScan.setOnClickListener(v -> scanDirs());
        root.addView(btnStart);
        root.addView(btnStop);
        root.addView(btnScan);

        Button btnBrowse = button("浏览存储找录像（手动选目录）");
        btnBrowse.setOnClickListener(v -> openBrowser());
        root.addView(btnBrowse);

        Button btnRefresh = button("刷新状态");
        btnRefresh.setOnClickListener(v -> refreshStatus());
        root.addView(btnRefresh);

        Button btnCloud = button("☁ 蓝奏云自动备份设置");
        btnCloud.setOnClickListener(v -> showCloudSettings());
        root.addView(btnCloud);

        logView = new TextView(this);
        logView.setTextSize(11);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setMovementMethod(new ScrollingMovementMethod());
        logView.setPadding(0, dp(8), 0, 0);
        logView.setText(logBuf.toString());
        root.addView(logView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(300)));

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        return b;
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    private void refreshStatus() {
        boolean running = CamServerService.running;
        int port = CamServerService.getPort(this);
        List<String> ips = Util.getAllIpv4();
        StringBuilder sb = new StringBuilder();
        sb.append("服务状态：").append(running ? "● 运行中" : "○ 已停止").append("\n");
        sb.append("端口：").append(port).append("\n");
        sb.append("已连接 Wi-Fi：").append(getCurrentSsid()).append("\n");
        sb.append("本机 IP（热点分配）：\n");
        for (String ip : ips) sb.append("  ").append(ip).append("\n");
        if (ips.isEmpty()) {
            sb.append("  （暂无 IP —— 请先在车机 Wi-Fi 设置里连接手机热点）\n");
        }
        sb.append("手机访问地址：\n");
        for (String ip : ips) sb.append("  http://").append(ip).append(":").append(port).append("\n");
        sb.append("录像目录：").append(CamServerService.defaultDir == null ? "/dvr/Video（默认）" : CamServerService.defaultDir);
        statusView.setText(sb.toString());
    }

    private String getCurrentSsid() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm == null) return "未知";
            String ssid = wm.getConnectionInfo().getSSID();
            if (ssid == null || ssid.contains("<unknown") || ssid.isEmpty()) {
                return "（无法读取，不影响使用）";
            }
            return ssid;
        } catch (Exception e) {
            return "（无法读取）";
        }
    }

    private void scanDirs() {
        appendLog(VideoScanner.diagnoseVendorRoots());
        appendLog("正在扫描录像目录…");
        new Thread(() -> {
            final List<VideoScanner.DirStat> dirs = VideoScanner.scanAll(30, s -> appendLog(s));
            H.post(() -> {
                if (dirs.isEmpty()) {
                    toast("未找到录像文件。可点【浏览存储找录像】手动选择目录");
                    return;
                }
                String[] names = new String[dirs.size()];
                for (int i = 0; i < dirs.size(); i++) {
                    VideoScanner.DirStat d = dirs.get(i);
                    names[i] = d.path + "\n  " + d.count + " 个视频 · " + Util.formatSize(d.totalSize);
                }
                new AlertDialog.Builder(this)
                        .setTitle("选择录像目录")
                        .setItems(names, (dialog, which) -> {
                            String dir = dirs.get(which).path;
                            CamServerService.defaultDir = dir;
                            getSharedPreferences(CamServerService.PREFS, MODE_PRIVATE)
                                    .edit().putString("rootDir", dir).apply();
                            appendLog("录像目录已设为: " + dir);
                            refreshStatus();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });
        }).start();
    }

    public static void appendLog(String s) {
        H.post(() -> {
            if (logBuf.length() > 6000) {
                logBuf.delete(0, logBuf.length() - 4000);
            }
            logBuf.append(java.text.SimpleDateFormat.getTimeInstance(java.text.SimpleDateFormat.MEDIUM)
                    .format(new java.util.Date())).append("  ").append(s).append("\n");
            if (logView != null) {
                logView.setText(logBuf.toString());
            }
        });
    }

    // ---------------- 手动浏览存储，逐级找录像 ----------------

    private AlertDialog dirDialog;
    private TextView dirPathText;
    private ArrayAdapter<String> dirAdapter;
    private final List<String> dirItems = new ArrayList<>();
    private final List<String[]> dirEntries = new ArrayList<>(); // {label, path, "dir"/"video"}
    private File currentDir;

    private void openBrowser() {
        // 从根目录开始，方便直接找到厂商录像目录 /dvr
        File start = new File("/");
        if (!start.isDirectory()) start = new File("/storage");
        if (!start.isDirectory()) start = new File("/storage/emulated/0");
        browseTo(start);
    }

    private void browseTo(final File dir) {
        if (!dir.isDirectory()) {
            toast("无法访问该目录");
            return;
        }
        currentDir = dir;
        final File target = dir;
        appendLog("浏览: " + target.getAbsolutePath());
        new Thread(() -> {
            final List<String[]> entries = new ArrayList<>();
            final boolean[] denied = {false};
            File[] subs;
            try {
                subs = target.listFiles();
            } catch (Exception e) {
                subs = null;
            }
            if (subs == null) {
                denied[0] = true;
            } else {
                for (File f : subs) {
                    String n = f.getName();
                    if (n.startsWith(".")) continue;
                    try {
                        if (f.isDirectory()) {
                            entries.add(new String[]{"📁 " + n, f.getAbsolutePath(), "dir"});
                        } else if (f.isFile() && Util.isVideo(n)) {
                            entries.add(new String[]{"▶ " + n, f.getAbsolutePath(), "video"});
                        }
                    } catch (Exception ignored) {}
                }
            }
            final List<String[]> sorted = entries;
            sorted.sort((a, b) -> {
                if (!a[2].equals(b[2])) return a[2].equals("dir") ? -1 : 1;
                return a[0].compareTo(b[0]);
            });
            H.post(() -> {
                if (dirDialog == null) buildDirDialog();
                dirPathText.setText("当前目录: " + target.getAbsolutePath());
                dirItems.clear();
                dirItems.add("✔ 选择【当前目录】为录像目录");
                dirEntries.clear();
                for (String[] e : sorted) {
                    dirItems.add(e[0]);
                    dirEntries.add(e);
                }
                if (denied[0]) {
                    dirItems.add("（⚠ 无权限读取此目录，被系统保护）");
                } else if (sorted.isEmpty()) {
                    dirItems.add("（此目录无子目录/视频）");
                }
                dirAdapter.notifyDataSetChanged();
                dirDialog.show();
            });
        }).start();
    }

    private void buildDirDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(10), dp(16), 0);
        dirPathText = new TextView(this);
        dirPathText.setTextSize(12);
        box.addView(dirPathText);
        ListView lv = new ListView(this);
        lv.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        dirAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, dirItems);
        lv.setAdapter(dirAdapter);
        box.addView(lv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(360)));
        dirDialog = new AlertDialog.Builder(this)
                .setTitle("浏览存储 · 找到录像目录")
                .setView(box)
                .setPositiveButton("返回上级", (d, w) -> {
                    File upDir = currentDir == null ? null : currentDir.getParentFile();
                    if (upDir == null) upDir = new File("/");
                    browseTo(upDir);
                })
                .setNegativeButton("关闭", null)
                .create();
        lv.setOnItemClickListener((parent, view, position, id) -> {
            if (position == 0) {
                if (currentDir == null) return;
                CamServerService.defaultDir = currentDir.getAbsolutePath();
                getSharedPreferences(CamServerService.PREFS, MODE_PRIVATE)
                        .edit().putString("rootDir", currentDir.getAbsolutePath()).apply();
                appendLog("录像目录已设为: " + currentDir.getAbsolutePath());
                refreshStatus();
                toast("已选择该目录为录像目录");
                return;
            }
            int idx = position - 1;
            if (idx < 0 || idx >= dirEntries.size()) return;
            String[] e = dirEntries.get(idx);
            if ("dir".equals(e[2])) {
                browseTo(new File(e[1]));
            } else {
                // 视频文件 → 直接选中其所在目录
                File vidParent = new File(e[1]).getParentFile();
                if (vidParent != null) {
                    CamServerService.defaultDir = vidParent.getAbsolutePath();
                    getSharedPreferences(CamServerService.PREFS, MODE_PRIVATE)
                            .edit().putString("rootDir", vidParent.getAbsolutePath()).apply();
                    appendLog("录像目录已设为: " + vidParent.getAbsolutePath());
                    refreshStatus();
                    toast("已选择 " + vidParent.getName() + " 为录像目录");
                }
            }
        });
    }

    private void showCloudSettings() {
        final android.content.SharedPreferences sp = getSharedPreferences(CamServerService.PREFS, MODE_PRIVATE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(10), dp(16), 0);

        final android.widget.CheckBox cbEnable = new android.widget.CheckBox(this);
        cbEnable.setText("开启自动上传到蓝奏云");
        cbEnable.setChecked(sp.getBoolean(AutoUploader.KEY_ENABLED, false));
        box.addView(cbEnable);

        TextView tvUser = new TextView(this);
        tvUser.setText("蓝奏云账号（邮箱/手机）");
        box.addView(tvUser);
        final android.widget.EditText etUser = new android.widget.EditText(this);
        etUser.setText(sp.getString(AutoUploader.KEY_USER, ""));
        box.addView(etUser);

        TextView tvPass = new TextView(this);
        tvPass.setText("蓝奏云密码");
        box.addView(tvPass);
        final android.widget.EditText etPass = new android.widget.EditText(this);
        etPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        String[] saved = LanZouCreds.load(this);
        if (saved != null) {
            if (etUser.getText().toString().trim().isEmpty()) etUser.setText(saved[0]);
            etPass.setText(saved[1]);
        }
        box.addView(etPass);

        final TextView tvState = new TextView(this);
        tvState.setTextSize(12);
        tvState.setPadding(0, dp(6), 0, dp(6));
        tvState.setText(loginStateText(sp));
        box.addView(tvState);

        final Button btnLogin = new Button(this);
        btnLogin.setText("🔐 登录（账号密码 · 纯接口）");
        btnLogin.setOnClickListener(v -> {
            final String user = etUser.getText().toString().trim();
            final String pass = etPass.getText().toString().trim();
            if (user.isEmpty() || pass.isEmpty()) { toast("请先输入账号密码"); return; }
            btnLogin.setEnabled(false);
            tvState.setText("正在登录（过 CDN → 提交 → 鉴权 → 自检）…");
            new Thread(() -> {
                final LanZouAuth.Result r = LanZouAuth.login(user, pass);
                runOnUiThread(() -> {
                    btnLogin.setEnabled(true);
                    if (r.ok) {
                        sp.edit()
                                .putString(AutoUploader.KEY_USER, user)
                                .putString(AutoUploader.KEY_COOKIE, r.cookie)
                                .putLong(AutoUploader.KEY_LAST_REFRESH, System.currentTimeMillis())
                                .putLong(AutoUploader.KEY_LAST_TRY, System.currentTimeMillis())
                                .remove(AutoUploader.KEY_PASS)
                                .apply();
                        LanZouCreds.save(MainActivity.this, user, pass);
                        tvState.setText("✅ 登录成功，Cookie 已保存（有效期约 3 天，失效自动重登）");
                        appendLog("蓝奏云登录成功，Cookie 已保存");
                        toast("✅ 登录成功");
                    } else {
                        tvState.setText("❌ " + r.message);
                        toast("❌ " + r.message);
                    }
                });
            }).start();
        });
        box.addView(btnLogin);

        Button btnWeb = new Button(this);
        btnWeb.setText("（备用）网页手动登录");
        btnWeb.setOnClickListener(v -> {
            sp.edit()
                    .putString("temp_login_user", etUser.getText().toString().trim())
                    .putString("temp_login_pass", etPass.getText().toString().trim())
                    .apply();
            startActivity(new Intent(this, LanZouLoginActivity.class));
        });
        box.addView(btnWeb);

        final Button btnTest = new Button(this);
        btnTest.setText("🧪 测试上传（1MB 测试文件）");
        btnTest.setOnClickListener(v -> {
            if (sp.getString(AutoUploader.KEY_COOKIE, "").isEmpty()) {
                toast("请先登录，再测试上传");
                return;
            }
            btnTest.setEnabled(false);
            tvState.setText("正在测试上传…");
            new Thread(() -> {
                long t0 = System.currentTimeMillis();
                boolean ok = false;
                String detail = "";
                final String[] reasonBox = new String[]{""};
                java.io.File tmp = new java.io.File(getCacheDir(),
                        "上传测试_" + System.currentTimeMillis() + ".mp4");
                try {
                    byte[] buf = new byte[1024 * 1024];
                    new java.util.Random().nextBytes(buf);
                    java.io.FileOutputStream fo = new java.io.FileOutputStream(tmp);
                    fo.write(buf);
                    fo.close();

                    LanZouClient c = new LanZouClient(sp.getString(AutoUploader.KEY_COOKIE, ""), null);
                    String fid = AutoUploader.ensureFolderId(sp, c);
                    detail = "-1".equals(fid)
                            ? "（目标：根目录，未找到「" + sp.getString(AutoUploader.KEY_FOLDER_NAME, "记录仪") + "」文件夹）"
                            : "（目标文件夹：" + sp.getString(AutoUploader.KEY_FOLDER_NAME, "记录仪") + " fol_id=" + fid + "）";
                    ok = c.upload(tmp);
                    if (!ok) reasonBox[0] = c.lastError;
                    if (!ok && !c.authExpired) {
                        // 换另一个端点再试一次（html5up.php <-> doupload.php）
                        String first = reasonBox[0];
                        String other = "/html5up.php".equals(c.endpoint) ? "/doupload.php" : "/html5up.php";
                        LanZouClient c2 = new LanZouClient(sp.getString(AutoUploader.KEY_COOKIE, ""), null);
                        c2.endpoint = other;
                        c2.folderId = c.folderId;
                        if (c2.upload(tmp)) {
                            ok = true;
                            sp.edit().putString(AutoUploader.KEY_ENDPOINT, other).apply();
                            detail = detail + "（改用 " + other + " 成功，已记住该端点）";
                        } else {
                            reasonBox[0] = "端点 " + c.endpoint + "：\n" + first
                                    + "\n\n端点 " + other + "：\n" + c2.lastError;
                        }
                    }
                    if (!ok && c.authExpired) {
                        AutoUploader up = new AutoUploader(MainActivity.this);
                        if (up.refreshCookieIfNeeded(true)) {
                            c = new LanZouClient(sp.getString(AutoUploader.KEY_COOKIE, ""), null);
                            ok = c.upload(tmp);
                            if (!ok) reasonBox[0] = c.lastError;
                            detail = ok ? "（重登后成功）" : "（重登后仍失败）";
                        } else {
                            detail = "（自动重登失败）";
                        }
                    }
                } catch (Exception e) {
                    detail = "：" + e.getMessage();
                } finally {
                    tmp.delete();
                }
                final boolean fok = ok;
                final String fdetail = detail;
                final long cost = System.currentTimeMillis() - t0;
                runOnUiThread(() -> {
                    btnTest.setEnabled(true);
                    if (fok) {
                        tvState.setText(loginStateText(sp));
                        appendLog("上传测试成功，用时 " + cost + "ms" + fdetail);
                        toast("✅ 上传链路正常（" + Math.max(1, cost / 1000) + " 秒）");
                    } else {
                        String ck = sp.getString(AutoUploader.KEY_COOKIE, "");
                        String ckState = ck.contains("phpdisk_info") ? "有 phpdisk_info"
                                : (ck.contains("lanzou_ifo") ? "有 lanzou_ifo" : "未取到鉴权 Cookie");
                        tvState.setText("❌ 上传测试失败，原因见弹窗");
                        appendLog("上传测试失败：" + reasonBox[0]);
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("上传测试失败")
                                .setMessage("失败原因：\n" + (reasonBox[0].isEmpty() ? "（无详细信息）" : reasonBox[0])
                                        + "\n\n耗时 " + cost + " ms"
                                        + "\n登录 Cookie：" + ckState
                                        + fdetail)
                                .setPositiveButton("知道了", null)
                                .show();
                    }
                });
            }).start();
        });
        box.addView(btnTest);

        TextView tvNote = new TextView(this);
        tvNote.setTextSize(11);
        tvNote.setText("说明：\n· 点【登录】即用账号密码走蓝奏云 PC 端接口，登录后自动保存 Cookie\n"
                + "· Cookie 约 3 天失效，App 会自动重新登录，不用手动管\n"
                + "· 账号密码用系统 Keystore 加密存储\n"
                + "· 上传目标：根目录下名为「记录仪」的文件夹（找不到则传到根目录，日志会提示）\n"
                + "· 蓝奏云免费版单文件限 100MB；接口登录失败时可用 WebView 备用登录");
        box.addView(tvNote);

        new AlertDialog.Builder(this)
                .setTitle("蓝奏云自动备份")
                .setView(box)
                .setPositiveButton("保存设置", (d, w) -> {
                    String user = etUser.getText().toString().trim();
                    String pass = etPass.getText().toString().trim();
                    sp.edit()
                            .putBoolean(AutoUploader.KEY_ENABLED, cbEnable.isChecked())
                            .putString(AutoUploader.KEY_USER, user)
                            .apply();
                    if (!user.isEmpty() && !pass.isEmpty()) {
                        LanZouCreds.save(MainActivity.this, user, pass);
                    }
                    toast("已保存");
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private String loginStateText(android.content.SharedPreferences sp) {
        String cookie = sp.getString(AutoUploader.KEY_COOKIE, "");
        long last = sp.getLong(AutoUploader.KEY_LAST_REFRESH, 0);
        if (cookie.isEmpty()) return "当前状态：未登录";
        String when = last == 0 ? "未知"
                : android.text.format.DateFormat.format("MM-dd HH:mm", last).toString();
        return "当前状态：已有 Cookie（上次登录 " + when + "）";
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
