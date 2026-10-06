<script>
  import { onMount } from 'svelte';
  import { api, downloadAttachment } from '../api.js';
  import { toast } from '../stores/toast.js';
  import CycleSelect from './CycleSelect.svelte';
  import VersionTabs from './VersionTabs.svelte';
  import RankingTable from './RankingTable.svelte';

  export let me; // 当前登录用户

  let cycles = [];
  let cycleId = null;
  let cycle = null;
  let scores = null;
  let materials = [];
  let appeals = [];
  let versions = [];
  let selectedVersion = null;

  // 材料表单
  let mat = { itemKey: '', itemName: '', title: '', claimedPoints: 5, file: null };
  // 异议表单（按分项）
  let appealReason = {};

  onMount(loadCycles);

  async function loadCycles() {
    cycles = await api.get('/api/cycles');
    if (cycles.length && !cycleId) {
      await pickCycle(cycles[0].id);
    }
  }

  async function pickCycle(id) {
    cycleId = id;
    cycle = cycles.find((c) => c.id === id);
    await Promise.all([loadScores(), loadMaterials(), loadAppeals(), loadVersions()]);
    if (versions.length) selectedVersion = versions[versions.length - 1].versionNo;
  }

  async function loadScores() {
    try {
      scores = await api.get(`/api/cycles/${cycleId}/scores/${me.id}`);
    } catch (e) {
      scores = null;
    }
  }
  async function loadMaterials() {
    materials = await api.get(`/api/materials?cycleId=${cycleId}&studentId=${me.id}`);
  }
  async function loadAppeals() {
    appeals = await api.get(`/api/appeals?cycleId=${cycleId}&studentId=${me.id}`);
  }
  async function loadVersions() {
    versions = await api.get(`/api/cycles/${cycleId}/versions`);
  }
  $: currentVersion = versions.find((v) => v.versionNo === selectedVersion);

  function onFile(e) {
    mat.file = e.target.files[0] || null;
  }

  async function submitMaterial() {
    if (!mat.itemKey || !mat.title || mat.claimedPoints === '') {
      return toast('请填写分项、标题和申报加分', 'err');
    }
    const fd = new FormData();
    fd.append('cycleId', cycleId);
    fd.append('itemKey', mat.itemKey.toUpperCase());
    fd.append('itemName', mat.itemName || mat.itemKey);
    fd.append('title', mat.title);
    fd.append('claimedPoints', mat.claimedPoints);
    if (mat.file) fd.append('file', mat.file);
    try {
      await api.upload('/api/materials', fd);
      toast('加分材料已提交，等待辅导员审核');
      mat = { itemKey: '', itemName: '', title: '', claimedPoints: 5, file: null };
      await Promise.all([loadMaterials(), loadScores()]);
    } catch (e) {
      toast(e.message, 'err');
    }
  }

  async function submitAppeal(item) {
    const reason = appealReason[item.itemKey];
    if (!reason || !reason.trim()) return toast('请填写异议理由', 'err');
    try {
      await api.post('/api/appeals', { cycleId, itemKey: item.itemKey, reason });
      toast('异议已提交，公示期内等待教务裁定');
      appealReason[item.itemKey] = '';
      appealReason = appealReason;
      await loadAppeals();
    } catch (e) {
      toast(e.message, 'err');
    }
  }

  const inPublication = () => cycle && cycle.status === 'PUBLISHED';
  const statusBadge = { PENDING: 'amber', APPROVED: 'green', REJECTED: 'red' };
  const appealBadge = { PENDING: 'amber', UPHELD: 'green', REJECTED: 'red', WITHDRAWN: '' };
  const appealText = { PENDING: '待裁定', UPHELD: '成立', REJECTED: '驳回', WITHDRAWN: '已撤回' };
</script>

