<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';
  import { toast } from '../stores/toast.js';
  import CycleSelect from './CycleSelect.svelte';
  import VersionTabs from './VersionTabs.svelte';
  import RankingTable from './RankingTable.svelte';

  export let me;

  let tab = 'cycles';
  let cycles = [];
  let cycleId = null;
  let cycle = null;

  let newCycleName = '';
  let lock = { tieRule: 'COMPETITION', awardQuota: 2, awardAmount: 5000, rulesSnapshot: '' };
  let deadlineDays = 7;

  let importText = '';
  let appeals = [];
  let decisions = {}; // appealId -> { correctedScore, comment }
  let corrections = [];
  let versions = [];
  let selectedVersion = null;
  let payments = { payments: [], adjustments: [] };

  let correction = { studentId: '', itemKey: 'ACADEMIC', newScore: 0, source: 'POST_CLOSE', reason: '' };
  let adjustment = { studentId: '', type: 'SUPPLEMENT', amount: 0, paymentId: null, correctionId: null, reason: '' };

  onMount(loadCycles);

  async function loadCycles() {
    cycles = await api.get('/api/cycles');
    if (cycles.length && !cycleId) await pickCycle(cycles[0].id);
  }
  async function pickCycle(id) {
    cycleId = id;
    cycle = cycles.find((c) => c.id === id);
    await loadTabData();
  }
  async function reloadCycle() {
    cycles = await api.get('/api/cycles');
    cycle = cycles.find((c) => c.id === cycleId);
  }

  async function loadTabData() {
    if (!cycleId) return;
    const [a, x, v, p] = await Promise.all([
      api.get(`/api/appeals?cycleId=${cycleId}`).catch(() => []),
      api.get(`/api/cycles/${cycleId}/corrections`).catch(() => []),
      api.get(`/api/cycles/${cycleId}/versions`).catch(() => []),
      api.get(`/api/payments/cycles/${cycleId}`).catch(() => { return { payments: [], adjustments: [] }; })
    ]);
    appeals = a;
    corrections = x;
    versions = v;
    payments = p;
    appeals.forEach((ap) => {
      if (!decisions[ap.id]) decisions[ap.id] = { correctedScore: '', comment: '' };
    });
    decisions = decisions;
    if (versions.length) selectedVersion = versions[versions.length - 1].versionNo;
  }
  $: currentVersion = versions.find((v) => v.versionNo === selectedVersion);

  async function createCycle() {
    if (!newCycleName.trim()) return toast('请输入批次名称', 'err');
    await api.post('/api/cycles', { name: newCycleName.trim() });
    newCycleName = '';
    toast('批次已创建（草稿，规则未锁定）', 'ok');
    await loadCycles();
  }

  async function lockRules() {
    try {
      await api.post(`/api/cycles/${cycleId}/lock`, lock);
      toast('排名规则已锁定，之后公示与重算都按此规则执行', 'ok');
      await reloadCycle();
    } catch (e) { toast(e.message, 'err'); }
  }

  async function publish() {
    try {
      await api.post(`/api/cycles/${cycleId}/publish`, {});
      toast('已公示并生成榜单 v1，学生可在公示期内提交异议', 'ok');
      await reloadCycle();
      await loadTabData();
      tab = 'ranking';
    } catch (e) { toast(e.message, 'err'); }
  }

  async function closeCycle() {
    if (!confirm('确认封榜？封榜后当前版本成为发放依据；此后更正只能生成新版本并走补发/退回。')) return;
    try {
      await api.post(`/api/cycles/${cycleId}/close`, {});
      toast('已封榜', 'ok');
      await reloadCycle();
      await loadTabData();
    } catch (e) { toast(e.message, 'err'); }
  }

  async function doImport() {
    let rows;
    try { rows = JSON.parse(importText); } catch (e) { return toast('JSON 解析失败：' + e.message, 'err'); }
    try {
      const r = await api.post(`/api/cycles/${cycleId}/import`, { rows });
      toast(`已导入 ${r.imported} 条成绩（规则锁定前可重复导入覆盖）`, 'ok');
    } catch (e) { toast(e.message, 'err'); }
  }

  async function decide(appealId, upheld) {
    const f = decisions[appealId];
    try {
      await api.post(`/api/appeals/${appealId}/decide`, {
        upheld,
        correctedScore: upheld ? Number(f.correctedScore) : null,
        comment: f.comment || (upheld ? '异议成立' : '异议驳回')
      });
      toast(upheld ? '异议成立：已写更正依据并按锁定规则重算新版本' : '已驳回', 'ok');
      await loadTabData();
      tab = 'ranking';
    } catch (e) { toast(e.message, 'err'); }
  }

  async function addCorrection() {
    try {
      const v = await api.post(`/api/cycles/${cycleId}/corrections`, {
        ...correction, newScore: Number(correction.newScore)
      });
      toast(`已生成新版本 v${v.versionNo}（${v.trigger}），旧版本保留`, 'ok');
      await loadTabData();
    } catch (e) { toast(e.message, 'err'); }
  }

  async function createPayments() {
    if (!confirm('按封榜版本生成整批发放记录？记录生成后不可修改，差额只能补发/退回。')) return;
    try {
      const r = await api.post(`/api/payments/cycles/${cycleId}/batch`, {});
      toast(`发放批次已生成：${r.batchNo}`, 'ok');
      await loadTabData();
    } catch (e) { toast(e.message, 'err'); }
  }

  async function addAdjustment() {
    try {
      await api.post(`/api/payments/cycles/${cycleId}/adjustments`, {
        ...adjustment,
        amount: Number(adjustment.amount),
        paymentId: adjustment.paymentId ? Number(adjustment.paymentId) : null,
        correctionId: adjustment.correctionId ? Number(adjustment.correctionId) : null
      });
      toast(adjustment.type === 'SUPPLEMENT' ? '补发已登记，原发放记录保持不变' : '退回已登记，原发放记录保持不变', 'ok');
      adjustment = { ...adjustment, amount: 0, reason: '' };
      await loadTabData();
    } catch (e) { toast(e.message, 'err'); }
  }

  const appealBadge = { PENDING: 'amber', UPHELD: 'green', REJECTED: 'red' };
  const appealText = { PENDING: '待裁定', UPHELD: '成立', REJECTED: '驳回' };
  const sourceText = { APPEAL: '公示异议', IMPORT_FIX: '导入勘误', POST_CLOSE: '封榜后更正' };

  const importPlaceholder = `[
  {"studentId":"S001","itemKey":"ACADEMIC","itemName":"学业成绩","score":85,"evidence":"GPA 导入"},
  {"studentId":"S002","itemKey":"ACADEMIC","itemName":"学业成绩","score":90,"evidence":"GPA 导入"}
]`;
</script>

