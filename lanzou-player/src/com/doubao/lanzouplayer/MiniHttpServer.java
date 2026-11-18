package com.doubao.lanzouplayer;

import com.doubao.lanzouplayer.LanzouClient;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/* loaded from: /home/user/Doubao/chats/38450769749427714/lanzou_player_analysis/apk_content/classes.dex */
public class MiniHttpServer {
    private final Assets assets;
    private AuthListener authListener;
    private final LanzouClient lz;
    private final ExecutorService pool = Executors.newFixedThreadPool(8);
    private final int port;
    private volatile boolean running;
    private ServerSocket server;

    /* loaded from: /home/user/Doubao/chats/38450769749427714/lanzou_player_analysis/apk_content/classes.dex */
    public interface Assets {
        byte[] read(String str) throws IOException;
    }

    /* loaded from: /home/user/Doubao/chats/38450769749427714/lanzou_player_analysis/apk_content/classes.dex */
    public interface AuthListener {
        void onLogin(String str, String str2);

        void onLogout();
    }

    public void setAuthListener(AuthListener authListener) {
        this.authListener = authListener;
    }

    public MiniHttpServer(int i, LanzouClient lanzouClient, Assets assets) {
        this.port = i;
        this.lz = lanzouClient;
        this.assets = assets;
    }

