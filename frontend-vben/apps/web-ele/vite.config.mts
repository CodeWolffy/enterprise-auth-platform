import { defineConfig } from '@vben/vite-config';

export default defineConfig(async () => {
  return {
    application: {},
    vite: {
      plugins: [],
      server: {
        proxy: {
          '/api': {
            changeOrigin: true,
            // enterprise-auth-platform 后端控制器自带 /api 前缀，故不剥离
            target: 'http://localhost:8080',
            ws: false,
            // 去除 Origin/Referer，由后端 CORS 按同源处理。当前 Vben 前端运行在 5777，
            // 后端 dev 环境的 allowed-origins 已同步为 5777，保持代理去除头为安全实践。
            configure: (proxy: any) => {
              proxy.on('proxyReq', (proxyReq: any) => {
                proxyReq.removeHeader('origin');
                proxyReq.removeHeader('referer');
              });
            },
          },
        },
      },
    },
  };
});
