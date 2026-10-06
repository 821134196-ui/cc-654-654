<script>
  import { onMount } from 'svelte';
  import { api, getUser, setUser } from './api.js';
  import { toasts } from './stores/toast.js';
  import Login from './components/Login.svelte';
  import StudentView from './components/StudentView.svelte';
  import CounselorView from './components/CounselorView.svelte';
  import AdminView from './components/AdminView.svelte';
  import NotificationBell from './components/NotificationBell.svelte';

  let me = null;
  let booted = false;

  onMount(async () => {
    const id = getUser();
    if (id) {
      try {
        me = await api.get('/api/me');
      } catch (e) {
        setUser('');
        me = null;
      }
    }
    booted = true;
  });

  function logout() {
    setUser('');
    me = null;
  }

  const roleLabel = { STUDENT: '学生', COUNSELOR: '辅导员', ADMIN: '教务' };
</script>

{#if !booted}
  <div class="app-shell"><p class="muted">加载中…</p></div>
{:else if !me}
  <div class="app-shell">
    <Login />
  </div>
{:else}
  <div class="app-shell">
    <div class="topbar">
      <h1>学生奖学金重算与申诉系统</h1>
      <div class="who">
        <NotificationBell userId={me.id} />
        <span>{me.name}（{roleLabel[me.role]} · <span class="mono">{me.id}</span>）</span>
        <button class="subtle" on:click={logout}>切换身份</button>
      </div>
    </div>

    {#if me.role === 'STUDENT'}
      <StudentView me={me} />
    {:else if me.role === 'COUNSELOR'}
      <CounselorView me={me} />
    {:else}
      <AdminView me={me} />
    {/if}
  </div>
{/if}

<div class="toast">
  {#each $toasts as t}
    <div class="item {t.kind === 'err' ? 'err' : t.kind === 'ok' ? 'ok' : ''}">{t.message}</div>
  {/each}
</div>
