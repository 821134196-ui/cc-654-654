<script>
  import { api, setUserId, getUserId } from './api.js';
  import { onMount } from 'svelte';
  import StudentView from './views/StudentView.svelte';
  import CounselorView from './views/CounselorView.svelte';
  import AcademicView from './views/AcademicView.svelte';

  let users = [];
  let me = null;
  let error = '';

  const TERM = '2026-2027-1';

  onMount(loadUsers);

  async function loadUsers() {
    try {
      users = await api.get('/api/users');
      const uid = getUserId();
      if (uid) await refreshMe();
    } catch (e) {
      error = e.message;
    }
  }

  async function refreshMe() {
    me = await api.get('/api/me');
    error = '';
  }

  async function switchUser(e) {
    setUserId(e.target.value);
    if (e.target.value) {
      await refreshMe();
    } else {
      me = null;
    }
  }
</script>

<header class="topbar">
  <h1>🎓 学生奖学金重算与申诉系统</h1>
  <span class="muted">学期 {TERM}</span>
  <div class="spacer"></div>
  <select on:change={switchUser} value={getUserId()}>
    <option value="">— 选择身份（本地模拟登录）—</option>
    {#each users as u}
      <option value={u.id}>{u.name}（{u.role === 'STUDENT' ? '学生' : u.role === 'COUNSELOR' ? '辅导员' : '教务'}）</option>
    {/each}
  </select>
</header>

<main>
  {#if error}<div class="error">{error}</div>{/if}

  {#if !me}
    <div class="panel">
      <h2>请先在右上角选择一个登录身份</h2>
      <p class="muted">
        系统内置演示账号：教务（王教务）、辅导员（李辅导员、赵辅导员）、学生（张三、李四、王五、赵六、钱七）。
        本地环境不设密码，通过 X-User-Id 模拟登录。
      </p>
    </div>
  {:else if me.role === 'STUDENT'}
    <StudentView {me} {TERM} />
  {:else if me.role === 'COUNSELOR'}
    <CounselorView {me} {TERM} />
  {:else}
    <AcademicView {me} {TERM} />
  {/if}
</main>
