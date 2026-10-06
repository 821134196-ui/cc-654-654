import { writable } from 'svelte/store';

let seq = 0;
export const toasts = writable([]);

export function toast(message, kind = 'info', ttl = 4000) {
  const id = ++seq;
  toasts.update((list) => [...list, { id, message, kind }]);
  setTimeout(() => toasts.update((list) => list.filter((t) => t.id !== id)), ttl);
}
