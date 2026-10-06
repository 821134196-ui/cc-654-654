<script>
  import { api, fmtTime, STATUS_LABEL, statusChip, yuan } from '../api.js';
  import { onMount } from 'svelte';

  export let me;
  export let TERM;

  let tab = 'scores';
  let versions = [];
  let selectedVersionId = null;
  let myRow = null;
  let ruleSnapshot = null;
  let materials = [];
  let appeals = [];
  let payments = [];
  let adjustments = [];
  let notifications = [];
  let error = '', ok = '';

  // 申诉表单
  let appealItem = '';
  let appealReason = '';
  // 材料表单
  let mItem = '', mTitle = '', mPoints = 0, mEvidence = '', mFile = null;

  onMount(load);

  async function load() {
    try {
      versions = await api.get(`/api/rankings/${TERM}/versions`);
      const cur = versions.find(v => v.status === 'PUBLISHED') || versions[versions.length - 1];
      if (cur) {
        selectedVersionId = cur.id;
        await selectVersion(cur.id);
      }
      await Promise.all([loadMaterials(), loadAppeals(), loadPayments(), loadNotifications()]);
    } catch (e) {
      error = e.message;
    }
  }

  async function selectVersion(id) {
    selectedVersionId = id;
    myRow = await api.get(`/api/rankings/versions/${id}/my-row`);
    const v = versions.find(x => x.id === id);
    ruleSnapshot = v ? JSON.parse(v.ruleSnapshotJson) : null;
  }

  function breakdown(row) {
    return row ? JSON.parse(row.itemBreakdownJson) : [];
  }

  async function loadMaterials() {
    materials = await api.get('/api/materials/mine');
  }
  async function loadAppeals() {
    appeals = await api.get('/api/appeals/mine');
  }
  async function loadPayments() {
    payments = await api.get('/api/disbursements');
    adjustments = await api.get('/api/disbursements/adjustments');
  }
  async function loadNotifications() {
    notifications = await api.get('/api/notifications');
  }

  async function submitAppeal() {
    error = ''; ok = '';
    try {
      await api.post('/api/appeals', {
        versionId: selectedVersionId,
        itemCode: appealItem || null,
        reason: appealReason,
      });
      appealItem = ''; appealReason = '';
      ok = '异议已提交，请等待教务处理。';
      await loadAppeals();
      await loadNotifications();
    } catch (e) { error = e.message; }
  }

  async function submitMaterial() {
    error = ''; ok = '';
    const fd = new FormData();
    fd.append('studentId', me.id);
    fd.append('itemCode', mItem);
    fd.append('title', mTitle);
    fd.append('claimedPoints', mPoints);
    fd.append('evidence', mEvidence);
    if (mFile) fd.append('file', mFile);
    try {
      await api.postForm('/api/materials', fd);
      mItem = ''; mTitle = ''; mPoints = 0; mEvidence = ''; mFile = null;
      ok = '材料已提交，等待辅导员审核。';
      await loadMaterials();
    } catch (e) { error = e.message; }
  }

  async function markRead(id) {
    await api.post(`/api/notifications/${id}/read`);
    await loadNotifications();
  }

  $: currentVersion = versions.find(v => v.id === selectedVersionId);
  $: canAppeal = currentVersion && currentVersion.status === 'PUBLISHED';
</script>

<div class="tabs">
  <button class:active={tab === 'scores'} on:click={() => tab = 'scores'}>我的得分与申诉</button>
  <button class:active={tab === 'materials'} on:click={() => tab = 'materials'}>我的加分材料</button>
  <button class:active={tab === 'payments'} on:click={() => tab = 'payments'}>发放与补退</button>
  <button class:active={tab === 'notifications'} on:click={() => tab = 'notifications'}>
    通知（{notifications.filter(n => !n.readFlag).length}）
  </button>
</div>

