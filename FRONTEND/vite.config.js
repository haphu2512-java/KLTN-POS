import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // Đọc FRONTEND/.env (cả biến không có tiền tố VITE_, chỉ dùng trong file cấu hình này)
  const env = loadEnv(mode, fileURLToPath(new URL('.', import.meta.url)), '')
  const backendUrl = env.DEV_PROXY_TARGET || 'http://localhost:8080'

  return {
    plugins: [react()],
    server: {
      host: true, // Lắng nghe trên 0.0.0.0 (cho phép máy khác trong mạng LAN truy cập qua IP)
      port: Number(env.DEV_SERVER_PORT) || 5173,
      proxy: {
        '/api': {
          target: backendUrl,
          changeOrigin: true,
        },
        '/ws': {
          target: backendUrl.replace(/^http/, 'ws'),
          ws: true,
        },
      },
    },
  }
})
