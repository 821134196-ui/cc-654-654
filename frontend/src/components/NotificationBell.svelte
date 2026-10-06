<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';

  export let userId;
  let items = [];
  let open = false;

  onMount(load);
  async function load() {
    try { items = await api.get('/api/notifications'); } catch (e) { items = []; }
  }
  async function markRead(n) {
    if (!n.read) {
      await api.post(`/api/notifications/${n.id}/read`);
      load();
    }
  }
  const unread = () => items.filter((n) => !n.read).length;
  const catColor = {
    APPEAL_DECISION: 'amber', REVIEW: 'blue', PAYMENT: 'green', INFO: ''
  };
</script>

<div class="bell">
  <button class="subtle" on:click={() => (open = !open)}>🔔 {#if unread()}<span class="count">{unread()}</span>{/if}</button>
  {#if open}
    <div class="panel">
      {#if items.length === 0}<p class="muted small">暂无通知</p>{/if}
      {#each items as n}
        <button class="n" class:unread={!n.read} on:click={() => markRead(n)}>
          <span class="badge {catColor[n.category] || ''}">{n.category}</span>
          <strong>{n.title}</strong>
          <div class="muted small">{n.content}</div>
        </button>
      {/each}
    </div>
  {/if}
</div>

<style>
  .bell { position: relative; }
  .count {
    background: var(--down); color: #fff; border-radius: 999px;
    padding: 0 6px; font-size: 11px; margin-left: 2px;
  }
  .panel {
    position: absolute; right: 0; top: 38px; width: 340px; max-height: 420px; overflow: auto;
    background: #fff; border: 1px solid var(--line); border-radius: 10px;
    box-shadow: 0 8px 30px rgba(20, 30, 50, .15); padding: 8px; z-index: 30;
  }
  .n {
    display: block; width: 100%; text-align: left;
    background: transparent; border: none; color: inherit;
    padding: 8px; border-radius: 8px; cursor: pointer;
  }
  .n:hover { background: #f4f7fd; }
  .n.unread { background: #f7faff; }
</style>
