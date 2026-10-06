<script>
  import { api, fmtTime, STATUS_LABEL, statusChip, yuan } from '../api.js';
  import { onMount } from 'svelte';

  export let me;
  export let TERM;

  let tab = 'rules';
  let rule = null;
  let versions = [];
  let rows = [];
  let selectedVersionId = null;
  let appeals = [];
  let disbursements = [];
  let adjustments = [];
  let audit = [];
  let error = '', ok = '';

  // 规则表单
  let fTie = 'COMPETITION', fCount = 3, fAmount = 500000, fDays = 7;
  let iCode = '', iName = '', iCat = 'MATERIAL', iMax = 10, iOrder = 10;
  // 导入
  let importText = '';
  // 公示/封榜/更正/申诉/发放
  let publishReason = '', finalizeReason = '', correctReason = '';
  let handleMap = {};
  let payNote = '', reconcileReason = '';

  onMount(load);

  async function load() {
    try {
      await Promise.all([loadRule(), loadVersions(), loadDisbursements(), loadAudit(), loadAppeals()]);
      if (versions.length) await selectVersion(versions[versions.length - 1].id);
    } catch (e) { error = e.message; }
  }

  async function loadRule() {
    rule = await api.get(`/api/rules/${TERM}`);
    if (rule.ruleSet) {
      fTie = rule.ruleSet.tieRule; fCount = rule.ruleSet.scholarshipCount;
      fAmount = rule.ruleSet.awardAmount; fDays = rule.ruleSet.appealWindowDays;
    }
  }
  async function loadVersions() {
    versions = await api.get(`/api/rankings/${TERM}/versions`);
  }
  async function selectVersion(id) {
    selectedVersionId = id;
    rows = await api.get(`/api/rankings/versions/${id}/rows`);
  }
  async function loadAppeals() {
    appeals = await api.get('/api/appeals');
    for (const a of appeals.filter(x => x.status === 'OPEN')) {
      if (!handleMap[a.id]) handleMap[a.id] = { uphold: true, comment: '' };
    }
  }
  async function loadDisbursements() {
    disbursements = await api.get('/api/disbursements');
    adjustments = await api.get('/api/disbursements/adjustments');
  }
  async function loadAudit() {
    audit = await api.get('/api/audit');
  }

  function flash(e) { error = e.message; ok = ''; }
  function success(msg) { ok = msg; error = ''; }

  async function saveRule() {
    try {
      await api.put(`/api/rules/${TERM}`, {
        tieRule: fTie, scholarshipCount: Number(fCount),
        awardAmount: Number(fAmount), appealWindowDays: Number(fDays),
      });
      await loadRule(); success('规则已保存（尚未锁定，可继续修改）。');
    } catch (e) { flash(e); }
  }
  async function addItem() {
    try {
      await api.post(`/api/rules/${TERM}/items`, {
        code: iCode, name: iName, category: iCat,
        maxPoints: Number(iMax), sortOrder: Number(iOrder),
      });
      iCode = ''; iName = ''; iMax = 10; iOrder += 10;
      await loadRule(); success('分项已加入规则。');
    } catch (e) { flash(e); }
  }
  async function lockRule() {
    try {
      await api.post(`/api/rules/${TERM}/lock`);
      await loadRule(); success('规则已在公示前锁定，此后不可修改，并列规则随每个版本留痕。');
    } catch (e) { flash(e); }
  }
  async function doImport() {
    try {
      const scores = JSON.parse(importText);
      const r = await api.post('/api/import/scores', { term: TERM, scores });
      success(`本地模拟导入成功：${r.imported} 条成绩。`);
    } catch (e) { flash(e); }
  }
  async function publish() {
    try {
      const v = await api.post('/api/rankings/publish', { term: TERM, reason: publishReason });
      publishReason = '';
      await loadVersions(); await selectVersion(v.id);
      success(`榜单 v${v.versionNo} 已公示，公示期至 ${fmtTime(v.appealDeadline)}。`);
    } catch (e) { flash(e); }
  }
  async function handleAppeal(id) {
    try {
      const f = handleMap[id];
      const r = await api.post(`/api/appeals/${id}/handle`, { uphold: f.uphold, comment: f.comment });
      f.comment = '';
      await loadAppeals(); await loadVersions();
      if (r.newVersion && r.newVersion.id) await selectVersion(r.newVersion.id);
      success(f.uphold ? '异议成立，已重算生成新版本，旧版本保留可查。' : '异议已驳回并记录依据。');
    } catch (e) { flash(e); }
  }
  async function finalize() {
    try {
      const v = await api.post(`/api/rankings/versions/${selectedVersionId}/finalize`, { reason: finalizeReason });
      finalizeReason = '';
      await loadVersions(); await selectVersion(v.id);
      success(`v${v.versionNo} 已封榜，作为奖学金发放依据。`);
    } catch (e) { flash(e); }
  }
  async function correct() {
    try {
      const v = await api.post('/api/rankings/correct', { term: TERM, reason: correctReason });
      correctReason = '';
      await loadVersions(); await selectVersion(v.id);
      success(`已生成封榜更正版本 v${v.versionNo}，旧封榜版本与原发放记录均保留不变。`);
    } catch (e) { flash(e); }
  }
  async function pay() {
    try {
      const r = await api.post('/api/disbursements/pay', { versionId: selectedVersionId, note: payNote });
      payNote = '';
      await loadDisbursements();
      success(`已按封榜版本生成 ${r.paidCount} 条原发放记录（不可修改）。`);
    } catch (e) { flash(e); }
  }
  async function reconcile() {
    try {
      const r = await api.post('/api/disbursements/reconcile', {
        correctionVersionId: selectedVersionId, reason: reconcileReason,
      });
      reconcileReason = '';
      await loadDisbursements();
      success(`已生成 ${r.adjustmentCount} 条补发/退回记录，原发放记录保持不变。`);
    } catch (e) { flash(e); }
  }

  $: currentVersion = versions.find(v => v.id === selectedVersionId);
  $: locked = rule?.ruleSet?.status === 'LOCKED';
  $: selectedRows = rows;
