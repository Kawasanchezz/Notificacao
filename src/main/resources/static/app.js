'use strict';
const $ = id => document.getElementById(id);
const STATUSES = ['PENDING', 'IN_PROGRESS', 'DONE', 'CANCELED'];
// sessionStorage: the token disappears when the tab closes (smaller exposure window than localStorage).
let token = sessionStorage.getItem('token');
let registering = false;

function toast(msg, err) {
  const t = $('toast');
  t.textContent = msg;
  t.className = 'show' + (err ? ' err' : '');
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => { t.className = ''; }, 3500);
}

const esc = s => String(s ?? '').replace(/[&<>"']/g,
  c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

async function api(path, method = 'GET', body) {
  const res = await fetch(path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (res.status === 401 && token) { logout(); throw new Error('Sessão expirada'); }
  if (!res.ok) {
    const p = await res.json().catch(() => ({}));
    const fields = p.errors ? ' — ' + Object.values(p.errors).join('; ') : '';
    throw new Error((p.detail || 'Erro ' + res.status) + fields);
  }
  return res.status === 204 ? null : res.json();
}

const safe = fn => async (...args) => {
  try { await fn(...args); } catch (e) { toast(e.message, true); }
};

function render() {
  $('auth').classList.toggle('hidden', !!token);
  $('app').classList.toggle('hidden', !token);
  $('logout').classList.toggle('hidden', !token);
  if (token) safe(loadAll)();
}

function logout() {
  token = null;
  sessionStorage.removeItem('token');
  render();
}

function setMode(reg) {
  registering = reg;
  $('nameRow').classList.toggle('hidden', !reg);
  $('aName').required = reg;
  $('authBtn').textContent = reg ? 'Criar conta' : 'Entrar';
  $('tabLogin').className = reg ? 'ghost' : '';
  $('tabReg').className = reg ? '' : 'ghost';
}

async function loadAll() { await Promise.all([loadProfile(), loadTasks()]); }

async function loadProfile() {
  const p = await api('/api/users/me');
  $('pName').value = p.name;
  $('pEmail').textContent = p.email;
  $('addrs').innerHTML = p.addresses.map(a => `<div class="item"><div>${esc(a.street)}, ${esc(a.number)}
    ${a.complement ? '(' + esc(a.complement) + ')' : ''}<div class="muted">${esc(a.neighborhood)} · ${esc(a.city)}/${esc(a.state)} · CEP ${esc(a.cep)}</div></div>
    <button class="danger small" data-action="del-addr" data-id="${esc(a.id)}">Excluir</button></div>`).join('')
    || '<div class="muted">Nenhum endereço.</div>';
  $('phones').innerHTML = p.phones.map(x => `<div class="item"><div>${esc(x.number)} <span class="badge">${esc(x.type)}</span></div>
    <button class="danger small" data-action="del-phone" data-id="${esc(x.id)}">Excluir</button></div>`).join('')
    || '<div class="muted">Nenhum telefone.</div>';
}

async function loadTasks() {
  const f = $('tFilter').value;
  const r = await api('/api/tasks?size=100' + (f ? '&status=' + encodeURIComponent(f) : ''));
  $('tasks').innerHTML = r.items.map(t => `<div class="item"><div><strong>${esc(t.title)}</strong>
    <div class="muted">${esc(t.description || '')} ${t.scheduledFor ? '· ' + new Date(t.scheduledFor).toLocaleString('pt-BR') : ''}</div></div>
    <div class="row inline"><select data-action="set-status" data-id="${esc(t.id)}">
      ${STATUSES.map(s => `<option ${s === t.status ? 'selected' : ''}>${s}</option>`).join('')}</select>
      <button class="danger small" data-action="del-task" data-id="${esc(t.id)}">Excluir</button></div></div>`).join('')
    || '<div class="muted">Nenhuma tarefa.</div>';
}

$('logout').addEventListener('click', logout);
$('tabLogin').addEventListener('click', () => setMode(false));
$('tabReg').addEventListener('click', () => setMode(true));

$('authForm').addEventListener('submit', safe(async e => {
  e.preventDefault();
  const body = { email: $('aEmail').value, password: $('aPass').value };
  if (registering) body.name = $('aName').value;
  const r = await api(registering ? '/api/auth/register' : '/api/auth/login', 'POST', body);
  token = r.accessToken;
  sessionStorage.setItem('token', token);
  $('aPass').value = '';
  render();
}));

$('saveProfile').addEventListener('click', safe(async () => {
  await api('/api/users/me', 'PUT', { name: $('pName').value });
  toast('Perfil salvo');
}));

$('addrForm').addEventListener('submit', safe(async e => {
  e.preventDefault();
  await api('/api/users/me/addresses', 'POST',
    { cep: $('cep').value, number: $('num').value, complement: $('comp').value || null });
  e.target.reset();
  await loadProfile();
}));

$('phoneForm').addEventListener('submit', safe(async e => {
  e.preventDefault();
  await api('/api/users/me/phones', 'POST', { number: $('phNum').value, type: $('phType').value });
  e.target.reset();
  await loadProfile();
}));

$('taskForm').addEventListener('submit', safe(async e => {
  e.preventDefault();
  const when = $('tWhen').value;
  await api('/api/tasks', 'POST', {
    title: $('tTitle').value, description: $('tDesc').value || null,
    scheduledFor: when ? new Date(when).toISOString() : null,
  });
  e.target.reset();
  await loadTasks();
}));

$('tFilter').addEventListener('change', safe(loadTasks));

// One delegated listener instead of inline handlers, so the page works under a strict CSP.
document.addEventListener('click', safe(async e => {
  const el = e.target.closest('button[data-action]');
  if (!el) return;
  const id = encodeURIComponent(el.dataset.id);
  switch (el.dataset.action) {
    case 'del-addr': await api('/api/users/me/addresses/' + id, 'DELETE'); await loadProfile(); break;
    case 'del-phone': await api('/api/users/me/phones/' + id, 'DELETE'); await loadProfile(); break;
    case 'del-task': await api('/api/tasks/' + id, 'DELETE'); await loadTasks(); break;
  }
}));

document.addEventListener('change', safe(async e => {
  const el = e.target.closest('select[data-action="set-status"]');
  if (!el) return;
  await api(`/api/tasks/${encodeURIComponent(el.dataset.id)}/status`, 'PATCH', { status: el.value });
  toast('Status atualizado');
}));

render();
