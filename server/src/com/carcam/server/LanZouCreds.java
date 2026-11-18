package com.carcam.server;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * 蓝奏云账号密码的安全存储。
 *
 * 主方案：Android Keystore 里的 AES-256-GCM 密钥加密后写入 App 私有目录（filesDir/lz_cred.bin）。
 * 兜底：个别车机 Keystore 不可用（加密失败）时，退回原来的明文 SharedPreferences，保证功能不中断。
 */
public class LanZouCreds {

    private static final String TAG = "LanZouCreds";
    private static final String FILE = "lz_cred.bin";
    private static final String KEY_ALIAS = "carcam_lz_cred";
    private static final String PREFS = "carcam_prefs";
    /** 明文兜底用的 key（只在 Keystore 加密失败时才写入） */
    private static final String FALLBACK_PASS = "lanzou_password";
    private static final String FALLBACK_USER = "lanzou_username";

    /** 保存账号密码；成功返回 true */
    public static synchronized boolean save(Context ctx, String user, String pass) {
        if (user == null || pass == null) return false;
        try {
            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] ct = cipher.doFinal((user + "\n" + pass).getBytes("UTF-8"));
            byte[] iv = cipher.getIV();

            File f = new File(ctx.getFilesDir(), FILE);
            FileOutputStream out = new FileOutputStream(f);
            out.write((Base64.encodeToString(iv, Base64.NO_WRAP) + "\n"
                    + Base64.encodeToString(ct, Base64.NO_WRAP)).getBytes("UTF-8"));
            out.close();

            // 加密成功，清掉可能残留的明文
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            sp.edit().remove(FALLBACK_PASS).apply();
            Log.i(TAG, "账号密码已加密保存");
            return true;
        } catch (Throwable e) {
            Log.e(TAG, "加密保存失败，回退明文: " + e);
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            sp.edit().putString(FALLBACK_USER, user).putString(FALLBACK_PASS, pass).apply();
            return false;
        }
    }

    /** 读取账号密码；没有则返回 null */
    public static synchronized String[] load(Context ctx) {
        try {
            File f = new File(ctx.getFilesDir(), FILE);
            if (f.exists()) {
                FileInputStream in = new FileInputStream(f);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                in.close();
                String text = bos.toString("UTF-8").trim();
                int nl = text.indexOf('\n');
                if (nl > 0) {
                    byte[] iv = Base64.decode(text.substring(0, nl).trim(), Base64.NO_WRAP);
                    byte[] ct = Base64.decode(text.substring(nl + 1).trim(), Base64.NO_WRAP);
                    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                    cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
                    String plain = new String(cipher.doFinal(ct), "UTF-8");
                    int p = plain.indexOf('\n');
                    if (p > 0) return new String[]{plain.substring(0, p), plain.substring(p + 1)};
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, "解密读取失败: " + e);
        }
        // 兜底：明文
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String u = sp.getString(FALLBACK_USER, "");
        String p = sp.getString(FALLBACK_PASS, "");
        if (!u.isEmpty() && !p.isEmpty()) return new String[]{u, p};
        return null;
    }

    /** 退出登录 / 重置时清空凭据 */
    public static synchronized void clear(Context ctx) {
        try {
            File f = new File(ctx.getFilesDir(), FILE);
            if (f.exists()) f.delete();
        } catch (Throwable ignored) {
        }
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .remove(FALLBACK_USER).remove(FALLBACK_PASS).apply();
        } catch (Throwable ignored) {
        }
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) {
            return (SecretKey) ks.getKey(KEY_ALIAS, null);
        }
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build();
        kg.init(spec);
        return kg.generateKey();
    }
}
