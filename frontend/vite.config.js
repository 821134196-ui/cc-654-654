import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';

// 构建产物直接输出到 Quarkus 静态资源目录，由后端一并提供
export default defineConfig({
  plugins: [svelte()],
  base: '/',
  build: {
    outDir: '../src/main/resources/META-INF/resources',
    emptyOutDir: true
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080'
    }
  }
});
