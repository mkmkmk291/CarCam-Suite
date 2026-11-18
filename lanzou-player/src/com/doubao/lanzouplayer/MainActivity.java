package com.doubao.lanzouplayer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import com.doubao.lanzouplayer.MiniHttpServer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/* loaded from: /home/user/Doubao/chats/38450769749427714/lanzou_player_analysis/apk_content/classes.dex */
public class MainActivity extends Activity {
    private static final String KEY_ALIAS = "lz_cred";
    private WebChromeClient chrome;
    private LanzouClient client;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private View fullscreenView;
    private FrameLayout root;
    private MiniHttpServer server;
    private WebView web;

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        this.root = new FrameLayout(this);
        WebView webView = new WebView(this);
        this.web = webView;
        this.root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(this.root);
        WebSettings settings = this.web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setSupportZoom(false);
        settings.setTextZoom(100);
        CookieManager.getInstance().setAcceptCookie(true);
        this.web.setWebViewClient(new WebViewClient());
        WebChromeClient webChromeClient = new WebChromeClient() { // from class: com.doubao.lanzouplayer.MainActivity.1
            @Override // android.webkit.WebChromeClient
            public void onShowCustomView(View view, WebChromeClient.CustomViewCallback customViewCallback) {
                if (MainActivity.this.fullscreenView == null) {
                    MainActivity.this.fullscreenView = view;
                    MainActivity.this.fullscreenCallback = customViewCallback;
                    MainActivity.this.root.addView(view, new FrameLayout.LayoutParams(-1, -1));
                    MainActivity.this.web.setVisibility(8);
                    return;
                }
                customViewCallback.onCustomViewHidden();
            }

            @Override // android.webkit.WebChromeClient
            public void onHideCustomView() {
                if (MainActivity.this.fullscreenView == null) {
                    return;
                }
                MainActivity.this.root.removeView(MainActivity.this.fullscreenView);
                MainActivity.this.fullscreenView = null;
                MainActivity.this.web.setVisibility(0);
                if (MainActivity.this.fullscreenCallback != null) {
                    MainActivity.this.fullscreenCallback.onCustomViewHidden();
                    MainActivity.this.fullscreenCallback = null;
                }
            }
        };
        this.chrome = webChromeClient;
        this.web.setWebChromeClient(webChromeClient);
        this.web.setDownloadListener(new DownloadListener() { // from class: com.doubao.lanzouplayer.MainActivity.2
            @Override // android.webkit.DownloadListener
            public void onDownloadStart(String str, String str2, String str3, String str4, long j) {
                Toast.makeText(MainActivity.this, "正在调用系统浏览器下载，请保持本应用在后台运行", 1).show();
                try {
                    Intent intent = new Intent("android.intent.action.VIEW", Uri.parse(str));
                    intent.addFlags(268435456);
                    MainActivity.this.startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "没有可用的浏览器", 0).show();
                }
            }
        });
        LanzouClient lanzouClient = new LanzouClient();
        this.client = lanzouClient;
        lanzouClient.loadCookies(readCookieFile());
        String[] readCredential = readCredential();
        if (readCredential != null) {
            this.client.setAutoLogin(readCredential[0], readCredential[1]);
        }
        new Thread(new Runnable() { // from class: com.doubao.lanzouplayer.MainActivity.3
            @Override // java.lang.Runnable
            public void run() {
                final int i = -1;
                for (int i2 = 8765; i2 <= 8785 && i < 0; i2++) {
                    MiniHttpServer miniHttpServer = new MiniHttpServer(i2, MainActivity.this.client, new MiniHttpServer.Assets() { // from class: com.doubao.lanzouplayer.MainActivity.3.1
                        @Override // com.doubao.lanzouplayer.MiniHttpServer.Assets
                        public byte[] read(String str) throws IOException {
                            return MainActivity.this.readAsset(str);
                        }
                    });
                    try {
                        i = miniHttpServer.start();
                        MainActivity.this.server = miniHttpServer;
                        miniHttpServer.setAuthListener(new MiniHttpServer.AuthListener() { // from class: com.doubao.lanzouplayer.MainActivity.3.2
                            @Override // com.doubao.lanzouplayer.MiniHttpServer.AuthListener
                            public void onLogin(String str, String str2) {
                                MainActivity.this.saveCredential(str, str2);
                                MainActivity.this.client.setAutoLogin(str, str2);
                            }

                            @Override // com.doubao.lanzouplayer.MiniHttpServer.AuthListener
                            public void onLogout() {
                                MainActivity.this.clearCredential();
                                MainActivity.this.client.clearAutoLogin();
                            }
                        });
                    } catch (IOException e) {
                    }
                }
                if (i < 0) {
                    MainActivity.this.runOnUiThread(new Runnable() { // from class: com.doubao.lanzouplayer.MainActivity.3.3
                        @Override // java.lang.Runnable
                        public void run() {
                            Toast.makeText(MainActivity.this, "本地服务启动失败", 1).show();
                        }
                    });
                } else {
                    MainActivity.this.runOnUiThread(new Runnable() { // from class: com.doubao.lanzouplayer.MainActivity.3.4
                        @Override // java.lang.Runnable
                        public void run() {
                            MainActivity.this.web.loadUrl("http://127.0.0.1:" + i + "/");
                        }
                    });
                }
            }
        }, "lanzou-server").start();
    }

    private SecretKey credentialKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES", "AndroidKeyStore");
            keyGenerator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, 3).setBlockModes("GCM").setEncryptionPaddings("NoPadding").setKeySize(256).build());
            return keyGenerator.generateKey();
        }
        return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void saveCredential(String str, String str2) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(1, credentialKey());
            byte[] iv = cipher.getIV();
            byte[] doFinal = cipher.doFinal((str + "\n" + str2).getBytes("UTF-8"));
            FileOutputStream fileOutputStream = new FileOutputStream(new File(getFilesDir(), "cred.bin"));
            fileOutputStream.write(iv);
            fileOutputStream.write(doFinal);
            fileOutputStream.close();
        } catch (Exception e) {
        }
    }

    private String[] readCredential() {
        File file = new File(getFilesDir(), "cred.bin");
        if (!file.exists()) {
            return null;
        }
        try {
            int length = (int) file.length();
            byte[] bArr = new byte[length];
            FileInputStream fileInputStream = new FileInputStream(file);
            int i = 0;
            while (i < length) {
                int read = fileInputStream.read(bArr, i, length - i);
                if (read <= 0) {
                    break;
                }
                i += read;
            }
            fileInputStream.close();
            byte[] bArr2 = new byte[12];
            System.arraycopy(bArr, 0, bArr2, 0, 12);
            int i2 = length - 12;
            byte[] bArr3 = new byte[i2];
            System.arraycopy(bArr, 12, bArr3, 0, i2);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(2, credentialKey(), new GCMParameterSpec(128, bArr2));
            String str = new String(cipher.doFinal(bArr3), "UTF-8");
            int indexOf = str.indexOf(10);
            if (indexOf <= 0) {
                return null;
            }
            return new String[]{str.substring(0, indexOf), str.substring(indexOf + 1)};
        } catch (Exception e) {
            return null;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void clearCredential() {
        new File(getFilesDir(), "cred.bin").delete();
    }

    private String readCookieFile() {
        File file = new File(getFilesDir(), "cookies.txt");
        if (!file.exists()) {
            return null;
        }
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            int length = (int) file.length();
            byte[] bArr = new byte[length];
            int i = 0;
            while (i < length) {
                int read = fileInputStream.read(bArr, i, length - i);
                if (read <= 0) {
                    break;
                }
                i += read;
            }
            fileInputStream.close();
            return new String(bArr, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private void saveCookies(LanzouClient lanzouClient) {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(new File(getFilesDir(), "cookies.txt"));
            fileOutputStream.write(lanzouClient.dumpCookies().getBytes("UTF-8"));
            fileOutputStream.close();
        } catch (Exception e) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public byte[] readAsset(String str) throws IOException {
        InputStream inputStream = null;
        try {
            inputStream = getAssets().open(str);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] bArr = new byte[8192];
            while (true) {
                int read = inputStream.read(bArr);
                if (read <= 0) {
                    break;
                }
                byteArrayOutputStream.write(bArr, 0, read);
            }
            return byteArrayOutputStream.toByteArray();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                }
            }
        }
    }

    @Override // android.app.Activity
    public void onBackPressed() {
        if (this.fullscreenView != null) {
            this.chrome.onHideCustomView();
            return;
        }
        WebView webView = this.web;
        if (webView != null && webView.canGoBack()) {
            this.web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override // android.app.Activity
    protected void onPause() {
        LanzouClient lanzouClient = this.client;
        if (lanzouClient != null) {
            saveCookies(lanzouClient);
        }
        super.onPause();
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        MiniHttpServer miniHttpServer = this.server;
        if (miniHttpServer != null) {
            miniHttpServer.stop();
        }
        WebView webView = this.web;
        if (webView != null) {
            webView.loadUrl("about:blank");
            this.web.destroy();
            this.web = null;
        }
        super.onDestroy();
    }
}
