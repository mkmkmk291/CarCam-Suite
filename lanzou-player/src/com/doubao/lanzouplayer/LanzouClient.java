package com.doubao.lanzouplayer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/* loaded from: /home/user/Doubao/chats/38450769749427714/lanzou_player_analysis/apk_content/classes.dex */
public class LanzouClient {
    private static final String MASK = "3000176000856006061501533003690027800375";
    public static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private String autoPass;
    private String autoUser;
    private long lastAutoTry;
    private static final int[] POS = {15, 35, 29, 24, 33, 16, 1, 38, 10, 9, 19, 31, 40, 27, 22, 23, 25, 13, 6, 11, 39, 18, 20, 8, 14, 21, 32, 26, 2, 30, 7, 4, 17, 5, 3, 28, 34, 37, 12, 36};
    private static final String[] VIDEO_EXT = {"mp4", "m4v", "webm", "mov", "mkv", "avi", "flv", "ts", "mpg", "mpeg", "wmv", "rmvb", "rm", "3gp", "ogv", "m2ts"};
    private static final String[] PLAYABLE_EXT = {"mp4", "m4v", "webm", "mov", "ogv", "ts"};
    private static final String[] IMAGE_EXT = {"jpg", "jpeg", "png", "gif", "webp", "bmp"};
    private final CookieJar jar = new CookieJar();
    private final Map<String, String[]> streamCache = new HashMap();
    private String account = "";

    /* loaded from: /home/user/Doubao/chats/38450769749427714/lanzou_player_analysis/apk_content/classes.dex */
    public static class Browse {
        public String folderId;
        public List<Crumbs> crumbs = new ArrayList();
        public List<Item> folders = new ArrayList();
        public List<Item> files = new ArrayList();
        public int page = 1;
        public int pages = 1;
    }

    /* loaded from: /home/user/Doubao/chats/38450769749427714/lanzou_player_analysis/apk_content/classes.dex */
    public static class Item {
        public String id;
        public boolean locked;
        public String mime;
        public String name;
        public boolean playable;
        public String type;
        public String ext = "";
        public String size = "";
        public String time = "";
        public String downs = "0";
        public String desc = "";
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: /home/user/Doubao/chats/38450769749427714/lanzou_player_analysis/apk_content/classes.dex */
    public static class CookieJar {
        private final Map<String, Map<String, String>> byDomain = new HashMap();

        CookieJar() {
        }

        private static String norm(String str) {
            String lowerCase = str.toLowerCase();
            while (lowerCase.startsWith(".")) {
                lowerCase = lowerCase.substring(1);
            }
            return lowerCase;
        }

        synchronized void set(String str, String str2, String str3) {
            String norm = norm(str);
            Map<String, String> map = this.byDomain.get(norm);
            if (map == null) {
                map = new HashMap<>();
                this.byDomain.put(norm, map);
            }
            map.put(str2, str3);
        }

        synchronized String header(URL url) {
            StringBuilder sb;
            String norm = norm(url.getHost());
            sb = new StringBuilder();
            for (Map.Entry<String, Map<String, String>> entry : this.byDomain.entrySet()) {
                String key = entry.getKey();
                if (norm.equals(key) || norm.endsWith("." + key)) {
                    for (Map.Entry<String, String> entry2 : entry.getValue().entrySet()) {
                        if (sb.length() > 0) {
                            sb.append("; ");
                        }
                        sb.append(entry2.getKey()).append('=').append(entry2.getValue());
                    }
                }
            }
            return sb.length() == 0 ? null : sb.toString();
        }

