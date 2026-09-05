import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig(({ mode }) => ({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  esbuild: mode === 'production' ? { drop: ['console', 'debugger'] } : {},
  build: {
    target: 'es2022',
    chunkSizeWarningLimit: 500,
    rollupOptions: {
      output: {
        manualChunks (id) {
          if (!id.includes('node_modules')) return undefined
          if (id.includes('/vue/') || id.includes('/vue-router/') || id.includes('/pinia/')) return 'vue-vendor'
          if (id.includes('/element-plus/')) return 'element-plus'
          if (id.includes('/echarts/lib/chart/bar/') || id.includes('/echarts/lib/chart/line/') || id.includes('/echarts/lib/chart/pie/')) return 'echarts-charts'
          if (id.includes('/echarts/lib/component/grid/') || id.includes('/echarts/lib/component/tooltip/') || id.includes('/echarts/lib/component/legend/') || id.includes('/echarts/lib/component/graphic/')) return 'echarts-components'
          if (id.includes('/echarts/') || id.includes('/zrender/')) return 'echarts-runtime'
          if (id.includes('/@tiptap/')) return 'tiptap'
          if (id.includes('/marked/') || id.includes('/dompurify/')) return 'content-vendor'
          return undefined
        }
      }
    }
  },
  optimizeDeps: {
    esbuildOptions: {
      target: 'es2022'
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      },
      '/ws': {
        target: 'http://localhost:8081',
        ws: true,
        changeOrigin: true
      }
    }
  }
}))
