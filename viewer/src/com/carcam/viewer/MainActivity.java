package com.carcam.viewer;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** 手机端首页：输入 IP 或自动扫描，找到车机录像服务器 */
public class MainActivity extends Activity {

    private static final Handler H = new Handler(Looper.getMainLooper());
    private static final String PREFS = "carcam_viewer";

    private EditText ipInput, portInput;
    private Button btnConnect, btnScan;
    private TextView hint, scanState;
    private ListView serverList;
    private List<String> listItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("车机录像查看器");
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
        }
        buildUi();
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        ipInput.setText(sp.getString("ip", ""));
        portInput.setText(String.valueOf(sp.getInt("port", 8080)));
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(14);
        root.setPadding(p, p, p, p);

        hint = new TextView(this);
        hint.setText("第一步：手机开启热点（个人热点），让车机 Wi-Fi 连接它\n第二步：车机端启动「车机录像服务器」\n第三步：点击【自动扫描】或在下方手动输入车机 IP\n（车机 IP 显示在车机端 App 首页，如 192.168.43.x）");
        hint.setTextSize(13);
        hint.setPadding(0, 0, 0, dp(10));
        root.addView(hint);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        ipInput = new EditText(this);
        ipInput.setHint("车机 IP（如 192.168.43.1）");
        ipInput.setInputType(InputType.TYPE_CLASS_PHONE);
        row.addView(ipInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2));

        portInput = new EditText(this);
        portInput.setHint("端口");
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        row.addView(portInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        btnConnect = new Button(this);
        btnConnect.setText("连接");
        btnConnect.setAllCaps(false);
        btnConnect.setOnClickListener(v -> connectManually());
        row.addView(btnConnect, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(row);

        btnScan = new Button(this);
        btnScan.setText("自动扫描（本机热点网段 + 常见网段）");
        btnScan.setAllCaps(false);
        btnScan.setOnClickListener(v -> startScan());
        root.addView(btnScan);

        scanState = new TextView(this);
        scanState.setTextSize(12);
        scanState.setPadding(0, dp(6), 0, 0);
        root.addView(scanState);

        serverList = new ListView(this);
        serverList.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, listItems));
        serverList.setOnItemClickListener((parent, view, position, id) -> {
            String item = listItems.get(position);
            String[] parts = item.split(" ");
            if (parts.length >= 2) openServer(parts[0], Integer.parseInt(parts[1]));
        });
        root.addView(serverList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    private void connectManually() {
        String ip = ipInput.getText().toString().trim();
        String portStr = portInput.getText().toString().trim();
        if (ip.isEmpty()) {
            toast("请输入车机 IP");
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            toast("端口无效");
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("ip", ip).putInt("port", port).apply();
        openServer(ip, port);
    }

    private void openServer(String ip, int port) {
        Intent i = new Intent(this, FileListActivity.class);
        i.putExtra("base", "http://" + ip + ":" + port);
        startActivity(i);
    }

    private void startScan() {
        scanState.setText("正在扫描…（本机热点网段 + 常见网段，最多约 15 秒）");
        listItems.clear();
        ((ArrayAdapter<?>) serverList.getAdapter()).notifyDataSetChanged();
        final int port;
        try {
            port = Integer.parseInt(portInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            scanState.setText("端口无效");
            return;
        }
        // 扫描网段：本机所在子网优先，再补常见手机热点网段
        final java.util.List<String> prefixes = new java.util.ArrayList<>();
        String localIp = Util.getLocalIpv4();
        if (!localIp.isEmpty()) {
            prefixes.add(localIp.substring(0, localIp.lastIndexOf('.') + 1));
        }
        String[] common = {"192.168.43.", "192.168.31.", "192.168.137.", "192.168.0.", "192.168.1.", "172.20.10."};
        for (String c : common) {
            if (!prefixes.contains(c)) prefixes.add(c);
        }
        new Thread(() -> {
            List<Util.ServerInfo> servers = Util.scanServers(prefixes, port, si ->
                    H.post(() -> {
                        String s = si.ip + " " + si.port + " · " + si.name;
                        if (!listItems.contains(s)) {
                            listItems.add(s);
                            ((ArrayAdapter<?>) serverList.getAdapter()).notifyDataSetChanged();
                        }
                    }));
            H.post(() -> {
                if (servers.isEmpty()) {
                    scanState.setText("未找到服务器。请确认：\n1) 手机热点已开启，且车机已连接该热点；\n2) 车机端 App 已点【启动录像服务器】并显示热点分配的 IP；\n3) 若车机 IP 不在常见网段，可在上方手动输入车机端显示的 IP");
                } else {
                    scanState.setText("找到 " + servers.size() + " 个服务器，点击下方条目进入");
                }
            });
        }).start();
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
