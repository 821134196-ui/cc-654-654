<script>
  import { createEventDispatcher } from 'svelte';
  export let cycles = [];
  export let selectedId = null;
  const dispatch = createEventDispatcher();
</script>

<select value={selectedId} on:change={(e) => dispatch('change', Number(e.target.value))}>
  <option value={null}>— 选择评定批次 —</option>
  {#each cycles as c}
    <option value={c.id}>{c.name}（{c.id}）· {c.status}</option>
  {/each}
</select>
