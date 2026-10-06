<script>
  import { api, setUser } from '../api.js';
  import { toast } from '../stores/toast.js';

  let users = [];
  let selected = localStorage.getItem('userId') || '';
  let ready = false;

  async function load() {
    try {
      users = await api.get('/api/users');
    } catch (e) {
      // users 接口需要辅导员以上角色；登录前 401 属正常，使用内置演示账号兜底
      users = [];
    }
    ready = true;
  }
  load();

  const fallback = [
    { id: 'S001', name: '张三', role: 'STUDENT' },
    { id: 'S002', name: '李四', role: 'STUDENT' },
    { id: 'S003', name: '王五', role: 'STUDENT' },
    { id: 'S004', name: '赵六', role: 'STUDENT' },
    { id: 'T001', name: '陈辅导员', role: 'COUNSELOR' },
    { id: 'T002', name: '林辅导员', role: 'COUNSELOR' },
    { id: 'A001', name: '周教务', role: 'ADMIN' }
  ];
  $: allUsers = users.length ? users : fallback;

  function login() {
    if (!selected) return toast('请选择一个演示身份', 'err');
    setUser(selected);
    // 通知父组件刷新
    window.location.reload();
  }
</script>

<div class="card login-card">
  <h2>学生奖学金重算与申诉系统</h2>
  <p class="muted">本地演示环境通过选择身份模拟登录（请求头 <span class="mono">X-User-Id</span>）。</p>
  <div class="row">
    <select bind:value={selected} style="flex:1">
      <option value="">— 选择登录身份 —</option>
      {#each allUsers.filter(u => u.role === 'STUDENT') as u}
        <option value={u.id}>{u.name}（{u.id} · 学生）</option>
      {/each}
      {#each allUsers.filter(u => u.role === 'COUNSELOR') as u}
        <option value={u.id}>{u.name}（{u.id} · 辅导员）</option>
      {/each}
      {#each allUsers.filter(u => u.role === 'ADMIN') as u}
        <option value={u.id}>{u.name}（{u.id} · 教务）</option>
      {/each}
    </select>
    <button on:click={login}>进入系统</button>
  </div>
  {#if ready && users.length === 0}
    <p class="small muted" style="margin-bottom:0">未连接到后端或库为空，以上为内置演示账号；启动后端后会自动同步真实用户。</p>
  {/if}
</div>
