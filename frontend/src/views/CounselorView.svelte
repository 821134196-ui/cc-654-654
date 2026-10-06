<script>
  import { api, fmtTime, STATUS_LABEL, statusChip } from '../api.js';
  import { onMount } from 'svelte';

  export let me;
  export let TERM;

  let tab = 'pending';
  let pending = [];
  let allMaterials = [];
  let appeals = [];
  let users = [];
  let error = '', ok = '';

  // 审核表单
  let reviewMap = {};
  // 代提交表单
  let sStudent = '', sItem = '', sTitle = '', sPoints = 0, sEvidence = '', sFile = null;

  onMount(load);

  async function load() {
    try {
      users = await api.get('/api/users?role=STUDENT');
      await refresh();
    } catch (e) { error = e.message; }
  }

  async function refresh() {
    pending = await api.get('/api/materials/pending');
    allMaterials = await api.get('/api/materials/pending'); // 待办为主，已审在全部列表
    appeals = await api.get('/api/appeals').catch(() => []);
    for (const m of pending) {
      if (!reviewMap[m.id]) {
        reviewMap[m.id] = { approved: true, points: m.claimedPoints, comment: '' };
      }
    }
  }

  async function review(id) {
    error = ''; ok = '';
    const f = reviewMap[id];
    try {
      await api.post(`/api/materials/${id}/review`, {
        approved: f.approved,
        approvedPoints: f.approved ? f.points : null,
        comment: f.comment,
      });
      ok = `材料 #${id} 审核完成。`;
      await refresh();
    } catch (e) { error = e.message; }
  }

  async function submitOnBehalf() {
    error = ''; ok = '';
    const fd = new FormData();
    fd.append('studentId', sStudent);
    fd.append('itemCode', sItem);
    fd.append('title', sTitle);
    fd.append('claimedPoints', sPoints);
    fd.append('evidence', sEvidence);
    if (sFile) fd.append('file', sFile);
    try {
      const m = await api.postForm('/api/materials', fd);
      ok = `已代提交材料 #${m.id}。注意：该材料您不能自己审批，须由其他辅导员处理。`;
      sItem = ''; sTitle = ''; sPoints = 0; sEvidence = ''; sFile = null;
      await refresh();
    } catch (e) { error = e.message; }
  }

  function studentName(id) {
    return users.find(u => u.id === id)?.name || id;
  }
  function isOwnSubmission(m) {
    return m.submittedBy === me.id;
  }
</script>

<div class="tabs">
  <button class:active={tab === 'pending'} on:click={() => tab = 'pending'}>待审材料（{pending.length}）</button>
  <button class:active={tab === 'submit'} on:click={() => tab = 'submit'}>代学生提交材料</button>
  <button class:active={tab === 'appeals'} on:click={() => tab = 'appeals'}>申诉进度（查看）</button>
</div>

{#if error}<div class="error">⛔ {error}</div>{/if}
{#if ok}<div class="ok">✅ {ok}</div>{/if}

{#if tab === 'pending'}
  <div class="panel">
    <h2>加分材料审核</h2>
    <p class="muted">
      红线：<b>审核人不能审批自己提交的材料</b>。自己（或代学生）提交的材料会标红，
      提交后须由其他辅导员审批，系统将强制拦截自审。
    </p>
    {#each pending as m}
      <div class="panel" style="background:{isOwnSubmission(m) ? '#fff7f6' : '#fff'}">
        <h2 style="margin-bottom:6px">
          《{m.title}》
          {#if isOwnSubmission(m)}<span class="chip red">您提交的材料：禁止自审</span>{/if}
        </h2>
        <p class="muted" style="margin:2px 0">
          学生：{studentName(m.studentId)} · 分项：<span class="mono">{m.itemCode}</span> ·
          申报：{m.claimedPoints} 分 · 提交时间：{fmtTime(m.submittedAt)}
        </p>
        <div class="basis">{m.evidence || '（无文字说明）'}</div>
        {#if m.attachmentPath}
          <p style="margin:8px 0"><a href={`/api/materials/${m.id}/attachment`} target="_blank">📎 查看附件：{m.attachmentName}</a></p>
        {/if}

        <div class="row" style="margin-top:10px">
          <div style="flex:0 0 150px">
            <label>审核结论</label>
            <select bind:value={reviewMap[m.id].approved}>
              <option value={true}>通过</option>
              <option value={false}>驳回</option>
            </select>
          </div>
          {#if reviewMap[m.id].approved}
            <div style="flex:0 0 150px">
              <label>认定分值</label>
              <input type="number" step="0.5" bind:value={reviewMap[m.id].points} />
            </div>
          {/if}
          <div>
            <label>处理依据（必填）</label>
            <input bind:value={reviewMap[m.id].comment} placeholder="如：校级竞赛一等奖，按规则第3条加8分" />
          </div>
        </div>
        <div style="margin-top:10px">
          <button class="small" on:click={() => review(m.id)}
                  disabled={isOwnSubmission(m) || !reviewMap[m.id].comment.trim()}>
            {isOwnSubmission(m) ? '提交人不能审核本材料' : '提交审核结果'}
          </button>
        </div>
      </div>
    {:else}
      <p class="muted">没有待审核材料。</p>
    {/each}
  </div>
{/if}

{#if tab === 'submit'}
  <div class="panel">
    <h2>代学生提交加分材料</h2>
    <p class="muted">辅导员可代提交，但提交后该材料会被自审规则锁定，需另一位辅导员审核。</p>
    <div class="row">
      <div>
        <label>学生</label>
        <select bind:value={sStudent}>
          <option value="">— 选择学生 —</option>
          {#each users as u}<option value={u.id}>{u.name}（{u.studentNo}）</option>{/each}
        </select>
      </div>
      <div><label>分项代码</label><input bind:value={sItem} placeholder="COMP_SPORT" /></div>
      <div><label>标题</label><input bind:value={sTitle} /></div>
      <div><label>申报分值</label><input type="number" step="0.5" bind:value={sPoints} /></div>
    </div>
    <label>情况说明</label>
    <textarea bind:value={sEvidence}></textarea>
    <label>附件（本地保存）</label>
    <input type="file" on:change={e => sFile = e.target.files[0]} />
    <div style="margin-top:10px">
      <button on:click={submitOnBehalf} disabled={!sStudent || !sItem || !sTitle}>代提交材料</button>
    </div>
  </div>
{/if}

{#if tab === 'appeals'}
  <div class="panel">
    <h2>学生申诉（教务处理，此处仅查看）</h2>
    <table>
      <thead><tr><th>ID</th><th>学生</th><th>分项</th><th>理由</th><th>状态</th><th>处理依据</th></tr></thead>
      <tbody>
      {#each appeals as a}
        <tr>
          <td>{a.id}</td><td>{studentName(a.studentId)}</td><td>{a.itemCode || '总分/排名'}</td>
          <td>{a.reason}</td>
          <td><span class="chip {statusChip(a.status)}">{STATUS_LABEL[a.status]}</span></td>
          <td>{a.handleComment || '—'}</td>
        </tr>
      {:else}
        <tr><td colspan="6" class="muted">暂无申诉</td></tr>
      {/each}
      </tbody>
    </table>
  </div>
{/if}
