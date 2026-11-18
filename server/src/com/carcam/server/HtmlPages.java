package com.carcam.server;

/** 生成手机浏览器可直接访问的页面（首页 + 播放页） */
public final class HtmlPages {

    private HtmlPages() {}

    public static String index() {
        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>车机行车记录仪</title><style>"
                + "body{font-family:system-ui,-apple-system,sans-serif;background:#0f172a;color:#e2e8f0;margin:0;padding:16px;max-width:960px;margin:0 auto}"
                + "h1{font-size:20px} .card{background:#1e293b;border-radius:12px;padding:12px;margin:10px 0}"
                + "a{color:#7dd3fc;text-decoration:none} .dir{font-size:18px;display:block;padding:8px;border-radius:8px;background:#0f172a;margin:4px 0}"
                + ".video{display:flex;gap:10px;align-items:center;padding:8px;border-radius:8px;background:#0f172a;margin:4px 0}"
                + ".thumb{width:96px;height:54px;object-fit:cover;border-radius:6px;background:#334155;flex-shrink:0}"
                + ".meta{font-size:12px;color:#94a3b8} .btn{display:inline-block;background:#2563eb;color:#fff;padding:6px 14px;border-radius:8px;margin-right:8px}"
                + "</style></head><body>"
                + "<h1>🚗 车机行车记录仪 · 远程查看</h1>"
                + "<p>手机已连接车机热点后，即可在此浏览录像。点击视频可在线播放，支持拖动进度。</p>"
                + "<div class=\"card\"><b>录像目录（自动识别，可点击进入）</b>"
                + "<div id=\"roots\">扫描中…</div></div>"
                + "<div class=\"card\"><b>文件浏览</b><div id=\"browser\">加载中…</div></div>"
                + "<script>"
                + "async function j(u){let r=await fetch(u);return r.json();}"
                + "function esc(s){return s.replace(/&/g,'&amp;').replace(/</g,'&lt;');}"
                + "function fmt(n){if(n<1024)return n+' B';if(n<1048576)return (n/1024).toFixed(1)+' KB';if(n<1073741824)return (n/1048576).toFixed(1)+' MB';return (n/1073741824).toFixed(2)+' GB';}"
                + "const B64='ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';"
                + "function enc(p){let b=new TextEncoder().encode(p);let s='';for(let i=0;i<b.length;i+=3){let n=(b[i]<<16)|((i+1<b.length?b[i+1]:0)<<8)|(i+2<b.length?b[i+2]:0);s+=B64[(n>>18)&63]+B64[(n>>12)&63]+(i+1<b.length?B64[(n>>6)&63]:'')+(i+2<b.length?B64[n&63]:'');}return s;}"
                + "function updDir(p){fetch('/api/list?p='+encodeURIComponent(p)).then(r=>r.json()).then(d=>{"
                + "let h='<a class=\"dir\" href=\"javascript:updDir(\\''+esc(up())+'\\')\">⬆ 上级目录</a>';"
                + "d.entries.forEach(e=>{if(e.isDir){h+='<a class=\"dir\" href=\"javascript:updDir(\\''+enc(e.path)+'\\')\">📁 '+esc(e.name)+'</a>';}"
                + "else if(e.video){h+='<div class=\"video\"><img class=\"thumb\" src=\"/thumb/'+enc(e.path)+'\">'"
                + "<div><a href=\"/view.html?p='+enc(e.path)+'\">▶ '+esc(e.name)+'</a>"
                + "<div class=\"meta\">'+fmt(e.size)+'</div></div></div>';}});"
                + "document.getElementById('browser').innerHTML=h;}).catch(()=>{document.getElementById('browser').innerHTML='加载失败';});}"
                + "function up(){let d=document.getElementById('cur').getAttribute('data-p');let i=d.lastIndexOf('/');return i>0?d.substring(0,i):'/';}"
                + "window._cur='';"
                + "j('/api/scan').then(d=>{let h='';d.roots.forEach(r=>{h+='<a class=\"dir\" href=\"javascript:updDir(\\''+enc(r.dir)+'\\')\">🎥 '+esc(r.dir)+' （'+r.count+' 个视频，'+fmt(r.size)+'）</a>';});"
                + "document.getElementById('roots').innerHTML=h;if(h==='')document.getElementById('roots').innerHTML='未找到录像，请确认记录仪已录制过视频';"
                + "let first=d.roots[0];if(first){document.getElementById('browser').innerHTML='<div id=\"cur\" data-p=\"'+esc(first.dir)+'\"></div>';updDir(first.dir);}});"
                + "</script></body></html>";
    }

    public static String view(String path) {
        String b64 = Util.enc(path);
        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>播放 - 车机录像</title><style>"
                + "body{background:#000;margin:0;color:#e2e8f0;font-family:system-ui,sans-serif}"
                + "video{width:100%;max-height:80vh;background:#000}"
                + ".bar{padding:10px;font-size:13px;display:flex;gap:8px;align-items:center;flex-wrap:wrap}"
                + "a{color:#7dd3fc;text-decoration:none} .path{color:#94a3b8;word-break:break-all}"
                + "</style></head><body>"
                + "<video id=\"v\" controls autoplay playsinline preload=\"metadata\" src=\"/video/" + b64 + "\"></video>"
                + "<div class=\"bar\"><a href=\"/\">← 返回列表</a><span class=\"path\">" + escapeHtml(path) + "</span></div>"
                + "<script>"
                + "const v=document.getElementById('v');"
                + "v.addEventListener('error',()=>{document.querySelector('.bar').insertAdjacentHTML('beforeend','<span style=\"color:#f87171\">⚠ 播放失败：该格式车机端不支持在线播放，可返回列表下载后用手机播放器打开</span>');});"
                + "</script></body></html>";
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