<div class="card">
  <h2>我的评定批次</h2>
  <CycleSelect cycles={cycles} selectedId={cycleId} on:change={(e) => pickCycle(e.detail)} />
  {#if cycle}
    <span class="badge blue" style="margin-left:10px">{cycle.status}</span>
    {#if cycle.rulesLocked}
      <span class="badge">规则已锁定：{cycle.tieRule} · 名额 {cycle.awardQuota}</span>
    {/if}
  {/if}
</div>

{#if scores}
  <div class="card">
    <h2>我的各分项得分与依据 <span class="muted small">（总分 {Number(scores.total).toFixed(2)}）</span></h2>
    <table>
      <thead>
        <tr>
          <th>分项</th><th class="right">基础分</th><th class="right">材料加分</th>
          <th class="right">小计</th><th style="width:30%">得分依据</th><th style="width:26%">公示异议</th>
        </tr>
      </thead>
      <tbody>
        {#each scores.items as item}
          <tr>
            <td><strong>{item.itemName}</strong><div class="muted small mono">{item.itemKey}</div></td>
            <td class="right mono">{Number(item.baseScore).toFixed(2)}</td>
            <td class="right mono">+{Number(item.bonusScore).toFixed(2)}</td>
            <td class="right mono"><strong>{Number(item.total).toFixed(2)}</strong></td>
            <td class="small">
              {item.evidence || '—'}
              {#if item.bonuses?.length}
                <ul class="bonus">
                  {#each item.bonuses as b}
                    <li>{b.materialTitle}：+{Number(b.awardedPoints).toFixed(1)}（{b.comment || '审核通过'}）</li>
                  {/each}
                </ul>
              {/if}
            </td>
            <td>
              {#if inPublication()}
                <textarea rows="2" placeholder="对该分项得分有异议？写明理由并提交"
                          bind:value={appealReason[item.itemKey]}></textarea>
                <button class="ghost" style="margin-top:4px" on:click={() => submitAppeal(item)}>提交异议</button>
              {:else}
                <span class="muted small">仅公示期可提交异议（当前 {cycle.status}）</span>
              {/if}
            </td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
{:else if cycle}
  <div class="card"><p class="muted">本批次暂无您的成绩数据。</p></div>
{/if}

<div class="grid2">
  <div class="card">
    <h2>我的加分材料</h2>
    <div class="row" style="margin-bottom:10px">
      <input placeholder="分项标识（如 COMPETITION）" bind:value={mat.itemKey} style="width:130px" />
      <input placeholder="分项名称" bind:value={mat.itemName} style="width:110px" />
      <input placeholder="材料标题" bind:value={mat.title} style="flex:1;min-width:120px" />
      <input type="number" step="0.5" bind:value={mat.claimedPoints} style="width:80px" title="申报加分" />
      <input type="file" on:change={onFile} />
      <button on:click={submitMaterial}>提交材料</button>
    </div>
    <table>
      <thead><tr><th>材料</th><th>状态</th><th>核定</th><th>审核依据</th><th>附件</th></tr></thead>
      <tbody>
        {#each materials as m}
          <tr>
            <td>{m.title}<div class="muted small">{m.itemName} · 申报 {m.claimedPoints}</div></td>
            <td><span class="badge {statusBadge[m.status]}">{m.status}</span></td>
            <td class="mono">{m.awardedPoints != null ? '+' + m.awardedPoints : '—'}</td>
            <td class="small">{m.reviewComment || '—'}<div class="muted">{m.reviewedByName || ''}</div></td>
            <td>
              {#if m.hasAttachment}
                <button class="subtle" on:click={() => downloadAttachment(`/api/attachments/material/${m.id}`)}>下载</button>
              {:else}无{/if}
            </td>
          </tr>
        {:else}
          <tr><td colspan="5" class="muted center">尚未提交材料</td></tr>
        {/each}
      </tbody>
    </table>
  </div>

  <div class="card">
    <h2>我的异议记录</h2>
    <table>
      <thead><tr><th>分项</th><th>状态</th><th>更正后</th><th>新榜单</th><th>裁定依据</th></tr></thead>
      <tbody>
        {#each appeals as a}
          <tr>
            <td>{a.itemName}</td>
            <td><span class="badge {appealBadge[a.status]}">{appealText[a.status]}</span></td>
            <td class="mono">{a.correctedScore != null ? a.correctedScore : '—'}</td>
            <td>{#if a.resultingVersion}<button class="subtle" on:click={() => (selectedVersion = a.resultingVersion)}>v{a.resultingVersion}</button>{:else}—{/if}</td>
            <td class="small">{a.decisionComment || '—'}</td>
          </tr>
        {:else}
          <tr><td colspan="5" class="muted center">暂无异议</td></tr>
        {/each}
      </tbody>
    </table>
  </div>
</div>

{#if versions.length}
  <div class="card">
    <h2>榜单版本（旧榜单永久可查，变化已标出）</h2>
    <VersionTabs versions={versions} selectedNo={selectedVersion}
                on:select={(e) => (selectedVersion = e.detail)} />
    {#if currentVersion}
      <p class="small muted" style="margin:6px 0 10px">
        生成依据：{currentVersion.basis}
      </p>
      <RankingTable entries={currentVersion.entries} highlightStudent={me.id} />
    {/if}
  </div>
{/if}

<style>
  .bonus { margin: 6px 0 0; padding-left: 16px; }
</style>
