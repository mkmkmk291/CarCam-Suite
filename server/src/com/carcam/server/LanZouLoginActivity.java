package com.carcam.server;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Bundle;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * 蓝奏云 WebView 备用登录页。
 *
 * 主登录走 LanZouAuth（账号密码纯接口）；这里只在接口登录失败时兜底：
 * 打开 accounts.woozooo.com 登录页（注意不是 pc.woozooo.com，那个域名打开是空白），
 * 自动填账号密码并提交，登录成功后把 accounts + pc 两个域的 Cookie 合并保存。
 */
public class LanZouLoginActivity extends Activity {

    private static final String TAG = "LanZouLogin";
    private static final String LOGIN_URL =
            "https://accounts.woozooo.com/accounts.php?action=login&ref=pc.woozooo.com";
    private static final String PC_URL = "https://pc.woozooo.com/mydisk.php";

    private WebView webView;
    private String autoUser, autoPass;
    private boolean autoSubmitted = false;
    private boolean saved = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("蓝奏云登录（备用）");

        SharedPreferences temp = getSharedPreferences(CamServerService.PREFS, MODE_PRIVATE);
        autoUser = temp.getString("temp_login_user", "");
        autoPass = temp.getString("temp_login_pass", "");
        temp.edit().remove("temp_login_user").remove("temp_login_pass").apply();
        if (autoUser.isEmpty()) {
            String[] cred = LanZouCreds.load(this);
            if (cred != null) {
                autoUser = cred[0];
                autoPass = cred[1];
            }
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));

        Button btnDone = new Button(this);
        btnDone.setText("✅ 登录完成，保存 Cookie");
        btnDone.setOnClickListener(v -> saveCookieAndFinish());
        root.addView(btnDone);

        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUserAgentString(LanZouAuth.UA);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                Log.d(TAG, "页面加载: " + url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                CookieManager.getInstance().flush();
                Log.d(TAG, "页面加载完成: " + url);

                if (!autoSubmitted && !autoUser.isEmpty() && !autoPass.isEmpty()) {
                    autoSubmitted = true;
                    Toast.makeText(LanZouLoginActivity.this, "正在自动填写账号密码…", Toast.LENGTH_SHORT).show();
                    webView.postDelayed(() -> fillAndSubmit(), 1500);
                }

                // 已进入网盘页面 = 登录成功
                if (url.contains("mydisk") || url.contains("disk.php")) {
                    Toast.makeText(LanZouLoginActivity.this, "检测到登录成功！", Toast.LENGTH_SHORT).show();
                    saveCookieAndFinish();
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }
        });

        root.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        setContentView(root);

        CookieManager.getInstance().removeAllCookies(null);
        webView.loadUrl(LOGIN_URL);
    }

    private void fillAndSubmit() {
        String user = autoUser.replace("\\", "\\\\").replace("'", "\\'");
        String pass = autoPass.replace("\\", "\\\\").replace("'", "\\'");
        String js = "javascript:(function(){"
                + "var inputs=document.querySelectorAll('input');"
                + "var u=null,p=null;"
                + "for(var i=0;i<inputs.length;i++){"
                + "  var inp=inputs[i],t=(inp.type||'').toLowerCase(),n=(inp.name||'').toLowerCase();"
                + "  if(!u&&(t==='text'||t==='email'||n.indexOf('user')>=0||n.indexOf('name')>=0||n.indexOf('account')>=0)){u=inp;}"
                + "  if(!p&&(t==='password'||n.indexOf('pass')>=0)){p=inp;}"
                + "}"
                + "if(u&&p){"
                + "  u.focus();u.value='" + user + "';u.dispatchEvent(new Event('input'));"
                + "  p.focus();p.value='" + pass + "';p.dispatchEvent(new Event('input'));"
                + "  var f=u.closest('form');"
                + "  if(f){f.submit();}else{"
                + "    var btn=document.querySelector('button[type=submit],input[type=submit],.login-btn,.btn-login');"
                + "    if(btn)btn.click();"
                + "  }"
                + "}})()";
        webView.evaluateJavascript(js, null);
    }

    private void saveCookieAndFinish() {
        if (saved) return;
        try {
            CookieManager cm = CookieManager.getInstance();
            String cookie1 = cm.getCookie("https://accounts.woozooo.com");
            String cookie2 = cm.getCookie("https://pc.woozooo.com");
            String cookie = mergeCookies(cookie1, cookie2);

            if (cookie == null || cookie.isEmpty()) {
                Toast.makeText(this, "未获取到 Cookie", Toast.LENGTH_LONG).show();
                return;
            }

            // 蓝奏云真正鉴权 Cookie：phpdisk_info / lanzou_ifo（web_uid 只在部分接口用到）
            boolean authed = cookie.contains("phpdisk_info") || cookie.contains("lanzou_ifo")
                    || (!cookie.contains("web_uid") && cookie.contains("PHPSESSID"));
            if (!authed) {
                Toast.makeText(this, "似乎还没登录成功", Toast.LENGTH_LONG).show();
                return;
            }

            SharedPreferences sp = getSharedPreferences(CamServerService.PREFS, Context.MODE_PRIVATE);
            sp.edit()
                    .putString(AutoUploader.KEY_COOKIE, cookie)
                    .putLong(AutoUploader.KEY_LAST_REFRESH, System.currentTimeMillis())
                    .putLong(AutoUploader.KEY_LAST_TRY, System.currentTimeMillis())
                    .apply();
            if (!autoUser.isEmpty() && !autoPass.isEmpty()) {
                sp.edit().putString(AutoUploader.KEY_USER, autoUser).apply();
                LanZouCreds.save(this, autoUser, autoPass);
            }
            saved = true;
            Toast.makeText(this, "✅ 登录状态已保存", Toast.LENGTH_LONG).show();
            Log.i(TAG, "Cookie 已保存，长度=" + cookie.length());
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String mergeCookies(String c1, String c2) {
        if (c1 == null || c1.isEmpty()) return c2;
        if (c2 == null || c2.isEmpty()) return c1;
        StringBuilder sb = new StringBuilder();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String c : new String[]{c1, c2}) {
            for (String part : c.split(";")) {
                part = part.trim();
                if (!part.isEmpty() && !seen.contains(part)) {
                    seen.add(part);
                    if (sb.length() > 0) sb.append("; ");
                    sb.append(part);
                }
            }
        }
        return sb.toString();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
