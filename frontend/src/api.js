// 本地模拟认证：所有请求带 X-User-Id
let currentUserId = localStorage.getItem('userId') || '';

export function setUserId(id) {
  currentUserId = String(id);
  localStorage.setItem('userId', currentUserId);
}
export function getUserId() {
  return currentUserId;
}

async function request(method, url, body, isForm) {
  const opts = {
    method,
    headers: {},
  };
  if (currentUserId) opts.headers['X-User-Id'] = currentUserId;
  if (body !== undefined) {
    if (isForm) {
      opts.body = body; // FormData
    } else {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    }
  }
  const res = await fetch(url, opts);
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    throw new Error(data?.error || `请求失败 ${res.status}`);
  }
  return data;
}

export const api = {
  get: (u) => request('GET', u),
  post: (u, b) => request('POST', u, b ?? {}),
  put: (u, b) => request('PUT', u, b ?? {}),
  postForm: (u, fd) => request('POST', u, fd, true),
};

export function yuan(cents) {
  return (cents / 100).toFixed(2);
}

export function fmtTime(t) {
  if (!t) return '—';
  return String(t).replace('T', ' ').slice(0, 19);
}

export const STATUS_LABEL = {
  DRAFT: '草稿',
  PUBLISHED: '公示中',
  SUPERSEDED: '已被取代（旧版留存）',
  FINALIZED: '已封榜',
  SUBMITTED: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  OPEN: '待处理',
  UPHELD: '成立已重算',
  INITIAL: '首次公示',
  APPEAL: '异议重算',
  CORRECTION: '封榜后更正',
  MAKEUP: '补发',
  REFUND: '退回',
};

export function statusChip(s) {
  const map = {
    PUBLISHED: 'amber', FINALIZED: 'green', SUPERSEDED: 'gray', DRAFT: 'gray',
    SUBMITTED: 'amber', APPROVED: 'green', REJECTED: 'red', OPEN: 'amber', UPHELD: 'green',
    MAKEUP: 'green', REFUND: 'red',
  };
  return map[s] || 'gray';
}