<div class="card">
  <div class="row" style="justify-content:space-between">
    <h2 style="margin:0">教务工作台</h2>
    <CycleSelect cycles={cycles} selectedId={cycleId} on:change={(e) => pickCycle(e.detail)} />
  </div>
  {#if cycle}
    <div class="row" style="margin-top:10px">
      <span class="badge blue">{cycle.status}</span>
      <span class="badge">{cycle.rulesLocked ? `规则已锁定：${cycle.tieRule} · 名额 ${cycle.awardQuota} · ${cycle.awardAmount} 元` : '规则未锁定'}</span>
      {#if cycle.objectionDeadline}<span class="muted small">异议截止：{cycle.objectionDeadline}</span>{/if}
    </div>
  {/if}
  <div class="tabs" style="margin:12px 0 0">
    <button class:active={tab === 'cycles'} on:click={() => (tab = 'cycles')}>规则与批次</button>
    <button class:active={tab === 'import'} on:click={() => (tab = 'import')}>成绩导入(模拟)</button>
    <button class:active={tab === 'appeals'} on:click={() => (tab = 'appeals')}>异议裁定 {appeals.filter(a => a.status === 'PENDING').length ? `(${appeals.filter(a => a.status === 'PENDING').length})` : ''}</button>
    <button class:active={tab === 'ranking'} on:click={() => (tab = 'ranking')}>榜单版本</button>
    <button class:active={tab === 'corrections'} on:click={() => (tab = 'corrections')}>更正留痕</button>
    <button class:active={tab === 'payments'} on:click={() => (tab = 'payments')}>发放与补退</button>
  </div>
</div>

{#if tab === 'cycles'}
  <div class="grid2">
    <div class="card">
      <h2>新建批次</h2>
      <div class="row">
        <input placeholder="如：2026 国家奖学金" bind:value={newCycleName} style="flex:1" />
        <button on:click={createCycle}>创建</button>
      </div>
    </div>
    {#if cycle}
      <div class="card">
        <h2>锁定排名规则（公示前，仅可锁定一次）</h2>
        <div class="row">
          <label class="field">并列规则
            <select bind:value={lock.tieRule} disabled={cycle.rulesLocked}>
              <option value="COMPETITION">竞赛排名（1,1,3 跳号）</option>
              <option value="DENSE">密集排名（1,1,2 不跳号）</option>
              <option value="SEQUENTIAL">顺序排名（同分按学号决胜，不并列）</option>
            </select>
          </label>
          <label class="field">获奖名额
            <input type="number" min="1" bind:value={lock.awardQuota} disabled={cycle.rulesLocked} />
          </label>
          <label class="field">每人奖金
            <input type="number" min="0" bind:value={lock.awardAmount} disabled={cycle.rulesLocked} />
          </label>
        </div>
        <label class="field" style="margin-top:10px">规则文本快照
          <textarea rows="3" bind:value={lock.rulesSnapshot} disabled={cycle.rulesLocked}
                    placeholder="如：按总分排名；总分=学业+德育+竞赛加分；并列按所选规则处理。"></textarea>
        </label>
        <div class="row" style="margin-top:10px">
          <button on:click={lockRules} disabled={cycle.rulesLocked}>
            {cycle.rulesLocked ? '已锁定' : '锁定规则'}
          </button>
          <button class="ghost" on:click={publish} disabled={cycle.status !== 'DRAFT' || !cycle.rulesLocked}>
            公示并生成 v1
          </button>
          <button class="ghost" on:click={closeCycle} disabled={cycle.status !== 'PUBLISHED'}>封榜</button>
        </div>
        {#if cycle.rulesLocked}
          <p class="small muted" style="margin-bottom:0">锁定人 {cycle.lockedBy} · {cycle.lockedAt}<br />快照：{cycle.rulesSnapshot}</p>
        {/if}
      </div>
    {/if}
  </div>
{/if}

{#if tab === 'import'}
  <div class="card">
    <h2>本地模拟导入成绩</h2>
    <p class="muted small">粘贴 JSON 数组；字段：studentId、itemKey、itemName、score、evidence。规则锁定后禁止整批导入（须走更正留痕）。</p>
    <textarea rows="10" bind:value={importText} style="width:100%;font-family:monospace"
      placeholder={importPlaceholder}></textarea>
    <div style="margin-top:8px"><button on:click={doImport}>导入</button></div>
  </div>
{/if}

{#if tab === 'appeals'}
  <div class="card">
    <h2>公示异议裁定</h2>
    <table>
      <thead><tr><th>学生</th><th>分项</th><th>异议理由</th><th>状态</th><th style="width:260px">裁定（成立须填更正分+依据）</th></tr></thead>
      <tbody>
        {#each appeals as a}
          <tr>
            <td>{a.studentName}<div class="muted small mono">{a.studentId}</div></td>
            <td>{a.itemName}</td>
            <td class="small">{a.reason}</td>
            <td>
              <span class="badge {appealBadge[a.status]}">{appealText[a.status]}</span>
              {#if a.resultingVersion}<div class="small muted">→ v{a.resultingVersion}</div>{/if}
              {#if a.decisionComment}<div class="small">{a.decisionComment}</div>{/if}
            </td>
            <td>
              {#if a.status === 'PENDING'}
                <div class="row">
                  <input type="number" step="0.5" placeholder="更正后分值" style="width:100px"
                         bind:value={decisions[a.id].correctedScore} />
                  <input placeholder="裁定依据" style="flex:1;min-width:90px"
                         bind:value={decisions[a.id].comment} />
                </div>
                <div class="row" style="margin-top:6px">
                  <button on:click={() => decide(a.id, true)}>成立并重算</button>
                  <button class="danger" on:click={() => decide(a.id, false)}>驳回</button>
                </div>
              {:else}
                <span class="muted small">已由 {a.decidedByName} 裁定</span>
              {/if}
            </td>
          </tr>
        {:else}
          <tr><td colspan="5" class="muted center">暂无异议</td></tr>
        {/each}
      </tbody>
    </table>
  </div>
{/if}

{#if tab === 'ranking'}
  <div class="card">
    <h2>榜单版本（每次重算追加新版本，旧版可查，变化标出）</h2>
    <VersionTabs versions={versions} selectedNo={selectedVersion} on:select={(e) => (selectedVersion = e.detail)} />
    {#if currentVersion}
      <dl class="kv">
        <dt>并列规则</dt><dd>{currentVersion.tieRule}（锁定快照，名额 {currentVersion.awardQuota}）</dd>
        <dt>生成依据</dt><dd>{currentVersion.basis}</dd>
      </dl>
      <RankingTable entries={currentVersion.entries} />
    {:else}
      <p class="muted">尚未公示，没有榜单版本。</p>
    {/if}
  </div>
{/if}

{#if tab === 'corrections'}
  <div class="card">
    <h2>封榜后更正 / 导入勘误</h2>
    <div class="row">
      <input placeholder="学号" bind:value={correction.studentId} style="width:100px" />
      <input placeholder="分项" bind:value={correction.itemKey} style="width:120px" />
      <input type="number" step="0.5" placeholder="更正后分值" bind:value={correction.newScore} style="width:110px" />
      <select bind:value={correction.source}>
        <option value="POST_CLOSE">封榜后更正</option>
        <option value="IMPORT_FIX">导入勘误</option>
      </select>
      <input placeholder="处理依据（必填）" style="flex:1;min-width:160px" bind:value={correction.reason} />
      <button on:click={addCorrection}>更正并重算</button>
    </div>
    <p class="small muted">封榜后更正只追加新版本，不改变封榜标记；产生的奖金差额请到“发放与补退”登记。</p>
  </div>
  <div class="card">
    <h2>更正流水（排名变化依据）</h2>
    <table>
      <thead><tr><th>时间</th><th>学生</th><th>分项</th><th>原值</th><th>新值</th><th>来源</th><th>依据</th><th>操作人</th></tr></thead>
      <tbody>
        {#each corrections as c}
          <tr>
            <td class="small muted">{c.createdAt}</td>
            <td>{c.studentName}<div class="small mono">{c.studentId}</div></td>
            <td>{c.itemName}</td>
            <td class="mono">{c.oldScore}</td>
            <td class="mono"><strong>{c.newScore}</strong></td>
            <td><span class="badge purple">{sourceText[c.source] || c.source}</span></td>
            <td class="small">{c.reason}{#if c.appealId}<div class="muted">关联异议 #{c.appealId}</div>{/if}</td>
            <td class="small">{c.operatorName}</td>
          </tr>
        {:else}
          <tr><td colspan="8" class="muted center">暂无更正记录</td></tr>
        {/each}
      </tbody>
    </table>
  </div>
{/if}

{#if tab === 'payments'}
  <div class="card">
    <h2>奖学金发放</h2>
    <div class="row">
      <button on:click={createPayments} disabled={cycle.status !== 'CLOSED'}>按封榜版本生成发放批次</button>
      <span class="muted small">Payment 一经生成不可修改、不可删除；错误只能通过下方补发/退回处理差额。</span>
    </div>
    <table style="margin-top:10px">
      <thead><tr><th>发放ID</th><th>批次号</th><th>学生</th><th>原发放金额</th><th>净额(含补退)</th><th>发放时间</th></tr></thead>
      <tbody>
        {#each payments.payments as p}
          <tr>
            <td class="mono">{p.id}</td>
            <td class="small mono">{p.batchNo}</td>
            <td>{p.studentName}（{p.studentId}）</td>
            <td class="mono">{p.amount}</td>
            <td class="mono"><strong>{p.netAmount}</strong></td>
            <td class="small muted">{p.paidAt}</td>
          </tr>
        {:else}
          <tr><td colspan="6" class="muted center">尚未生成发放记录</td></tr>
        {/each}
      </tbody>
    </table>
  </div>

  <div class="card">
    <h2>补发 / 退回（只追加，不改动原记录）</h2>
    <div class="row">
      <input placeholder="学号" bind:value={adjustment.studentId} style="width:100px" />
      <select bind:value={adjustment.type}>
        <option value="SUPPLEMENT">补发 SUPPLEMENT</option>
        <option value="REFUND">退回 REFUND</option>
      </select>
      <input type="number" step="0.01" placeholder="金额(正数)" bind:value={adjustment.amount} style="width:110px" />
      <input type="number" placeholder="原发放ID(退回必填)" bind:value={adjustment.paymentId} style="width:130px" />
      <input type="number" placeholder="关联更正ID(可空)" bind:value={adjustment.correctionId} style="width:120px" />
      <input placeholder="处理依据（必填）" style="flex:1;min-width:160px" bind:value={adjustment.reason} />
      <button on:click={addAdjustment}>登记</button>
    </div>
    <p class="small muted">补发可无原发放记录（封榜后新进获奖线者）；退回必须关联原发放，且累计退回不得超过 已发放+已补发。</p>
    <table style="margin-top:10px">
      <thead><tr><th>时间</th><th>类型</th><th>学生</th><th>金额</th><th>原发放</th><th>关联更正</th><th>依据</th><th>登记人</th></tr></thead>
      <tbody>
        {#each payments.adjustments as a}
          <tr>
            <td class="small muted">{a.createdAt}</td>
            <td><span class="badge {a.type === 'SUPPLEMENT' ? 'green' : 'red'}">{a.type === 'SUPPLEMENT' ? '补发' : '退回'}</span></td>
            <td>{a.studentName}<div class="small mono">{a.studentId}</div></td>
            <td class="mono">{a.type === 'SUPPLEMENT' ? '+' : '−'}{a.amount}</td>
            <td class="mono">{a.paymentId ?? '—'}</td>
            <td class="mono">{a.correctionId ?? '—'}</td>
            <td class="small">{a.reason}</td>
            <td class="small">{a.createdByName}</td>
          </tr>
        {:else}
          <tr><td colspan="8" class="muted center">暂无补退记录</td></tr>
        {/each}
      </tbody>
    </table>
  </div>
{/if}
