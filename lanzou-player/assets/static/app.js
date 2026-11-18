const $ = (id) => document.getElementById(id);

const state = {
  folderId: '-1',
  page: 1,
  files: [],
  videos: [],
  index: -1,
  keyword: '',
};

/* ---------------- 小工具 ---------------- */
let toastTimer = null;
function toast(msg) {
  const el = $('toast');
  el.textContent = msg;
  el.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), 2200);
}

async function api(path, options = {}) {
  const resp = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  let data = {};
  try { data = await resp.json(); } catch (e) { /* ignore */ }
  if (!resp.ok || data.ok === false) {
    const err = new Error(data.error || `请求失败（${resp.status}）`);
    err.status = resp.status;
    throw err;
  }
  return data;
}

function iconFor(item) {
  if (item.type === 'folder') return { cls: 'folder', text: '夹' };
  if (item.type === 'video') return { cls: 'video', text: (item.ext || 'V').slice(0, 4).toUpperCase() };
  if (item.type === 'image') return { cls: 'image', text: (item.ext || 'IMG').slice(0, 3).toUpperCase() };
  return { cls: 'file', text: (item.ext || 'FILE').slice(0, 4).toUpperCase() };
}

function showView(name) {
  $('loginView').hidden = name !== 'login';
  $('browseView').hidden = name !== 'browse';
  $('playerView').hidden = name !== 'player';
}

/* ---------------- 登录 ---------------- */
function renderAccount(text) {
  $('accountLine').textContent = text;
}

async function bootstrap() {
  try {
    const st = await api('/api/state');
    if (st.logged_in) {
      renderAccount(st.account ? `${st.account}` : (st.username || '已登录'));
      $('btnRefresh').hidden = false;
      $('btnLogout').hidden = false;
      showView('browse');
      await loadFolder('-1', 1);
    } else {
      renderAccount('未登录');
      showView('login');
      const saved = localStorage.getItem('lz_user');
      if (saved) $('username').value = saved;
    }
  } catch (e) {
    renderAccount('未登录');
    showView('login');
  }
}