</script>

<div class="tabs">
  <button class:active={tab === 'rules'} on:click={() => tab = 'rules'}>① 规则锁定</button>
  <button class:active={tab === 'import'} on:click={() => tab = 'import'}>② 成绩导入(模拟)</button>
  <button class:active={tab === 'ranking'} on:click={() => tab = 'ranking'}>③ 榜单/版本/变化</button>
  <button class:active={tab === 'appeals'} on:click={() => tab = 'appeals'}>④ 异议处理（{appeals.filter(a => a.status === 'OPEN').length}）</button>
  <button class:active={tab === 'payment'} on:click={() => tab = 'payment'}>⑤ 发放与补退</button>
  <button class:active={tab === 'audit'} on:click={() => tab = 'audit'}>处理依据(审计)</button>
</div>

{#if error}<div class="error">⛔ {error}</div>{/if}
{#if ok}<div class="ok">✅ {ok}</div>{/if}

{#if tab === 'rules'}
  <div class="panel">
    <h2>排名规则（公示前锁定）
      {#if locked}<span class="chip green">已锁定：{rule.ruleSet.lockedBy} · {fmtTime(rule.ruleSet.lockedAt)}</span>
      {:else}<span class="chip amber">草稿</span>{/if}
    </h2>
    <div class="row">
      <div>
        <label>并列名次规则（锁定后不可改）</label>
        <select bind:value={fTie} disabled={locked}>
          <option value="COMPETITION">竞赛排名：并列占用名次（1,2,2,4）</option>
          <option value="DENSE">密集排名：并列不占名次（1,2,2,3）</option>
        </select>
      </div>
      <div><label>获奖名额</label><input type="number" bind:value={fCount} disabled={locked} /></div>
      <div><label>每人奖金额（分）</label><input type="number" bind:value={fAmount} disabled={locked} /></div>
      <div><label>公示期天数</label><input type="number" bind:value={fDays} disabled={locked} /></div>
    </div>
    <div style="margin-top:10px"><button on:click={saveRule} disabled={locked}>保存规则</button></div>

    <h3>评分分项</h3>
    <table>
      <thead><tr><th>代码</th><th>名称</th><th>类别</th><th>上限分</th><th>排序</th></tr></thead>
      <tbody>
      {#each rule?.items || [] as it}
        <tr><td class="mono">{it.code}</td><td>{it.name}</td>
            <td>{it.category === 'GPA' ? '绩点成绩' : '加分材料'}</td><td>{it.maxPoints}</td><td>{it.sortOrder}</td></tr>
      {:else}
        <tr><td colspan="5" class="muted">尚未配置分项（至少需一个 GPA 分项才能锁定）</td></tr>
      {/each}
      </tbody>
    </table>

    {#if !locked}
      <h3>增加分项</h3>
      <div class="row">
        <div><label>代码</label><input bind:value={iCode} placeholder="GPA / COMP_SPORT" /></div>
        <div><label>名称</label><input bind:value={iName} /></div>
        <div>
          <label>类别</label>
          <select bind:value={iCat}>
            <option value="GPA">绩点成绩</option>
            <option value="MATERIAL">加分材料</option>
          </select>
        </div>
        <div><label>上限分</label><input type="number" step="0.5" bind:value={iMax} /></div>
        <div><label>排序</label><input type="number" bind:value={iOrder} /></div>
      </div>
      <div style="margin-top:10px">
        <button class="ghost" on:click={addItem}>增加分项</button>
        <button on:click={lockRule}>🔒 公示前锁定规则</button>
      </div>
    {/if}
  </div>
{/if}

{#if tab === 'import'}
  <div class="panel">
    <h2>成绩导入（本地模拟接口）</h2>
    <p class="muted">
      粘贴 JSON 数组模拟教务系统推送。字段：studentUsername、courseName、credit、score。
      同一学生同一学期重复导入会覆盖旧数据，便于演示“异议成立后重算”。
    </p>
    <textarea rows="10" bind:value={importText} placeholder='[&#10;  &#123;"studentUsername":"s1","courseName":"高等数学","credit":4,"score":92&#125;,&#10;  &#123;"studentUsername":"s2","courseName":"高等数学","credit":4,"score":88&#125;&#10;]'
      style="font-family:ui-monospace,monospace;font-size:12.5px"></textarea>
    <div style="margin-top:10px"><button on:click={doImport}>导入成绩</button></div>
  </div>
{/if}

{#if tab === 'ranking'}
  <div class="panel">
    <h2>榜单版本（每次重算/更正均保留旧版可查）</h2>
    <div class="toolbar">
      <select style="width:auto;min-width:300px" value={selectedVersionId}
              on:change={e => selectVersion(Number(e.target.value))}>
        {#each versions as v}
          <option value={v.id}>
            v{v.versionNo} · {STATUS_LABEL[v.status]} · {STATUS_LABEL[v.trigger]} · {fmtTime(v.createdAt)}
          </option>
        {/each}
      </select>
      {#if currentVersion}
        <span class="chip {statusChip(currentVersion.status)}">{STATUS_LABEL[currentVersion.status]}</span>
        <span class="chip gray">{STATUS_LABEL[currentVersion.trigger]}</span>
      {/if}
    </div>
    {#if currentVersion}
      <p class="muted">
        产生依据：{currentVersion.reason || '—'}<br />
        规则快照：并列={currentVersion.tieRule}，名额={currentVersion.scholarshipCount}，
        奖金额={yuan(currentVersion.awardAmount)} 元，公示截止={fmtTime(currentVersion.appealDeadline)}，
        封榜时间={fmtTime(currentVersion.finalizedAt)}
      </p>
    {/if}

    <table>
      <thead><tr>
        <th style="width:60px">名次</th><th>学生</th><th style="width:90px">总分</th>
        <th style="width:130px">相对上一版</th><th style="width:110px">获奖资格</th><th>分项依据</th>
      </tr></thead>
      <tbody>
      {#each selectedRows as r}
        <tr style="background:{r.changed ? '#fffbe9' : '#fff'}">
          <td><b>{r.rank}</b></td>
          <td>{r.studentName}<div class="muted">{r.studentNo}</div></td>
          <td>{r.totalScore}
            {#if r.prevScore != null}<div class="muted">上版 {r.prevScore}</div>{/if}
          </td>
          <td>
            {#if r.prevRank == null}<span class="muted">首版</span>
            {:else if !r.changed}<span class="muted">无变化</span>
            {:else}
              {#if r.rank < r.prevRank}<span class="delta-up">↑ {r.prevRank} → {r.rank}</span>
              {:else if r.rank > r.prevRank}<span class="delta-down">↓ {r.prevRank} → {r.rank}</span>
              {:else}<span class="chip amber">分数变化</span>{/if}
            {/if}
          </td>
          <td>{#if r.awardEligible}<span class="chip green">名额内</span>{:else}<span class="chip gray">名额外</span>{/if}</td>
          <td>
            <details>
              <summary>查看 {JSON.parse(r.itemBreakdownJson).length} 个分项依据</summary>
              {#each JSON.parse(r.itemBreakdownJson) as it}
                <div style="margin:6px 0">
                  <b>{it.name}</b>（{it.code}）：{it.score}/{it.maxPoints}
                  <div class="basis">{it.basis}</div>
                </div>
              {/each}
            </details>
          </td>
        </tr>
      {:else}
        <tr><td colspan="6" class="muted">暂无版本，请先锁定规则并公示。</td></tr>
      {/each}
      </tbody>
    </table>

    <h3>版本操作</h3>
    {#if !currentVersion}
      <label>公示备注（依据）</label>
      <input bind:value={publishReason} placeholder="按已锁定的 2026-2027-1 学年规则公示" />
      <button on:click={publish} disabled={!locked}>首次公示</button>
      {#if !locked}<span class="muted"> 需先在「规则锁定」页锁定规则</span>{/if}
    {:else if currentVersion.status === 'PUBLISHED'}
      <label>封榜备注</label>
      <input bind:value={finalizeReason} placeholder="公示期满、异议处理完毕，准予封榜" />
      <button on:click={finalize}>封榜（成为发放依据）</button>
    {:else if currentVersion.status === 'FINALIZED'}
      <div class="row">
        <div>
          <label>封榜后更正原因（必填，原记录不动）</label>
          <input bind:value={correctReason} placeholder="如：王五补录省级竞赛获奖材料经复核通过，需重新核算" />
        </div>
      </div>
      <button class="danger" on:click={correct} disabled={!correctReason.trim()}>生成封榜更正版本</button>
      <span class="muted"> 更正后请到「发放与补退」按差额生成补发/退回记录</span>
    {:else}
      <p class="muted">该版本为被取代的历史版本，仅可查看。</p>
    {/if}
  </div>
{/if}

{#if tab === 'appeals'}
  <div class="panel">
    <h2>公示异议处理</h2>
    {#each appeals.filter(a => a.status === 'OPEN') as a}
      <div class="panel" style="background:#fffbe9">
        <h2>异议 #{a.id} · 学生ID {a.studentId} · 分项 {a.itemCode || '总分/排名'}</h2>
        <div class="basis">{a.reason}</div>
        <p class="muted">提出时间：{fmtTime(a.createdAt)}</p>
        <div class="row">
          <div style="flex:0 0 160px">
            <label>结论</label>
            <select bind:value={handleMap[a.id].uphold}>
              <option value={true}>成立 → 重新计算排名</option>
              <option value={false}>驳回</option>
            </select>
          </div>
          <div>
            <label>处理依据（必填）</label>
            <input bind:value={handleMap[a.id].comment} placeholder="如：经核验，该生竞赛证书真实有效，按规则应加 8 分" />
          </div>
        </div>
        <div style="margin-top:10px">
          <button class="small" on:click={() => handleAppeal(a.id)} disabled={!handleMap[a.id].comment.trim()}>
            提交处理结论
          </button>
        </div>
      </div>
    {:else}
      <p class="muted">没有待处理异议。</p>
    {/each}

    <h3>已处理异议</h3>
    <table>
      <thead><tr><th>ID</th><th>学生ID</th><th>分项</th><th>结论</th><th>处理依据</th><th>新版本</th></tr></thead>
      <tbody>
      {#each appeals.filter(a => a.status !== 'OPEN') as a}
        <tr>
          <td>{a.id}</td><td>{a.studentId}</td><td>{a.itemCode || '总分/排名'}</td>
          <td><span class="chip {statusChip(a.status)}">{STATUS_LABEL[a.status]}</span></td>
          <td>{a.handleComment}</td><td>{a.newVersionId ? 'v' + (versions.find(v => v.id === a.newVersionId)?.versionNo || a.newVersionId) : '—'}</td>
        </tr>
      {/each}
      </tbody>
    </table>
  </div>
{/if}

{#if tab === 'payment'}
  <div class="panel">
    <h2>奖学金发放</h2>
    {#if currentVersion?.status === 'FINALIZED'}
      <p class="muted">
        当前选中版本 v{currentVersion.versionNo}
        （{STATUS_LABEL[currentVersion.trigger]}）。发放生成的原记录不可修改。
      </p>
      {#if currentVersion.trigger === 'CORRECTION'}
        <label>补退处理依据（必填）</label>
        <input bind:value={reconcileReason} placeholder="如：封榜更正后张三落榜需退回、钱七新上榜需补发" />
        <button class="danger" on:click={reconcile} disabled={!reconcileReason.trim()}>按更正版本生成补发/退回</button>
      {:else}
        <label>发放备注</label>
        <input bind:value={payNote} placeholder="按封榜 v{currentVersion.versionNo} 统一发放" />
        <button on:click={pay}>按本封榜版本发放</button>
      {/if}
    {:else}
      <p class="muted">请先在「榜单/版本/变化」页选择一个已封榜版本。</p>
    {/if}

    <h3>原发放记录（永久不变）</h3>
    <table>
      <thead><tr><th>ID</th><th>学生</th><th>发放版本</th><th>名次</th><th>金额(元)</th><th>时间</th><th>操作人</th></tr></thead>
      <tbody>
      {#each disbursements as d}
        <tr><td>{d.id}</td><td>{d.studentId}</td><td>v{versions.find(v => v.id === d.versionId)?.versionNo ?? d.versionId}</td>
            <td>{d.rankAtPayment}</td><td>{yuan(d.amount)}</td><td>{fmtTime(d.paidAt)}</td><td>{d.createdBy}</td></tr>
      {:else}<tr><td colspan="7" class="muted">暂无发放记录</td></tr>{/each}
      </tbody>
    </table>

    <h3>补发 / 退回记录（差额留痕）</h3>
    <table>
      <thead><tr><th>ID</th><th>学生</th><th>类型</th><th>金额(元)</th><th>原发放</th><th>更正版本</th><th>依据</th><th>操作人</th></tr></thead>
      <tbody>
      {#each adjustments as a}
        <tr>
          <td>{a.id}</td><td>{a.studentId}</td>
          <td><span class="chip {statusChip(a.kind)}">{STATUS_LABEL[a.kind]}</span></td>
          <td>{yuan(a.amount)}</td><td>{a.disbursementId || '—（新上榜）'}</td>
          <td>v{versions.find(v => v.id === a.correctionVersionId)?.versionNo ?? a.correctionVersionId}</td>
          <td>{a.reason}</td><td>{a.createdBy}</td>
        </tr>
      {:else}<tr><td colspan="8" class="muted">暂无补发/退回记录</td></tr>{/each}
      </tbody>
    </table>
  </div>
{/if}

{#if tab === 'audit'}
  <div class="panel">
    <h2>处理依据（审计日志）</h2>
    <p class="muted">规则锁定、材料审核、自审拦截、公示重算、封榜更正、发放与补退全程留痕。</p>
    <table>
      <thead><tr><th style="width:150px">时间</th><th style="width:110px">操作人</th><th style="width:180px">动作</th><th>详情与依据</th></tr></thead>
      <tbody>
      {#each audit as l}
        <tr>
          <td class="muted">{fmtTime(l.createdAt)}</td>
          <td>{l.actorName}</td>
          <td class="mono" style="font-size:12px">{l.action}</td>
          <td style="white-space:pre-line">{l.detail}</td>
        </tr>
      {:else}<tr><td colspan="4" class="muted">暂无日志</td></tr>{/each}
      </tbody>
    </table>
  </div>
{/if}
