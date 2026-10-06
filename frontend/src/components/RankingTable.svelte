<script>
  export let entries = [];
  export let highlightStudent = null;

  const changeLabel = {
    UP: '↑ 上升', DOWN: '↓ 下降', SAME: '—', NEW: '新上榜', OUT_AWARD: '掉出获奖线'
  };
  const changeClass = {
    UP: 'change-up', DOWN: 'change-down', SAME: 'change-same',
    NEW: 'change-new', OUT_AWARD: 'change-out'
  };
</script>

<table>
  <thead>
    <tr>
      <th style="width:64px">名次</th>
      <th>学号</th>
      <th>姓名</th>
      <th class="right">总分</th>
      <th style="width:90px">获奖</th>
      <th style="width:150px">相比上版</th>
    </tr>
  </thead>
  <tbody>
    {#each entries as e}
      <tr class:e-award={e.inAward} style={highlightStudent === e.studentId ? 'outline: 2px solid #f0c14b;' : ''}>
        <td><strong>{e.rank}</strong></td>
        <td class="mono">{e.studentId}</td>
        <td>{e.studentName}</td>
        <td class="right mono">{Number(e.totalScore).toFixed(2)}</td>
        <td>{#if e.inAward}<span class="badge green">在名额内</span>{:else}<span class="badge">未入选</span>{/if}</td>
        <td class={changeClass[e.change] || ''}>
          {#if e.prevRank != null}<span class="muted small">{e.prevRank} → </span>{/if}{changeLabel[e.change] || e.change}
        </td>
      </tr>
    {/each}
  </tbody>
</table>

<style>
  tr.e-award td { background: #f4fbf6; }
</style>
