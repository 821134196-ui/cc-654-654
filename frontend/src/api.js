// 后端 API 封装：本地用 X-User-Id 请求头模拟登录身份。
const BASE = import.meta.env.VITE_API_BASE || '';

let currentUserId = localStorage.getItem('userId') || '';

export function setUser(id) {
  currentUserId = id || '';
  if (id) localStorage.setItem('userId', id);
  else localStorage.removeItem('userId');
}
export function getUser() {
  return currentUserId;
}

async function request(method, path, { body, form, raw } = {}) {
  const headers = {};
  if (currentUserId) headers['X-User-Id'] = currentUserId;
  let payload;
  if (form) {
    payload = form; // FormData：浏览器自动设置 multipart boundary
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  const res = await fetch(BASE + path, { method, headers, body: payload });
  if (res.status === 204) return null;
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    const msg = data?.error || `请求失败（${res.status}）`;
    const err = new Error(msg);
    err.status = res.status;
    err.data = data;
    throw err;
  }
  return raw ? res : data;
}

export const api = {
  get: (p) => request('GET', p),
  post: (p, body) => request('POST', p, { body }),
  upload: (p, form) => request('POST', p, { form: form }),
  downloadUrl: (path) => BASE + path
};

export function authHeader() {
  return currentUserId;
}

/** 附件下载需要带 X-User-Id，用 fetch 转 blob 触发保存。 */
export async function downloadAttachment(path) {
  const res = await fetch(BASE + path, { headers: { 'X-User-Id': currentUserId } });
  if (!res.ok) {
    const d = await res.json().catch(() => ({}));
    throw new Error(d.error || '下载失败');
  }
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = path.split('/').pop();
  a.click();
  URL.revokeObjectURL(url);
}
