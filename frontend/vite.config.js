import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';

export default defineConfig({
  plugins: [svelte()],
  server: {
    port: 5173,
    proxy: {
      // 本地开发也可走代理，避免跨域；前端默认直连 8080（CORS 已放开）
      '/api': 'http://localhost:8080'
    }
  }
});
