import path from 'node:path'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver({ importStyle: 'css' })],
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    host: true,
    // host：'127.0.0.1',
    port: 5173,
  },
  build: {
    chunkSizeWarningLimit: 700,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('element-plus') || id.includes('@element-plus/icons-vue')) {
              return 'element-plus'
            }
            if (id.includes('echarts')) {
              return 'charts'
            }
            if (id.includes('axios')) {
              return 'http'
            }
            if (id.includes('vue') || id.includes('pinia') || id.includes('vue-router')) {
              return 'vue'
            }
          }
          return undefined
        },
      },
    },
  },
})