    public int start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(this.port, 50, InetAddress.getByName("127.0.0.1"));
        this.server = serverSocket;
        serverSocket.setReuseAddress(true);
        this.running = true;
        int localPort = this.server.getLocalPort();
        Thread thread = new Thread(new Runnable() { // from class: com.doubao.lanzouplayer.MiniHttpServer.1
            @Override // java.lang.Runnable
            public void run() {
                while (MiniHttpServer.this.running) {
                    try {
                        final Socket accept = MiniHttpServer.this.server.accept();
                        accept.setSoTimeout(0);
                        MiniHttpServer.this.pool.submit(new Runnable() { // from class: com.doubao.lanzouplayer.MiniHttpServer.1.1
                            @Override // java.lang.Runnable
                            public void run() {
                                MiniHttpServer.this.handle(accept);
                            }
                        });
                    } catch (IOException e) {
                        boolean unused = MiniHttpServer.this.running;
                    }
                }
            }
        }, "mini-http-accept");
        thread.setDaemon(true);
        thread.start();
        return localPort;
    }

    public void stop() {
        this.running = false;
        try {
            ServerSocket serverSocket = this.server;
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Code restructure failed: missing block: B:58:0x00be, code lost:
    
        r12.close();
     */
    /* JADX WARN: Code restructure failed: missing block: B:59:0x00c1, code lost:
    
        return;
     */
    /* JADX WARN: Removed duplicated region for block: B:35:0x0088 A[Catch: all -> 0x00c2, TryCatch #1 {all -> 0x00c2, blocks: (B:2:0x0001, B:4:0x0014, B:7:0x001c, B:16:0x002d, B:17:0x0038, B:19:0x003e, B:21:0x0044, B:23:0x004a, B:25:0x0052, B:30:0x006d, B:52:0x0078, B:33:0x0083, B:35:0x0088, B:37:0x0091, B:40:0x0093, B:42:0x009e, B:43:0x00ab), top: B:1:0x0001, outer: #0 }] */
    /* JADX WARN: Removed duplicated region for block: B:42:0x009e A[Catch: all -> 0x00c2, TryCatch #1 {all -> 0x00c2, blocks: (B:2:0x0001, B:4:0x0014, B:7:0x001c, B:16:0x002d, B:17:0x0038, B:19:0x003e, B:21:0x0044, B:23:0x004a, B:25:0x0052, B:30:0x006d, B:52:0x0078, B:33:0x0083, B:35:0x0088, B:37:0x0091, B:40:0x0093, B:42:0x009e, B:43:0x00ab), top: B:1:0x0001, outer: #0 }] */
    /* JADX WARN: Removed duplicated region for block: B:45:0x00b8 A[Catch: Exception -> 0x00ce, TRY_ENTER, TryCatch #0 {Exception -> 0x00ce, blocks: (B:11:0x0028, B:45:0x00b8, B:58:0x00be, B:65:0x00cd, B:70:0x00ca, B:2:0x0001, B:4:0x0014, B:7:0x001c, B:16:0x002d, B:17:0x0038, B:19:0x003e, B:21:0x0044, B:23:0x004a, B:25:0x0052, B:30:0x006d, B:52:0x0078, B:33:0x0083, B:35:0x0088, B:37:0x0091, B:40:0x0093, B:42:0x009e, B:43:0x00ab, B:67:0x00c5), top: B:1:0x0001, inners: #1, #3 }] */
    /* JADX WARN: Removed duplicated region for block: B:49:0x00a9  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public void handle(java.net.Socket r12) {
        /*
            r11 = this;
            java.io.BufferedInputStream r0 = new java.io.BufferedInputStream     // Catch: java.lang.Throwable -> Lc2
            java.io.InputStream r1 = r12.getInputStream()     // Catch: java.lang.Throwable -> Lc2
            r0.<init>(r1)     // Catch: java.lang.Throwable -> Lc2
            java.io.OutputStream r1 = r12.getOutputStream()     // Catch: java.lang.Throwable -> Lc2
            java.lang.String r2 = readLine(r0)     // Catch: java.lang.Throwable -> Lc2
            if (r2 == 0) goto Lbc
            boolean r3 = r2.isEmpty()     // Catch: java.lang.Throwable -> Lc2
            if (r3 == 0) goto L1c
            goto Lbc
        L1c:
            java.lang.String r3 = " "
            java.lang.String[] r2 = r2.split(r3)     // Catch: java.lang.Throwable -> Lc2
            int r3 = r2.length     // Catch: java.lang.Throwable -> Lc2
            r4 = 2
            if (r3 >= r4) goto L2c
            if (r12 == 0) goto L2b
            r12.close()     // Catch: java.lang.Exception -> Lce
        L2b:
            return
        L2c:
            r3 = 0
            r4 = r2[r3]     // Catch: java.lang.Throwable -> Lc2
            r5 = 1
            r2 = r2[r5]     // Catch: java.lang.Throwable -> Lc2
            java.util.HashMap r6 = new java.util.HashMap     // Catch: java.lang.Throwable -> Lc2
            r6.<init>()     // Catch: java.lang.Throwable -> Lc2
            r7 = r3
        L38:
            java.lang.String r8 = readLine(r0)     // Catch: java.lang.Throwable -> Lc2
            if (r8 == 0) goto L6d
            boolean r9 = r8.isEmpty()     // Catch: java.lang.Throwable -> Lc2
            if (r9 != 0) goto L6d
            int r9 = r7 + 1
            r10 = 100
            if (r7 >= r10) goto L6d
            r7 = 58
            int r7 = r8.indexOf(r7)     // Catch: java.lang.Throwable -> Lc2
            if (r7 <= 0) goto L6b
            java.lang.String r10 = r8.substring(r3, r7)     // Catch: java.lang.Throwable -> Lc2
            java.lang.String r10 = r10.trim()     // Catch: java.lang.Throwable -> Lc2
            java.lang.String r10 = r10.toLowerCase()     // Catch: java.lang.Throwable -> Lc2
            int r7 = r7 + 1
            java.lang.String r7 = r8.substring(r7)     // Catch: java.lang.Throwable -> Lc2
            java.lang.String r7 = r7.trim()     // Catch: java.lang.Throwable -> Lc2
            r6.put(r10, r7)     // Catch: java.lang.Throwable -> Lc2
        L6b:
            r7 = r9
            goto L38
        L6d:
            java.lang.String r7 = "content-length"
            java.lang.Object r7 = r6.get(r7)     // Catch: java.lang.Throwable -> Lc2
            java.lang.String r7 = (java.lang.String) r7     // Catch: java.lang.Throwable -> Lc2
            if (r7 == 0) goto L82
            java.lang.String r7 = r7.trim()     // Catch: java.lang.NumberFormatException -> L81 java.lang.Throwable -> Lc2
            int r7 = java.lang.Integer.parseInt(r7)     // Catch: java.lang.NumberFormatException -> L81 java.lang.Throwable -> Lc2
            goto L83
        L81:
            r7 = move-exception
        L82:
            r7 = r3
        L83:
            byte[] r8 = new byte[r7]     // Catch: java.lang.Throwable -> Lc2
            r9 = r3
        L86:
            if (r9 >= r7) goto L93
            int r10 = r7 - r9
            int r10 = r0.read(r8, r9, r10)     // Catch: java.lang.Throwable -> Lc2
            if (r10 >= 0) goto L91
            goto L93
        L91:
            int r9 = r9 + r10
            goto L86
        L93:
            java.lang.String r0 = ""
            r7 = 63
            int r7 = r2.indexOf(r7)     // Catch: java.lang.Throwable -> Lc2
            if (r7 < 0) goto La9
            java.lang.String r0 = r2.substring(r3, r7)     // Catch: java.lang.Throwable -> Lc2
            int r7 = r7 + r5
            java.lang.String r2 = r2.substring(r7)     // Catch: java.lang.Throwable -> Lc2
            r5 = r2
            goto Lab
        La9:
            r5 = r0
            r0 = r2
        Lab:
            r2 = r11
            r3 = r4
            r4 = r0
            r7 = r8
            r8 = r1
            r2.route(r3, r4, r5, r6, r7, r8)     // Catch: java.lang.Throwable -> Lc2
            r1.flush()     // Catch: java.lang.Throwable -> Lc2
            if (r12 == 0) goto Lbb
            r12.close()     // Catch: java.lang.Exception -> Lce
        Lbb:
            goto Lcf
        Lbc:
            if (r12 == 0) goto Lc1
            r12.close()     // Catch: java.lang.Exception -> Lce
        Lc1:
            return
        Lc2:
            r0 = move-exception
            if (r12 == 0) goto Lcd
            r12.close()     // Catch: java.lang.Throwable -> Lc9
            goto Lcd
        Lc9:
            r12 = move-exception
            r0.addSuppressed(r12)     // Catch: java.lang.Exception -> Lce
        Lcd:
            throw r0     // Catch: java.lang.Exception -> Lce
        Lce:
            r12 = move-exception
        Lcf:
            return
        */
        throw new UnsupportedOperationException("Method not decompiled: com.doubao.lanzouplayer.MiniHttpServer.handle(java.net.Socket):void");
    }

    private void route(String str, String str2, String str3, Map<String, String> map, byte[] bArr, OutputStream outputStream) throws IOException {
        if (str2.equals("/") || str2.equals("/index.html")) {
            serveAsset("index.html", outputStream);
            return;
        }
        if (str2.startsWith("/static/")) {
            serveAsset(str2.substring(1), outputStream);
            return;
        }
        if (str2.equals("/favicon.ico")) {
            writeHead(outputStream, 204, "text/plain", "0", null, null);
            return;
        }
        if (str2.equals("/api/state")) {
            this.lz.tryAutoLogin();
            json(outputStream, 200, "{\"logged_in\":" + this.lz.loggedIn() + ",\"username\":\"" + esc(this.lz.account()) + "\",\"account\":\"" + esc(this.lz.account()) + "\"}");
            return;
        }
        if (str2.equals("/api/login") && str.equals("POST")) {
            HashMap hashMap = new HashMap();
            String str4 = new String(bArr, "UTF-8");
            hashMap.put("username", nullToEmpty(LanzouClient.jstr(str4, "username")));
            hashMap.put("password", nullToEmpty(LanzouClient.jstr(str4, "password")));
            if (!((String) hashMap.get("username")).isEmpty() && !((String) hashMap.get("password")).isEmpty()) {
                try {
                    String login = this.lz.login((String) hashMap.get("username"), (String) hashMap.get("password"));
                    AuthListener authListener = this.authListener;
                    if (authListener != null) {
                        authListener.onLogin((String) hashMap.get("username"), (String) hashMap.get("password"));
                    }
                    json(outputStream, 200, "{\"ok\":true,\"account\":\"" + esc(login) + "\"}");
                    return;
                } catch (Exception e) {
                    json(outputStream, 400, err(e.getMessage()));
                    return;
                }
            }
            json(outputStream, 400, err("请填写账号和密码"));
            return;
        }
        if (str2.equals("/api/logout")) {
            this.lz.logout();
            AuthListener authListener2 = this.authListener;
            if (authListener2 != null) {
                authListener2.onLogout();
            }
            json(outputStream, 200, "{\"ok\":true}");
            return;
        }
        if (str2.equals("/api/browse")) {
            if (!this.lz.loggedIn()) {
                json(outputStream, 401, err("请先登录"));
                return;
            }
            Map<String, String> parseQuery = parseQuery(str3);
            try {
                json(outputStream, 200, browseJson(this.lz.browse(parseInt(parseQuery.get("folder_id"), -1), Math.max(1, parseInt(parseQuery.get("page"), 1)))));
                return;
            } catch (Exception e2) {
                json(outputStream, 400, err(e2.getMessage()));
                return;
            }
        }
        if (str2.startsWith("/api/stream/")) {
            if (!this.lz.loggedIn()) {
                json(outputStream, 401, err("请先登录"));
                return;
            } else {
                stream(str2.substring("/api/stream/".length()), parseQuery(str3).get("ext"), map.get("range"), outputStream);
                return;
            }
        }
        if (str2.startsWith("/api/download/")) {
            if (!this.lz.loggedIn()) {
                json(outputStream, 401, err("请先登录"));
                return;
            }
            String substring = str2.substring("/api/download/".length());
            String str5 = parseQuery(str3).get("name");
            if (str5 == null) {
                str5 = "video.mp4";
            }
            download(substring, str5, outputStream);
            return;
        }
        if (str2.startsWith("/api/link/")) {
            try {
                this.lz.resolve(str2.substring("/api/link/".length()));
                json(outputStream, 200, "{\"ok\":true}");
                return;
            } catch (Exception e3) {
                json(outputStream, 502, err(e3.getMessage()));
                return;
            }
        }
        json(outputStream, 404, err("not found"));
    }

    private void stream(String str, String str2, String str3, OutputStream outputStream) throws IOException {
        try {
            LanzouClient.Resp openStream = this.lz.openStream(str, str3, "GET");
            if (str2 == null) {
                str2 = "mp4";
            }
            try {
                String mime = LanzouClient.mime(str2);
                String header = openStream.header("Content-Length");
                String header2 = openStream.header("Content-Range");
                StringBuilder sb = new StringBuilder();
                int i = openStream.code == 206 ? 206 : 200;
                sb.append("HTTP/1.1 ").append(i).append(i == 206 ? " Partial Content" : " OK").append("\r\n");
                sb.append("Content-Type: ").append(mime).append("\r\n");
                sb.append("Accept-Ranges: bytes\r\n");
                sb.append("Cache-Control: no-store\r\n");
                if (header != null) {
                    sb.append("Content-Length: ").append(header).append("\r\n");
                }
                if (header2 != null) {
                    sb.append("Content-Range: ").append(header2).append("\r\n");
                }
                if (header == null) {
                    sb.append("Connection: close\r\n");
                }
                sb.append("\r\n");
                outputStream.write(sb.toString().getBytes("UTF-8"));
                outputStream.flush();
                if (openStream.body != null) {
                    byte[] bArr = new byte[65536];
                    while (true) {
                        int read = openStream.body.read(bArr);
                        if (read <= 0) {
                            break;
                        }
                        outputStream.write(bArr, 0, read);
                        outputStream.flush();
                    }
                }
                openStream.close();
            } catch (Throwable th) {
                openStream.close();
                throw th;
            }
        } catch (Exception e) {
            json(outputStream, 502, err(e.getMessage()));
        }
    }

    private void download(String str, String str2, OutputStream outputStream) throws IOException {
        try {
            LanzouClient.Resp openStream = this.lz.openStream(str, null, "GET");
            try {
                String header = openStream.header("Content-Length");
                StringBuilder sb = new StringBuilder();
                sb.append("HTTP/1.1 200 OK\r\n");
                sb.append("Content-Type: application/octet-stream\r\n");
                sb.append("Content-Disposition: attachment; filename=\"").append(str2).append("\"\r\n");
                if (header != null) {
                    sb.append("Content-Length: ").append(header).append("\r\n");
                } else {
                    sb.append("Connection: close\r\n");
                }
                sb.append("\r\n");
                outputStream.write(sb.toString().getBytes("UTF-8"));
                outputStream.flush();
                if (openStream.body != null) {
                    byte[] bArr = new byte[131072];
                    while (true) {
                        int read = openStream.body.read(bArr);
                        if (read <= 0) {
                            break;
                        }
                        outputStream.write(bArr, 0, read);
                        outputStream.flush();
                    }
                }
            } finally {
                openStream.close();
            }
        } catch (Exception e) {
            json(outputStream, 502, err(e.getMessage()));
        }
    }

    private void serveAsset(String str, OutputStream outputStream) throws IOException {
        byte[] bArr;
        String str2;
        try {
            bArr = this.assets.read(str);
        } catch (IOException e) {
            bArr = null;
        }
        if (bArr == null) {
            json(outputStream, 404, err("not found"));
            return;
        }
        if (str.endsWith(".html")) {
            str2 = "text/html; charset=utf-8";
        } else if (str.endsWith(".css")) {
            str2 = "text/css; charset=utf-8";
        } else {
            str2 = str.endsWith(".js") ? "application/javascript; charset=utf-8" : "text/plain; charset=utf-8";
        }
        writeHead(outputStream, 200, str2, String.valueOf(bArr.length), null, null);
        outputStream.write(bArr);
        outputStream.flush();
    }

    private void json(OutputStream outputStream, int i, String str) throws IOException {
        byte[] bytes = str.getBytes("UTF-8");
        writeHead(outputStream, i, "application/json; charset=utf-8", String.valueOf(bytes.length), null, null);
        outputStream.write(bytes);
        outputStream.flush();
    }

    private void writeHead(OutputStream outputStream, int i, String str, String str2, String str3, String str4) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(i).append(" ").append(reason(i)).append("\r\n");
        sb.append("Content-Type: ").append(str).append("\r\n");
        StringBuilder append = sb.append("Content-Length: ");
        if (str2 == null) {
            str2 = "0";
        }
        append.append(str2).append("\r\n");
        if (str3 != null) {
            sb.append("Content-Range: ").append(str3).append("\r\n");
        }
        if (str4 != null) {
            sb.append(str4);
        }
        sb.append("Cache-Control: no-store\r\n");
        sb.append("Connection: close\r\n\r\n");
        outputStream.write(sb.toString().getBytes("UTF-8"));
    }

    private static String reason(int i) {
        switch (i) {
            case 200:
                return "OK";
            case 204:
                return "No Content";
            case 206:
                return "Partial Content";
            case 400:
                return "Bad Request";
            case 401:
                return "Unauthorized";
            case 404:
                return "Not Found";
            case 502:
                return "Bad Gateway";
            default:
                return "OK";
        }
    }

    private static String err(String str) {
        StringBuilder append = new StringBuilder().append("{\"ok\":false,\"error\":\"");
        if (str == null) {
            str = "请求失败";
        }
        return append.append(esc(str)).append("\"}").toString();
    }

    static String esc(String str) {
        if (str == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            switch (c) {
                case '\t':
                    sb.append("\\t");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", Integer.valueOf(c)));
                        break;
                    } else {
                        sb.append(c);
                        break;
                    }
            }
        }
        return sb.toString();
    }

    private static String browseJson(LanzouClient.Browse browse) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"ok\":true,\"folder_id\":\"").append(esc(browse.folderId)).append("\"");
        sb.append(",\"crumbs\":[");
        for (int i = 0; i < browse.crumbs.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":\"").append(esc(browse.crumbs.get(i).id)).append("\",\"name\":\"").append(esc(browse.crumbs.get(i).name)).append("\"}");
        }
        sb.append("],\"folders\":[");
        for (int i2 = 0; i2 < browse.folders.size(); i2++) {
            if (i2 > 0) {
                sb.append(',');
            }
            sb.append(itemJson(browse.folders.get(i2)));
        }
        sb.append("],\"files\":[");
        for (int i3 = 0; i3 < browse.files.size(); i3++) {
            if (i3 > 0) {
                sb.append(',');
            }
            sb.append(itemJson(browse.files.get(i3)));
        }
        sb.append("],\"page\":").append(browse.page).append(",\"pages\":").append(browse.pages).append("}");
        return sb.toString();
    }

    private static String itemJson(LanzouClient.Item item) {
        return "{\"type\":\"" + esc(item.type) + "\",\"id\":\"" + esc(item.id) + "\",\"name\":\"" + esc(item.name) + "\",\"ext\":\"" + esc(item.ext) + "\",\"size\":\"" + esc(item.size) + "\",\"time\":\"" + esc(item.time) + "\",\"downloads\":\"" + esc(item.downs) + "\",\"playable\":" + item.playable + ",\"mime\":\"" + esc(item.mime) + "\",\"desc\":\"" + esc(item.desc) + "\",\"locked\":" + item.locked + "}";
    }

    private static Map<String, String> parseQuery(String str) {
        HashMap hashMap = new HashMap();
        if (str == null || str.isEmpty()) {
            return hashMap;
        }
        for (String str2 : str.split("&")) {
            int indexOf = str2.indexOf(61);
            if (indexOf >= 0) {
                try {
                    hashMap.put(str2.substring(0, indexOf), URLDecoder.decode(str2.substring(indexOf + 1), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                }
            }
        }
        return hashMap;
    }

    private static int parseInt(String str, int i) {
        if (str == null) {
            return i;
        }
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            return i;
        }
    }

    private static String nullToEmpty(String str) {
        return str == null ? "" : str;
    }

    private static String readLine(InputStream inputStream) throws IOException {
        int read;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        while (true) {
            read = inputStream.read();
            if (read == -1 || read == 10) {
                break;
            }
            if (read != 13) {
                byteArrayOutputStream.write(read);
                if (byteArrayOutputStream.size() > 8192) {
                    break;
                }
            }
        }
        if (read == -1 && byteArrayOutputStream.size() == 0) {
            return null;
        }
        return new String(byteArrayOutputStream.toByteArray(), "UTF-8");
    }
}
