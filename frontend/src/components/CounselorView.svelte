<script>
  import { onMount } from 'svelte';
  import { api, downloadAttachment } from '../api.js';
  import { toast } from '../stores/toast.js';
  import CycleSelect from './CycleSelect.svelte';
  import VersionTabs from './VersionTabs.svelte';
  import RankingTable from './RankingTable.svelte';

  export let me;

  let cycles = [];
  let cycleId = null;
  let cycle = null;
  let materials = [];
  let versions = [];
  let selectedVersion = null;
  let tab = 'pending';
  let reviewForms = {}; // materialId -> { awardedPoints, comment }

  onMount(loadCycles);

  async function loadCycles() {
    cycles = await api.get('/api/cycles');
    if (cycles.length && !cycleId) await pickCycle(cycles[0].id);
  }
  async function pickCycle(id) {
    cycleId = id;
    cycle = cycles.find((c) => c.id === id);
    await loadAll();
  }
  async function loadAll() {
    materials = await api.get(`/api/materials?cycleId=${cycleId}`);
    materials.forEach((m) => {
      if (m.status === 'PENDING' && !reviewForms[m.id]) {
        reviewForms[m.id] = { awardedPoints: m.claimedPoints, comment: '' };
      }
    });
    reviewForms = reviewForms;
    versions = await api.get(`/api/cycles/${cycleId}/versions`);
    if (versions.length) selectedVersion = versions[versions.length - 1].versionNo;
  }
  $: currentVersion = versions.find((v) => v.versionNo === selectedVersion);
  $: pending = materials.filter((m) => m.status === 'PENDING');
  $: shown = tab === 'pending' ? pending : materials;

  function ensureForm(m) {
    if (!reviewForms[m.id]) {
      reviewForms[m.id] = { awardedPoints: m.claimedPoints, comment: '' };
    }
  }
  void ensureForm;

  async function review(m, approved) {
    ensureForm(m);
    const f = reviewForms[m.id];
    try {
      await api.post(`/api/materials/${m.id}/review`, {
        approved,
        awardedPoints: approved ? Number(f.awardedPoints) : null,
        comment: f.comment || (approved ? '材料核验通过' : '材料不符合加分条件')
      });
      toast(approved ? '已审核通过（学生将收到通知）' : '已驳回', 'ok');
      await loadAll();
    } catch (e) {
      // 自审拦截会在这里以 403 明确提示
      toast(e.message, 'err', 6000);
    }
  }

  const statusBadge = { PENDING: 'amber', APPROVED: 'green', REJECTED: 'red' };
</script>

<div class="card">
  <h2>负责批次 · 加分材料审核</h2>
  <CycleSelect cycles={cycles} selectedId={cycleId} on:change={(e) => pickCycle(e.detail)} />
  {#if cycle}
    <span class="badge blue" style="margin-left:10px">{cycle.status}</span>
    <span class="badge">并列规则：{cycle.tieRule || '未锁定'}</span>
  {/if}
</div>

<div class="card">
  <div class="tabs">
    <button class:active={tab === 'pending'} on:click={() => (tab = 'pending')}>
      待审核 {pending.length ? `(${pending.length})` : ''}
    </button>
    <button class:active={tab === 'all'} on:click={() => (tab = 'all')}>全部材料</button>
  </div>

  <table>
    <thead>
      <tr>
        <th>学生</th><th>材料 / 分项</th><th>申报</th><th>提交人</th>
        <th>状态</th><th style="width:230px">审核操作（核定分 / 意见）</th><th>附件</th>
      </tr>
    </thead>
    <tbody>
      {#each shown as m}
        {@const selfSubmitted = m.submittedBy === me.id}
        <tr class:self={selfSubmitted && m.status === 'PENDING'}>
          <td>{m.studentName}<div class="muted small mono">{m.studentId}</div></td>
          <td>{m.title}<div class="muted small">{m.itemName}（{m.itemKey}）</div></td>
          <td class="mono">{m.claimedPoints}</td>
          <td>
            {m.submittedByName}
            {#if selfSubmitted}<div class="badge red" title="提交人不能审批自己提交的材料">本人提交 · 禁止自审</div>{/if}
          </td>
          <td>
            <span class="badge {statusBadge[m.status]}">{m.status}</span>
            {#if m.reviewedByName}<div class="muted small">审核：{m.reviewedByName}</div>{/if}
          </td>
          <td>
            {#if m.status === 'PENDING'}
              <div class="row">
                <input type="number" step="0.5" style="width:72px"
                       bind:value={reviewForms[m.id].awardedPoints} />
                <input placeholder="审核意见/依据" style="flex:1;min-width:90px"
                       bind:value={reviewForms[m.id].comment} />
                <button on:click={() => review(m, true)}>通过</button>
                <button class="danger" on:click={() => review(m, false)}>驳回</button>
              </div>
              {#if selfSubmitted}
                <div class="small self-warn">⚠ 该材料由你本人提交，系统将拦截你的审批（403），请由 {me.role === 'COUNSELOR' ? '另一位辅导员' : '其他审核人'} 处理。</div>
              {/if}
            {:else}
              <span class="small mono">{m.awardedPoints != null ? '+' + m.awardedPoints : ''} {m.reviewComment || ''}</span>
            {/if}
          </td>
          <td>
            {#if m.hasAttachment}
              <button class="subtle" on:click={() => downloadAttachment(`/api/attachments/material/${m.id}`)}>下载</button>
            {:else}无{/if}
          </td>
        </tr>
      {:else}
        <tr><td colspan="7" class="muted center">暂无材料</td></tr>
      {/each}
    </tbody>
  </table>
</div>

{#if versions.length}
  <div class="card">
    <h2>榜单版本（只读）</h2>
    <VersionTabs versions={versions} selectedNo={selectedVersion}
                on:select={(e) => (selectedVersion = e.detail)} />
    {#if currentVersion}
      <p class="small muted">依据：{currentVersion.basis}</p>
      <RankingTable entries={currentVersion.entries} />
    {/if}
  </div>
{/if}

<style>
  tr.self td { background: #fff8f8; }
  .self-warn { color: var(--down); margin-top: 4px; }
</style>