async function doLogin() {
  const username = $('username').value.trim();
  const password = $('password').value;
  if (!username || !password) {
    $('loginErr').textContent = '请填写账号和密码';
    return;
  }
  const btn = $('btnLogin');
  btn.disabled = true;
  btn.textContent = '登录中…';
  $('loginErr').textContent = '';
  try {
    const res = await api('/api/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    });
    if ($('remember').checked) localStorage.setItem('lz_user', username);
    else localStorage.removeItem('lz_user');
    $('password').value = '';
    renderAccount(res.account || username);
    $('btnRefresh').hidden = false;
    $('btnLogout').hidden = false;
    showView('browse');
    await loadFolder('-1', 1);
    toast('登录成功');
  } catch (e) {
    $('loginErr').textContent = e.message;
  } finally {
    btn.disabled = false;
    btn.textContent = '登 录';
  }
}

async function doLogout() {
  if (!confirm('确定退出登录？')) return;
  await api('/api/logout', { method: 'POST' }).catch(() => {});
  $('video').pause();
  $('video').removeAttribute('src');
  renderAccount('未登录');
  $('btnRefresh').hidden = true;
  $('btnLogout').hidden = true;
  showView('login');
}

/* ---------------- 目录浏览 ---------------- */
async function loadFolder(folderId, page = 1) {
  state.folderId = String(folderId);
  state.page = page;
  $('list').innerHTML = '<div class="empty">正在读取目录…</div>';
  try {
    const data = await api(`/api/browse?folder_id=${encodeURIComponent(folderId)}&page=${page}`);
    state.files = [...(data.folders || []), ...(data.files || [])];
    renderCrumbs(data.crumbs || []);
    renderList();
    renderPager(data.page, data.pages);
    $('btnRefresh').hidden = false;
  } catch (e) {
    if (e.status === 401) {
      showView('login');
      return;
    }
    $('list').innerHTML = `<div class="empty">${e.message}</div>`;
  }
}

function renderCrumbs(crumbs) {
  const box = $('crumbs');
  box.innerHTML = '';
  const list = crumbs && crumbs.length ? crumbs : [{ id: '-1', name: '全部文件' }];
  list.forEach((c, i) => {
    const b = document.createElement('button');
    b.textContent = c.name || '目录';
    b.onclick = () => loadFolder(c.id, 1);
    box.appendChild(b);
    if (i < list.length - 1) {
      const sep = document.createElement('span');
      sep.className = 'sep';
      sep.textContent = '/';
      box.appendChild(sep);
    }
  });
}

function renderList() {
  const kw = state.keyword.trim().toLowerCase();
  const items = state.files.filter((it) => !kw || it.name.toLowerCase().includes(kw));
  const box = $('list');
  box.innerHTML = '';
  $('count').textContent = items.length ? `${items.length} 项` : '';

  if (!items.length) {
    box.innerHTML = '<div class="empty">这个目录是空的</div>';
    return;
  }

  items.forEach((item) => {
    const row = document.createElement('div');
    row.className = 'row';

    const ico = iconFor(item);
    const icoEl = document.createElement('div');
    icoEl.className = `ico ${ico.cls}`;
    icoEl.textContent = ico.text;

    const meta = document.createElement('div');
    meta.className = 'meta';
    const name = document.createElement('div');
    name.className = 'name';
    name.textContent = item.name;
    const sub = document.createElement('div');
    sub.className = 'sub';
    sub.textContent = item.type === 'folder'
      ? (item.desc || '文件夹')
      : `${item.size || ''}${item.time ? ' · ' + item.time : ''}`;
    meta.appendChild(name);
    meta.appendChild(sub);

    row.appendChild(icoEl);
    row.appendChild(meta);

    if (item.type === 'folder') {
      row.onclick = () => loadFolder(item.id, 1);
    } else if (item.type === 'video') {
      const badge = document.createElement('span');
      badge.className = 'badge ' + (item.playable ? 'play' : '');
      badge.textContent = item.playable ? '可在线播放' : '建议下载';
      row.appendChild(badge);

      const playBtn = document.createElement('button');
      playBtn.className = 'playbtn';
      playBtn.textContent = '▶ 播放';
      playBtn.onclick = (ev) => {
        ev.stopPropagation();
        openPlayer(item);
      };
      row.appendChild(playBtn);
      row.onclick = () => openPlayer(item);
    } else {
      const dl = document.createElement('a');
      dl.className = 'badge';
      dl.textContent = '下载';
      dl.href = `/api/download/${item.id}?name=${encodeURIComponent(item.name)}`;
      dl.style.textDecoration = 'none';
      dl.onclick = (ev) => ev.stopPropagation();
      row.appendChild(dl);
      row.onclick = () => {
        window.location.href = `/api/download/${item.id}?name=${encodeURIComponent(item.name)}`;
      };
    }
    box.appendChild(row);
  });
}

function renderPager(page, pages) {
  const box = $('pager');
  box.innerHTML = '';
  if (!pages || pages <= 1) return;
  const prev = document.createElement('button');
  prev.textContent = '上一页';
  prev.disabled = page <= 1;
  prev.onclick = () => loadFolder(state.folderId, page - 1);
  const info = document.createElement('span');
  info.className = 'page-info';
  info.textContent = `${page} / ${pages}`;
  const next = document.createElement('button');
  next.textContent = '下一页';
  next.disabled = page >= pages;
  next.onclick = () => loadFolder(state.folderId, page + 1);
  box.appendChild(prev);
  box.appendChild(info);
  box.appendChild(next);
}

/* ---------------- 播放 ---------------- */
function openPlayer(item) {
  savePosition(state.videos[state.index]?.id);
  state.videos = state.files.filter((f) => f.type === 'video');
  state.index = state.videos.findIndex((v) => v.id === item.id);
  if (state.index < 0) {
    state.videos.push(item);
    state.index = state.videos.length - 1;
  }
  showView('player');
  renderPlaylist();
  loadVideo(state.index, true);
}

function loadVideo(index, reset = false) {
  const list = state.videos;
  if (index < 0 || index >= list.length) return;
  const item = list[index];
  const prevItem = state.index >= 0 ? list[state.index] : null;
  if (prevItem && prevItem.id !== item.id) savePosition(prevItem.id);
  state.index = index;

  const video = $('video');
  const tip = $('videoTip');
  $('nowTitle').textContent = item.name;
  $('btnDownload').href = `/api/download/${item.id}?name=${encodeURIComponent(item.name)}`;

  tip.hidden = item.playable;
  if (!item.playable) {
    tip.textContent = `该格式（${item.ext || '未知'}）浏览器可能无法解码，若无法播放请点“下载到本地”用本地播放器打开。`;
  }

  video.src = `/api/stream/${item.id}?ext=${encodeURIComponent(item.ext || 'mp4')}`;
  video.load();
  video.playbackRate = parseFloat($('speed').value) || 1;

  video.onloadedmetadata = () => {
    const pos = readPosition(item.id);
    if (pos > 3 && video.duration && pos < video.duration - 5) {
      video.currentTime = pos;
      toast(`已从 ${formatTime(pos)} 继续播放`);
    }
    video.onloadedmetadata = null;
  };

  video.play().catch(() => { /* 浏览器可能要求手动点播放 */ });
  renderPlaylist();
}

function renderPlaylist() {
  const ul = $('playlist');
  ul.innerHTML = '';
  if (!state.videos.length) {
    ul.innerHTML = '<li>当前目录没有视频</li>';
    return;
  }
  state.videos.forEach((v, i) => {
    const li = document.createElement('li');
    li.className = i === state.index ? 'active' : '';
    const no = document.createElement('span');
    no.className = 'no';
    no.textContent = String(i + 1).padStart(2, '0');
    const t = document.createElement('span');
    t.textContent = v.name.replace(/\.[^.]+$/, '');
    li.appendChild(no);
    li.appendChild(t);
    li.onclick = () => loadVideo(i, true);
    ul.appendChild(li);
  });
}

function formatTime(sec) {
  sec = Math.max(0, Math.floor(sec || 0));
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  const s = sec % 60;
  const pad = (n) => String(n).padStart(2, '0');
  return h ? `${h}:${pad(m)}:${pad(s)}` : `${m}:${pad(s)}`;
}

function posKey(id) { return `lz_pos_${id}`; }
function savePosition(id) {
  const video = $('video');
  if (!id || !video.duration || video.currentTime < 3) return;
  try {
    localStorage.setItem(posKey(id), String(Math.floor(video.currentTime)));
  } catch (e) { /* ignore */ }
}
function readPosition(id) {
  const v = parseFloat(localStorage.getItem(posKey(id)) || '0');
  return Number.isFinite(v) ? v : 0;
}

/* ---------------- 事件绑定 ---------------- */
$('btnLogin').onclick = doLogin;
$('password').addEventListener('keydown', (e) => { if (e.key === 'Enter') doLogin(); });
$('btnLogout').onclick = doLogout;
$('btnRefresh').onclick = () => loadFolder(state.folderId, state.page);
$('search').addEventListener('input', (e) => { state.keyword = e.target.value; renderList(); });
$('btnBack').onclick = () => {
  savePosition(state.videos[state.index]?.id);
  $('video').pause();
  showView('browse');
};
$('btnPrev').onclick = () => { if (state.index > 0) loadVideo(state.index - 1); else toast('已经是第一个了'); };
$('btnNext').onclick = () => {
  if (state.index < state.videos.length - 1) loadVideo(state.index + 1);
  else toast('已经是最后一个了');
};
$('speed').onchange = (e) => { $('video').playbackRate = parseFloat(e.target.value); };
$('btnCopy').onclick = async () => {
  const item = state.videos[state.index];
  if (!item) return;
  const url = `${location.origin}/api/stream/${item.id}?ext=${encodeURIComponent(item.ext || 'mp4')}`;
  try {
    await navigator.clipboard.writeText(url);
    toast('播放地址已复制，可粘贴到 VLC 或手机浏览器');
  } catch (e) {
    prompt('复制下面的地址：', url);
  }
};

const video = $('video');
let lastSave = 0;
video.addEventListener('timeupdate', () => {
  const now = Date.now();
  if (now - lastSave > 3000) {
    lastSave = now;
    savePosition(state.videos[state.index]?.id);
  }
});
video.addEventListener('error', () => {
  const item = state.videos[state.index];
  if (!item) return;
  $('videoTip').hidden = false;
  $('videoTip').textContent = '播放失败：可能直链已过期或该格式不被浏览器支持，点“下载到本地”试试。';
});
window.addEventListener('keydown', (e) => {
  if ($('playerView').hidden) return;
  if (['INPUT', 'TEXTAREA'].includes(document.activeElement.tagName)) return;
  const v = $('video');
  if (e.code === 'Space') { e.preventDefault(); v.paused ? v.play() : v.pause(); }
  else if (e.code === 'ArrowRight') { e.preventDefault(); v.currentTime = Math.min(v.duration || 1e9, v.currentTime + 10); }
  else if (e.code === 'ArrowLeft') { e.preventDefault(); v.currentTime = Math.max(0, v.currentTime - 10); }
  else if (e.code === 'ArrowUp') { e.preventDefault(); v.volume = Math.min(1, v.volume + 0.1); }
  else if (e.code === 'ArrowDown') { e.preventDefault(); v.volume = Math.max(0, v.volume - 0.1); }
  else if (e.key === 'f' || e.key === 'F') {
    if (document.fullscreenElement) document.exitFullscreen();
    else v.parentElement.requestFullscreen().catch(() => {});
  }
});
window.addEventListener('beforeunload', () => savePosition(state.videos[state.index]?.id));

bootstrap();
