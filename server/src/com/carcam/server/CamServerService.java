package com.carcam.server;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;

import java.io.File;
import java.io.IOException;

/** 前台服务：运行 HTTP 录像服务器 */
public class CamServerService extends Service implements HttpServer.Log {

    private static final String CHANNEL_ID = "carcam_server";
    public static final String PREFS = "carcam_prefs";
    public static final String KEY_PORT = "port";
    public static final int DEFAULT_PORT = 8080;

    private HttpServer http;
    private int port = DEFAULT_PORT;
    private AutoUploader autoUploader;

    /** 用户手动指定的录像根目录；null 表示自动识别 */
    public static volatile String defaultDir;

    public static boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();
        if (!Guard.install(this)) {
            running = false;
            stopSelf();
            return;
        }
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        port = sp.getInt(KEY_PORT, DEFAULT_PORT);
        String dir = sp.getString("rootDir", "");
        if (dir.isEmpty()) {
            // 自动检测哪个目录存在（大小写都试）
            if (new File("/dvr/Video").isDirectory()) {
                dir = "/dvr/Video";
            } else if (new File("/DVR/Video").isDirectory()) {
                dir = "/DVR/Video";
            } else {
                dir = "/dvr/Video"; // 默认
            }
            sp.edit().putString("rootDir", dir).apply();
            log("自动检测录像目录: " + dir);
        }
        defaultDir = dir;
        running = true;
        startForeground(1, buildNotification());
        http = new HttpServer(port, this);
        try {
            http.start();
        } catch (IOException e) {
            log("启动失败: " + e.getMessage());
            stopSelf();
        }
        autoUploader = new AutoUploader(this);
        autoUploader.start();
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "行车记录仪服务",
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, 0);
        Notification.Builder b = android.os.Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setContentTitle("车机录像服务器运行中")
                .setContentText("手机连接热点后访问 http://" + Util.getAllIpv4() + ":" + port)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Override
    public void log(String s) {
        MainActivity.appendLog(s);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (http != null) http.stop();
        if (autoUploader != null) autoUploader.stop();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static int getPort(Context c) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_PORT, DEFAULT_PORT);
    }
}
