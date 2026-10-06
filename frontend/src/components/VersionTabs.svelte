<script>
  import { createEventDispatcher } from 'svelte';
  export let versions = [];
  export let selectedNo = null;
  const dispatch = createEventDispatcher();

  const triggerLabel = {
    INITIAL: '首次公示', RECALC: '异议重算', POST_CLOSE: '封榜后更正'
  };
</script>

<div class="tabs">
  {#each versions as v}
    <button class:active={v.versionNo === selectedNo}
            on:click={() => dispatch('select', v.versionNo)}>
      v{v.versionNo} · {triggerLabel[v.trigger] || v.trigger}
      {#if v.sealedFlag}<span title="封榜版本">🔒</span>{/if}
    </button>
  {/each}
</div>
