package com.carcam.viewer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 浏览车机录像目录，点击播放，长按/按钮下载 */
public class FileListActivity extends Activity {

    private static final Handler H = new Handler(Looper.getMainLooper());
    private static final ExecutorService pool = Executors.newFixedThreadPool(4);

    private String base;
    private String currentDir = "";
    private TextView pathView;
    private LinearLayout listBox;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        base = getIntent().getStringExtra("base");
        setTitle("录像文件");
        buildUi();
        loadDir("");
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        pathView = new TextView(this);
        pathView.setTextSize(12);
        pathView.setPadding(dp(12), dp(10), dp(12), dp(4));
        root.addView(pathView);

        scroll = new ScrollView(this);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listBox);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    private void loadDir(final String dir) {
        currentDir = dir;
        listBox.removeAllViews();
        addItem("⬆ 返回上级目录", null, false, dir.isEmpty() ? null : "up");
        pathView.setText("路径: " + (dir.isEmpty() ? "/（车机自动识别目录）" : dir));

        new Thread(() -> {
            final String json = Util.httpGet(base + "/api/list?p=" + urlEncode(dir), 8000);
            H.post(() -> {
                if (json == null) {
                    addItem("❌ 加载失败（服务器未运行或网络不通）", null, false, null);
                    return;
                }
                try {
                    JSONObject obj = new JSONObject(json);
                    JSONArray arr = obj.getJSONArray("entries");
                    List<String[]> dirs = new ArrayList<>();
                    List<String[]> vids = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject e = arr.getJSONObject(i);
                        String name = e.getString("name");
                        String path = e.getString("path");
                        boolean isDir = e.getBoolean("isDir");
                        boolean isVideo = e.optBoolean("video", false);
                        long size = e.getLong("size");
                        if (isDir) {
                            dirs.add(new String[]{name, path});
                        } else if (isVideo) {
                            vids.add(new String[]{name, path, Util.formatSize(size)});
                        }
                    }
                    for (String[] d : dirs) addItem("📁 " + d[0], d[1], true, "dir");
                    for (String[] v : vids) addVideoItem(v[0], v[1], v[2]);
                    if (dirs.isEmpty() && vids.isEmpty()) {
                        addItem("（此目录无内容）", null, false, null);
                    }
                } catch (Exception e) {
                    addItem("❌ 数据解析失败: " + e.getMessage(), null, false, null);
                }
            });
        }).start();
    }

    private void addItem(String label, final String path, boolean clickable, String tag) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(15);
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        t.setTextColor(0xFFE2E8F0);
        if (clickable) {
            t.setBackgroundResource(android.R.drawable.list_selector_background);
        }
        if (path != null) {
            if ("dir".equals(tag)) {
                t.setOnClickListener(v -> loadDir(path));
            } else if ("up".equals(tag)) {
                t.setOnClickListener(v -> {
                    int i = currentDir.lastIndexOf('/');
                    loadDir(i > 0 ? currentDir.substring(0, i) : "");
                });
            }
        }
        listBox.addView(t);
    }

    private void addVideoItem(final String name, final String path, String sizeText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(6), dp(12), dp(6));
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        final ImageView thumb = new ImageView(this);
        thumb.setLayoutParams(new LinearLayout.LayoutParams(dp(96), dp(54)));
        thumb.setBackgroundColor(0xFF334155);
        row.addView(thumb);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView tv = new TextView(this);
        tv.setText("▶ " + name);
        tv.setTextSize(14);
        tv.setTextColor(0xFF7DD3FC);
        col.addView(tv);
        TextView meta = new TextView(this);
        meta.setText(sizeText);
        meta.setTextSize(11);
        meta.setTextColor(0xFF94A3B8);
        col.addView(meta);
        row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView dl = new TextView(this);
        dl.setText("下载");
        dl.setTextColor(0xFF34D399);
        dl.setTextSize(14);
        dl.setPadding(dp(10), dp(4), dp(10), dp(4));
        row.addView(dl);

        row.setOnClickListener(v -> {
            Intent i = new Intent(this, PlayerActivity.class);
            i.putExtra("url", Util.videoUrl(base, path));
            i.putExtra("name", name);
            startActivity(i);
        });
        dl.setOnClickListener(v -> download(path, name));
        listBox.addView(row);

        // 异步加载缩略图
        pool.execute(() -> {
            final Bitmap bmp = fetchThumb(path);
            H.post(() -> {
                if (bmp != null) thumb.setImageBitmap(bmp);
            });
        });
    }

    private Bitmap fetchThumb(String path) {
        try {
            String b64 = Base64.encodeToString(path.getBytes(),
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            HttpURLConnection c = (HttpURLConnection) new URL(base + "/thumb/" + b64).openConnection();
            c.setConnectTimeout(4000);
            c.setReadTimeout(4000);
            int code = c.getResponseCode();
            if (code != 200) return null;
            InputStream in = c.getInputStream();
            Bitmap b = BitmapFactory.decodeStream(in);
            in.close();
            c.disconnect();
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private void download(final String path, final String name) {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null || !dir.exists()) dir = Environment.getExternalStorageDirectory();
        final File dest = new File(dir, sanitize(name));

        final android.app.ProgressDialog pd = new android.app.ProgressDialog(this);
        pd.setTitle("下载录像");
        pd.setMessage(name);
        pd.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
        pd.setMax(100);
        pd.setProgress(0);
        pd.setCancelable(false);
        pd.show();

        new Thread(() -> {
            boolean ok = Util.download(Util.videoUrl(base, path), dest, (percent, downloaded, total) ->
                H.post(() -> {
                    pd.setProgress(percent);
                    String sizeText = total > 0
                        ? Util.formatSize(downloaded) + " / " + Util.formatSize(total)
                        : Util.formatSize(downloaded);
                    pd.setMessage(name + "\n" + sizeText + "  (" + percent + "%)");
                }));
            H.post(() -> {
                pd.dismiss();
                Toast.makeText(this, ok ? "已保存到 " + dest.getAbsolutePath() : "下载失败",
                        Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private static String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    static String formatSize(long s) {
        if (s < 1024) return s + " B";
        if (s < 1048576) return String.format(java.util.Locale.US, "%.1f KB", s / 1024.0);
        if (s < 1073741824L) return String.format(java.util.Locale.US, "%.1f MB", s / 1048576.0);
        return String.format(java.util.Locale.US, "%.2f GB", s / 1073741824.0);
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
