import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': 'http://127.0.0.1:8080',
      // MCP Streamable HTTP 端点在后端；接入指引按 window.location.origin 生成，须经此代理可达
      '/mcp': 'http://127.0.0.1:8080',
    },
  },
});