        synchronized void store(String str, Map<String, List<String>> map) {
            if (map == null) {
                return;
            }
            try {
                String norm = norm(new URL(str).getHost());
                for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                    if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("Set-Cookie")) {
                        for (String str2 : entry.getValue()) {
                            if (str2 != null) {
                                String[] split = str2.split(";");
                                String trim = split[0].trim();
                                int indexOf = trim.indexOf(61);
                                if (indexOf > 0) {
                                    String trim2 = trim.substring(0, indexOf).trim();
                                    String trim3 = trim.substring(indexOf + 1).trim();
                                    if (trim3.length() > 2 && trim3.startsWith("\"") && trim3.endsWith("\"")) {
                                        trim3 = trim3.substring(1, trim3.length() - 1);
                                    }
                                    String str3 = norm;
                                    for (int i = 1; i < split.length; i++) {
                                        String trim4 = split[i].trim();
                                        if (trim4.toLowerCase().startsWith("domain=")) {
                                            str3 = norm(trim4.substring(7).trim());
                                        }
                                    }
                                    set(str3, trim2, trim3);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
            }
        }

        synchronized boolean has(String str) {
            Iterator<Map<String, String>> it = this.byDomain.values().iterator();
            while (it.hasNext()) {
                String str2 = it.next().get(str);
                if (str2 != null && !str2.isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        synchronized void clear() {
            this.byDomain.clear();
        }
    }

    /* loaded from: /home/user/Doubao/chats/38450769749427714/lanzou_player_analysis/apk_content/classes.dex */
    public static class Resp implements AutoCloseable {
        public final InputStream body;
        private byte[] buffered;
        public final int code;
        public final HttpURLConnection conn;
        public final Map<String, List<String>> headers;

        Resp(int i, HttpURLConnection httpURLConnection, InputStream inputStream) {
            this.code = i;
            this.conn = httpURLConnection;
            this.body = inputStream;
            this.headers = httpURLConnection.getHeaderFields();
        }

        Resp(int i, HttpURLConnection httpURLConnection, byte[] bArr) {
            this.code = i;
            this.conn = httpURLConnection;
            this.body = null;
            this.buffered = bArr;
            this.headers = httpURLConnection.getHeaderFields();
        }

        public String header(String str) {
            for (Map.Entry<String, List<String>> entry : this.headers.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(str)) {
                    List<String> value = entry.getValue();
                    if (value == null || value.isEmpty()) {
                        return null;
                    }
                    return value.get(0);
                }
            }
            return null;
        }

        public byte[] bytes() throws IOException {
            byte[] bArr = this.buffered;
            if (bArr != null) {
                return bArr;
            }
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            InputStream inputStream = this.body;
            String header = header("Content-Encoding");
            if (header != null && header.toLowerCase().contains("gzip") && this.body != null) {
                inputStream = new GZIPInputStream(this.body);
            }
            byte[] bArr2 = new byte[8192];
            while (inputStream != null) {
                int read = inputStream.read(bArr2);
                if (read <= 0) {
                    break;
                }
                byteArrayOutputStream.write(bArr2, 0, read);
            }
            return byteArrayOutputStream.toByteArray();
        }

        public String text() {
            try {
                return new String(bytes(), "UTF-8");
            } catch (Exception e) {
                return "";
            }
        }

        @Override // java.lang.AutoCloseable
        public void close() {
            try {
                InputStream inputStream = this.body;
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception e) {
            }
            try {
                this.conn.disconnect();
            } catch (Exception e2) {
            }
        }
    }

    private HttpURLConnection open(String str, String str2, Map<String, String> map, boolean z, boolean z2) throws IOException {
        URL url = new URL(str2);
        HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
        httpURLConnection.setRequestMethod(str);
        httpURLConnection.setConnectTimeout(20000);
        httpURLConnection.setReadTimeout(60000);
        httpURLConnection.setInstanceFollowRedirects(z2);
        httpURLConnection.setRequestProperty("User-Agent", UA);
        httpURLConnection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
        httpURLConnection.setRequestProperty("Accept-Encoding", "identity");
        if (map != null) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                httpURLConnection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        String header = this.jar.header(url);
        if (header != null) {
            httpURLConnection.setRequestProperty("Cookie", header);
        }
        return httpURLConnection;
    }

    private static String host(String str) {
        try {
            return new URL(str).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    public Resp get(String str, Map<String, String> map) throws IOException {
        for (int i = 0; i < 6; i++) {
            HttpURLConnection open = open("GET", str, map, true, true);
            int responseCode = open.getResponseCode();
            this.jar.store(str, open.getHeaderFields());
            Resp resp = new Resp(responseCode, open, responseCode >= 400 ? open.getErrorStream() : open.getInputStream());
            byte[] bytes = resp.bytes();
            resp.close();
            Matcher matcher = Pattern.compile("arg1='([0-9A-Fa-f]{40})'").matcher(new String(bytes, "UTF-8"));
            if (System.getProperty("lz.debug") != null) {
                System.out.println("[get] " + str + " code=" + responseCode + " len=" + bytes.length + " arg1=" + (matcher.find() ? "Y" : "N"));
                matcher.reset();
            }
            if (!matcher.find()) {
                return new Resp(responseCode, open, bytes);
            }
            String host = host(str);
            if (host != null) {
                setCookie(host, "acw_sc__v2", acw(matcher.group(1)));
            }
        }
        throw new IOException("CDN 校验未通过");
    }

    private void setCookie(String str, String str2, String str3) {
        this.jar.set(str, str2, str3);
    }

    public static String acw(String str) {
        char[] cArr = new char[40];
        int i = 0;
        for (int i2 = 0; i2 < 40; i2++) {
            for (int i3 = 0; i3 < 40; i3++) {
                if (POS[i3] == i2 + 1) {
                    cArr[i3] = str.charAt(i2);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        String str2 = new String(cArr);
        while (i < 40) {
            int i4 = i + 2;
            sb.append(String.format("%02x", Integer.valueOf(Integer.parseInt(MASK.substring(i, i4), 16) ^ Integer.parseInt(str2.substring(i, i4), 16))));
            i = i4;
        }
        return sb.toString();
    }

    public synchronized String login(String str, String str2) throws IOException {
        this.jar.clear();
        this.streamCache.clear();
        get("https://accounts.woozooo.com/accounts.php?action=login&ref=pc.woozooo.com", null);
        String str3 = "task=uselogin&username=" + URLEncoder.encode(str, "UTF-8") + "&password=" + URLEncoder.encode(str2, "UTF-8") + "&ref=pc.woozooo.com";
        HttpURLConnection open = open("POST", "https://accounts.woozooo.com/accounts.php", headers("https://accounts.woozooo.com/accounts.php?action=login&ref=pc.woozooo.com"), false, false);
        open.setDoOutput(true);
        open.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        OutputStream outputStream = open.getOutputStream();
        outputStream.write(str3.getBytes("UTF-8"));
        outputStream.flush();
        outputStream.close();
        try {
            String readText = readText(open, open.getResponseCode());
            if (jint(readText, "zt", 0) != 1) {
                String jstr = jstr(readText, "msgs");
                if (jstr == null) {
                    jstr = "账号或密码不正确";
                }
                throw new IOException(jstr);
            }
            String unescape = unescape(jstr(readText, "msgs"));
            if (unescape == null || unescape.isEmpty()) {
                throw new IOException("登录返回缺少跳转地址");
            }
            if (unescape.startsWith("/")) {
                unescape = "https://accounts.woozooo.com" + unescape;
            }
            get(unescape, null);
            if (!hasAuthCookie()) {
                throw new IOException("登录鉴权失败，请重试");
            }
            this.account = str;
            diskApi("47", "folder_id=-1");
        } catch (Exception e) {
            throw new IOException("登录请求失败");
        }
        return this.account;
    }

    public synchronized String dumpCookies() {
        StringBuilder sb;
        sb = new StringBuilder();
        for (Map.Entry entry : this.jar.byDomain.entrySet()) {
            for (Map.Entry entry2 : ((Map) entry.getValue()).entrySet()) {
                sb.append((String) entry.getKey()).append('\t').append((String) entry2.getKey()).append('\t').append((String) entry2.getValue()).append('\n');
            }
        }
        return sb.toString();
    }

    public synchronized void loadCookies(String str) {
        if (str != null) {
            if (!str.isEmpty()) {
                for (String str2 : str.split("\n")) {
                    String[] split = str2.split("\t");
                    if (split.length == 3 && !split[0].isEmpty() && !split[1].isEmpty()) {
                        this.jar.set(split[0], split[1], split[2]);
                    }
                }
            }
        }
    }

    public synchronized void setAutoLogin(String str, String str2) {
        this.autoUser = str;
        this.autoPass = str2;
    }

    public synchronized void clearAutoLogin() {
        this.autoUser = null;
        this.autoPass = null;
        this.lastAutoTry = 0L;
    }

    public synchronized boolean tryAutoLogin() {
        if (loggedIn()) {
            return true;
        }
        if (this.autoUser != null && this.autoPass != null) {
            long currentTimeMillis = System.currentTimeMillis();
            if (currentTimeMillis - this.lastAutoTry < 300000) {
                return false;
            }
            this.lastAutoTry = currentTimeMillis;
            try {
                login(this.autoUser, this.autoPass);
                return loggedIn();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    private boolean hasAuthCookie() {
        return this.jar.has("phpdisk_info") || this.jar.has("lanzou_ifo");
    }

    public boolean loggedIn() {
        return hasAuthCookie();
    }

    public String account() {
        return this.account;
    }

    public synchronized void logout() {
        this.jar.clear();
        this.streamCache.clear();
        this.account = "";
    }

    private Map<String, String> headers(String str) {
        HashMap hashMap = new HashMap();
        if (str != null) {
            hashMap.put("Referer", str);
        }
        hashMap.put("X-Requested-With", "XMLHttpRequest");
        return hashMap;
    }

    private String readText(HttpURLConnection httpURLConnection, int i) throws IOException {
        try {
            this.jar.store(httpURLConnection.getURL().toString(), httpURLConnection.getHeaderFields());
        } catch (Exception e) {
        }
        InputStream errorStream = i >= 400 ? httpURLConnection.getErrorStream() : httpURLConnection.getInputStream();
        String headerField = httpURLConnection.getHeaderField("Content-Encoding");
        if (errorStream != null && headerField != null && headerField.toLowerCase().contains("gzip")) {
            errorStream = new GZIPInputStream(errorStream);
        }
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] bArr = new byte[8192];
        while (errorStream != null) {
            int read = errorStream.read(bArr);
            if (read <= 0) {
                break;
            }
            byteArrayOutputStream.write(bArr, 0, read);
        }
        return new String(byteArrayOutputStream.toByteArray(), "UTF-8");
    }

    /* loaded from: /home/user/Doubao/chats/38450769749427714/lanzou_player_analysis/apk_content/classes.dex */
    public static class Crumbs {
        public String id;
        public String name;

        Crumbs(String str, String str2) {
            this.id = str;
            this.name = str2;
        }
    }

    private String diskApi(String str, String str2) throws IOException {
        HttpURLConnection open = open("POST", "https://pc.woozooo.com/doupload.php", headers("https://pc.woozooo.com/mydisk.php"), false, false);
        open.setDoOutput(true);
        open.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        open.setRequestProperty("Origin", "https://pc.woozooo.com");
        String str3 = "task=" + str + (str2.isEmpty() ? "" : "&" + str2);
        OutputStream outputStream = open.getOutputStream();
        outputStream.write(str3.getBytes("UTF-8"));
        outputStream.flush();
        outputStream.close();
        String readText = readText(open, open.getResponseCode());
        if (readText.trim().startsWith("<")) {
            throw new IOException("登录已失效，请重新登录");
        }
        int jint = jint(readText, "zt", 0);
        if (jint != 1 && jint != 2) {
            String jstr = jstr(readText, "info");
            if (jstr == null) {
                jstr = "接口请求失败";
            }
            throw new IOException(jstr);
        }
        return readText;
    }

    public synchronized Browse browse(int i, int i2) throws IOException {
        Browse browse;
        String str;
        browse = new Browse();
        browse.folderId = String.valueOf(i);
        String diskApi = diskApi("47", "folder_id=" + i);
        for (String str2 : objects(jarray(diskApi, "text"))) {
            Item item = new Item();
            item.type = "folder";
            item.id = jstr(str2, "fol_id");
            item.name = unescape(jstr(str2, "name"));
            item.desc = unescape(jstr(str2, "folder_des"));
            item.locked = "1".equals(jstr(str2, "is_lock"));
            if (item.id != null) {
                if (item.name == null) {
                    item.name = "未命名文件夹";
                }
                browse.folders.add(item);
            }
        }
        for (String str3 : objects(jarray(diskApi, "info"))) {
            String jstr = jstr(str3, "folderid");
            if (jstr != null) {
                browse.crumbs.add(new Crumbs(jstr, jstr(str3, "name")));
            }
        }
        if (i != -1) {
            if (browse.crumbs.isEmpty()) {
                browse.crumbs.add(new Crumbs(String.valueOf(i), "当前文件夹"));
            }
            if (!"-1".equals(browse.crumbs.get(0).id)) {
                browse.crumbs.add(0, new Crumbs("-1", "全部文件"));
            }
        }
        String diskApi2 = diskApi("5", "folder_id=" + i + "&pg=" + i2);
        for (String str4 : objects(jarray(diskApi2, "text"))) {
            Item item2 = new Item();
            item2.id = jstr(str4, "id");
            String jstr2 = jstr(str4, "name_all");
            if (jstr2 == null) {
                jstr2 = jstr(str4, "name");
            }
            item2.name = jstr2 == null ? "" : unescape(jstr2);
            int lastIndexOf = item2.name.lastIndexOf(46);
            item2.ext = lastIndexOf >= 0 ? item2.name.substring(lastIndexOf + 1).toLowerCase() : "";
            item2.size = unescape(jstr(str4, "size"));
            item2.time = unescape(jstr(str4, "time"));
            String jstr3 = jstr(str4, "downs");
            if (jstr3 != null) {
                item2.downs = jstr3;
            }
            item2.playable = contains(PLAYABLE_EXT, item2.ext);
            item2.mime = mime(item2.ext);
            if (contains(VIDEO_EXT, item2.ext)) {
                str = "video";
            } else {
                str = contains(IMAGE_EXT, item2.ext) ? "image" : "file";
            }
            item2.type = str;
            if (item2.id != null) {
                browse.files.add(item2);
            }
        }
        int jint = jint(diskApi2, "info", 1);
        browse.page = i2;
        browse.pages = Math.max(i2, jint);
        return browse;
    }

    private String shareId(String str) throws IOException {
        String diskApi = diskApi("22", "file_id=" + str);
        String jstr = jstr(diskApi, "f_id");
        String unescape = unescape(jstr(diskApi, "is_newd"));
        if (jstr == null || jstr.isEmpty()) {
            throw new IOException("该文件未开启分享，无法在线播放");
        }
        if (unescape == null || unescape.isEmpty()) {
            unescape = "https://www.lanzoui.com";
        }
        return trimSlash(unescape) + "/" + jstr;
    }

    /* JADX WARN: Removed duplicated region for block: B:55:0x01fa  */
    /* JADX WARN: Removed duplicated region for block: B:58:0x01fb A[Catch: all -> 0x022f, TryCatch #0 {, blocks: (B:3:0x0001, B:5:0x000d, B:7:0x001b, B:11:0x001f, B:13:0x0046, B:15:0x0087, B:18:0x0091, B:21:0x0099, B:23:0x009f, B:25:0x00af, B:28:0x00c0, B:29:0x00c7, B:30:0x00c8, B:32:0x0166, B:35:0x017e, B:38:0x0195, B:40:0x01b3, B:44:0x01bd, B:47:0x01c4, B:50:0x01d1, B:53:0x01eb, B:56:0x01fd, B:57:0x020e, B:58:0x01fb, B:60:0x020f, B:61:0x0216, B:62:0x0217, B:63:0x021e, B:64:0x021f, B:65:0x0226, B:67:0x0227, B:68:0x022e), top: B:2:0x0001 }] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public synchronized java.lang.String resolve(java.lang.String r14) throws java.io.IOException {
        /*
            Method dump skipped, instructions count: 562
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.doubao.lanzouplayer.LanzouClient.resolve(java.lang.String):java.lang.String");
    }

    private static Map<String, String> rangeHeaders(String str) {
        HashMap hashMap = new HashMap();
        if (str != null) {
            hashMap.put("Range", str);
        }
        return hashMap;
    }

    public Resp openStream(String str, String str2, String str3) throws IOException {
        for (int i = 0; i < 4; i++) {
            String resolve = resolve(str);
            HttpURLConnection open = open(str3, resolve, rangeHeaders(str2), true, true);
            int responseCode = open.getResponseCode();
            this.jar.store(resolve, open.getHeaderFields());
            Resp resp = new Resp(responseCode, open, responseCode >= 400 ? open.getErrorStream() : open.getInputStream());
            String header = resp.header("Content-Type");
            if (header != null && header.toLowerCase().contains("text/html")) {
                byte[] bytes = resp.bytes();
                resp.close();
                Matcher matcher = Pattern.compile("arg1='([0-9A-Fa-f]{40})'").matcher(new String(bytes, "UTF-8"));
                if (matcher.find()) {
                    String host = host(resolve);
                    if (host != null) {
                        setCookie(host, "acw_sc__v2", acw(matcher.group(1)));
                    }
                } else {
                    this.streamCache.remove(str);
                }
            } else {
                return resp;
            }
        }
        throw new IOException("视频直链不可用，请稍后重试");
    }

    private static boolean contains(String[] strArr, String str) {
        for (String str2 : strArr) {
            if (str2.equals(str)) {
                return true;
            }
        }
        return false;
    }

    /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
    public static String mime(String str) {
        char c;
        if (str == null) {
            return "video/mp4";
        }
        switch (str.hashCode()) {
            case 3643:
                if (str.equals("rm")) {
                    c = 15;
                    break;
                }
                c = 65535;
                break;
            case 3711:
                if (str.equals("ts")) {
                    c = 7;
                    break;
                }
                c = 65535;
                break;
            case 52316:
                if (str.equals("3gp")) {
                    c = '\f';
                    break;
                }
                c = 65535;
                break;
            case 96980:
                if (str.equals("avi")) {
                    c = 5;
                    break;
                }
                c = 65535;
                break;
            case 101488:
                if (str.equals("flv")) {
                    c = 6;
                    break;
                }
                c = 65535;
                break;
            case 106479:
                if (str.equals("m4v")) {
                    c = 1;
                    break;
                }
                c = 65535;
                break;
            case 108184:
                if (str.equals("mkv")) {
                    c = 4;
                    break;
                }
                c = 65535;
                break;
            case 108273:
                if (str.equals("mp4")) {
                    c = 0;
                    break;
                }
                c = 65535;
                break;
            case 108308:
                if (str.equals("mov")) {
                    c = 2;
                    break;
                }
                c = 65535;
                break;
            case 108324:
                if (str.equals("mpg")) {
                    c = '\t';
                    break;
                }
                c = 65535;
                break;
            case 109982:
                if (str.equals("ogv")) {
                    c = '\r';
                    break;
                }
                c = 65535;
                break;
            case 117856:
                if (str.equals("wmv")) {
                    c = 11;
                    break;
                }
                c = 65535;
                break;
            case 3298980:
                if (str.equals("m2ts")) {
                    c = '\b';
                    break;
                }
                c = 65535;
                break;
            case 3358085:
                if (str.equals("mpeg")) {
                    c = '\n';
                    break;
                }
                c = 65535;
                break;
            case 3504679:
                if (str.equals("rmvb")) {
                    c = 14;
                    break;
                }
                c = 65535;
                break;
            case 3645337:
                if (str.equals("webm")) {
                    c = 3;
                    break;
                }
                c = 65535;
                break;
            default:
                c = 65535;
                break;
        }
        switch (c) {
            case 0:
            case 1:
                return "video/mp4";
            case 2:
                return "video/quicktime";
            case 3:
                return "video/webm";
            case 4:
                return "video/x-matroska";
            case 5:
                return "video/x-msvideo";
            case 6:
                return "video/x-flv";
            case 7:
            case '\b':
                return "video/mp2t";
            case '\t':
            case '\n':
                return "video/mpeg";
            case 11:
                return "video/x-ms-wmv";
            case '\f':
                return "video/3gpp";
            case '\r':
                return "video/ogg";
            case 14:
                return "application/vnd.rn-realmedia-vbr";
            case 15:
                return "application/vnd.rn-realmedia";
            default:
                return "video/mp4";
        }
    }

    private static String trimSlash(String str) {
        while (str.endsWith("/")) {
            str = str.substring(0, str.length() - 1);
        }
        return str;
    }

    static String var(String str, String str2) {
        Matcher matcher = Pattern.compile("var " + Pattern.quote(str2) + " = '([^']*)'").matcher(str);
        return matcher.find() ? matcher.group(1) : "";
    }

    static String jarray(String str, String str2) {
        int indexOf;
        int matchBracket;
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(str2) + "\"\\s*:\\s*\\[").matcher(str);
        if (matcher.find() && (matchBracket = matchBracket(str, (indexOf = str.indexOf(91, matcher.start())))) >= 0) {
            return str.substring(indexOf, matchBracket + 1);
        }
        return null;
    }

    static int matchBracket(String str, int i) {
        char charAt = str.charAt(i);
        char c = charAt == '[' ? ']' : '}';
        boolean z = false;
        int i2 = 0;
        while (i < str.length()) {
            char charAt2 = str.charAt(i);
            if (z) {
                if (charAt2 == '\\') {
                    i++;
                } else if (charAt2 == '\"') {
                    z = false;
                }
            } else if (charAt2 == '\"') {
                z = true;
            } else if (charAt2 == charAt) {
                i2++;
            } else if (charAt2 == c && i2 - 1 == 0) {
                return i;
            }
            i++;
        }
        return -1;
    }

    static List<String> objects(String str) {
        int indexOf;
        int matchBracket;
        ArrayList arrayList = new ArrayList();
        if (str == null) {
            return arrayList;
        }
        int i = 0;
        while (i < str.length() && (indexOf = str.indexOf(123, i)) >= 0 && (matchBracket = matchBracket(str, indexOf)) >= 0) {
            int i2 = matchBracket + 1;
            arrayList.add(str.substring(indexOf, i2));
            i = i2;
        }
        return arrayList;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static String jstr(String str, String str2) {
        if (str == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(str2) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(str);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    static int jint(String str, String str2, int i) {
        if (str == null) {
            return i;
        }
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(str2) + "\"\\s*:\\s*\"?(\\d+)").matcher(str);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : i;
    }

    static String unescape(String str) {
        int i;
        if (str == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int i2 = 0;
        while (i2 < str.length()) {
            char charAt = str.charAt(i2);
            if (charAt == '\\' && (i = i2 + 1) < str.length()) {
                char charAt2 = str.charAt(i);
                switch (charAt2) {
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        i2 = i + 4;
                        if (i2 < str.length()) {
                            sb.append((char) Integer.parseInt(str.substring(i + 1, i + 5), 16));
                            break;
                        }
                        break;
                    default:
                        sb.append(charAt2);
                        break;
                }
                i2 = i;
            } else {
                sb.append(charAt);
            }
            i2++;
        }
        return sb.toString();
    }
}