{#if error}<div class="error">{error}</div>{/if}
{#if ok}<div class="ok">{ok}</div>{/if}

{#if tab === 'scores'}
  <div class="panel">
    <h2>榜单版本与我的得分</h2>
    {#if versions.length === 0}
      <p class="muted">当前学期尚未公示榜单。</p>
    {:else}
      <div class="toolbar">
        <label style="margin:0">查看版本：</label>
        <select style="width:auto;min-width:260px" value={selectedVersionId}
                on:change={e => selectVersion(Number(e.target.value))}>
          {#each versions as v}
            <option value={v.id}>
              v{v.versionNo} · {STATUS_LABEL[v.status]} · {STATUS_LABEL[v.trigger]} · {fmtTime(v.createdAt)}
            </option>
          {/each}
        </select>
        <span class="chip {statusChip(currentVersion?.status)}">{STATUS_LABEL[currentVersion?.status]}</span>
      </div>
      <p class="muted">
        产生依据：{currentVersion?.reason || '—'}<br />
        并列规则：{ruleSnapshot?.tieRuleDesc} · 名额：{ruleSnapshot?.scholarshipCount} ·
        公示截止：{fmtTime(currentVersion?.appealDeadline)}
      </p>

      {#if myRow}
        <table>
          <tr><th style="width:110px">我的名次</th><td>
            第 <b>{myRow.rank}</b> 名
            {#if myRow.prevRank}
              （上一版第 {myRow.prevRank} 名，
                {#if myRow.rank < myRow.prevRank}<span class="delta-up">↑ 上升 {myRow.prevRank - myRow.rank}</span>
                {:else if myRow.rank > myRow.prevRank}<span class="delta-down">↓ 下降 {myRow.rank - myRow.prevRank}</span>
                {:else}名次未变{/if}）
            {/if}
            {#if myRow.changed}<span class="chip amber">本版有变化</span>{/if}
            {#if myRow.awardEligible}<span class="chip green">获奖名额内</span>{/if}
          </td></tr>
          <tr><th>总分</th><td><b>{myRow.totalScore}</b>
            {#if myRow.prevScore != null}<span class="muted">（上一版 {myRow.prevScore}）</span>{/if}
          </td></tr>
        </table>

        <h3>各分项得分与依据</h3>
        <table>
          <thead><tr><th>分项</th><th style="width:90px">得分/上限</th><th>评分依据</th></tr></thead>
          <tbody>
          {#each breakdown(myRow) as item}
            <tr>
              <td><b>{item.name}</b><div class="muted mono">{item.code}（{item.category === 'GPA' ? '绩点成绩' : '加分材料'}）</div></td>
              <td>{item.score} / {item.maxPoints}</td>
              <td><div class="basis">{item.basis}</div></td>
            </tr>
          {:else}
            <tr><td colspan="3" class="muted">暂无分项数据</td></tr>
          {/each}
          </tbody>
        </table>
      {:else}
        <p class="muted">该版本中未找到您的排名记录。</p>
      {/if}
    {/if}
  </div>

  <div class="panel">
    <h2>提交异议（公示期内）</h2>
    {#if !canAppeal}
      <p class="muted">仅在榜单「公示中」且未过公示截止时间时可提交异议。当前版本不可申诉。</p>
    {:else}
      <div class="row">
        <div>
          <label>异议分项（可空＝对总分/排名有异议）</label>
          <select bind:value={appealItem}>
            <option value="">— 总分/排名 —</option>
            {#each breakdown(myRow) as item}
              <option value={item.code}>{item.name}（{item.code}）</option>
            {/each}
          </select>
        </div>
      </div>
      <label>异议理由与依据</label>
      <textarea bind:value={appealReason} placeholder="请说明具体分项、存疑的得分或材料认定依据……"></textarea>
      <div style="margin-top:10px">
        <button on:click={submitAppeal} disabled={!appealReason.trim()}>提交异议</button>
      </div>
    {/if}

    <h3>我的申诉记录</h3>
    <table>
      <thead><tr><th>版本</th><th>分项</th><th>理由</th><th>状态</th><th>处理依据</th></tr></thead>
      <tbody>
      {#each appeals as a}
        <tr>
          <td>v{versions.find(v => v.id === a.versionId)?.versionNo ?? a.versionId}</td>
          <td>{a.itemCode || '总分/排名'}</td>
          <td>{a.reason}</td>
          <td><span class="chip {statusChip(a.status)}">{STATUS_LABEL[a.status]}</span></td>
          <td>{a.handleComment || '—'}</td>
        </tr>
      {:else}
        <tr><td colspan="5" class="muted">暂无申诉</td></tr>
      {/each}
      </tbody>
    </table>
  </div>
{/if}

{#if tab === 'materials'}
  <div class="panel">
    <h2>提交加分材料</h2>
    <div class="row">
      <div><label>分项代码</label><input bind:value={mItem} placeholder="如 COMP_SPORT" /></div>
      <div><label>材料标题</label><input bind:value={mTitle} placeholder="如 校运动会100米第一名" /></div>
      <div><label>申报分值</label><input type="number" step="0.5" bind:value={mPoints} /></div>
    </div>
    <label>情况说明</label>
    <textarea bind:value={mEvidence}></textarea>
    <label>证明附件（本地保存，可空）</label>
    <input type="file" on:change={e => mFile = e.target.files[0]} />
    <div style="margin-top:10px"><button on:click={submitMaterial} disabled={!mItem || !mTitle}>提交材料</button></div>
  </div>
  <div class="panel">
    <h2>我的材料</h2>
    <table>
      <thead><tr><th>标题</th><th>分项</th><th>申报/认定</th><th>状态</th><th>审核依据</th><th>附件</th></tr></thead>
      <tbody>
      {#each materials as m}
        <tr>
          <td>{m.title}</td>
          <td class="mono">{m.itemCode}</td>
          <td>{m.claimedPoints}{#if m.approvedPoints != null} / <b>{m.approvedPoints}</b>{/if}</td>
          <td><span class="chip {statusChip(m.status)}">{STATUS_LABEL[m.status]}</span></td>
          <td>{m.reviewComment || '—'}</td>
          <td>{#if m.attachmentPath}<a href={`/api/materials/${m.id}/attachment`} target="_blank">下载</a>{:else}—{/if}</td>
        </tr>
      {/each}
      {#if materials.length === 0}<tr><td colspan="6" class="muted">暂无材料</td></tr>{/if}
      </tbody>
    </table>
  </div>
{/if}

{#if tab === 'payments'}
  <div class="panel">
    <h2>我的奖学金记录</h2>
    <p class="muted">原发放记录一经生成不可修改；如封榜更正产生差额，以补发/退回记录体现。</p>
    <h3>原发放记录（不可变）</h3>
    <table>
      <thead><tr><th>榜单版本</th><th>名次</th><th>金额(元)</th><th>发放时间</th></tr></thead>
      <tbody>
      {#each payments as d}
        <tr><td>v{versions.find(v => v.id === d.versionId)?.versionNo ?? d.versionId}</td>
            <td>{d.rankAtPayment}</td><td>{yuan(d.amount)}</td><td>{fmtTime(d.paidAt)}</td></tr>
      {/each}
      {#if payments.length === 0}<tr><td colspan="4" class="muted">暂无发放</td></tr>{/if}
      </tbody>
    </table>
    <h3>补发 / 退回记录</h3>
    <table>
      <thead><tr><th>类型</th><th>金额(元)</th><th>依据</th><th>时间</th></tr></thead>
      <tbody>
      {#each adjustments as a}
        <tr>
          <td><span class="chip {statusChip(a.kind)}">{STATUS_LABEL[a.kind]}</span></td>
          <td>{yuan(a.amount)}</td><td>{a.reason}</td><td>{fmtTime(a.createdAt)}</td>
        </tr>
      {/each}
      {#if adjustments.length === 0}<tr><td colspan="4" class="muted">暂无补发/退回</td></tr>{/if}
      </tbody>
    </table>
  </div>
{/if}

{#if tab === 'notifications'}
  <div class="panel">
    <h2>通知（本地模拟，仅站内消息）</h2>
    {#each notifications as n}
      <div class="panel" style="margin-bottom:10px;padding:12px 14px">
        <b>{n.title}</b> {#if !n.readFlag}<span class="chip amber">未读</span>{/if}
        <div class="muted">{fmtTime(n.createdAt)}</div>
        <div>{n.content}</div>
        {#if !n.readFlag}<button class="small ghost" style="margin-top:6px" on:click={() => markRead(n.id)}>标记已读</button>{/if}
      </div>
    {/each}
    {#if notifications.length === 0}<p class="muted">暂无通知</p>{/if}
  </div>
{/if}
