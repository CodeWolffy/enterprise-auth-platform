/**
 * 该文件可自行根据业务逻辑进行调整
 */
import type { RequestClientOptions } from '@vben/request';

import { useAppConfig } from '@vben/hooks';
import { preferences } from '@vben/preferences';
import {
  authenticateResponseInterceptor,
  defaultResponseInterceptor,
  errorMessageResponseInterceptor,
  RequestClient,
} from '@vben/request';
import { useAccessStore } from '@vben/stores';

import { ElMessage } from 'element-plus';

import { useAuthStore } from '#/store';

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);

// ============ 请求作用域管理 ============

/**
 * 租户作用域类型
 * - active: 使用当前登录租户ID（默认）
 * - operator: 使用操作者租户ID（平台管理员切换租户时）
 * - none: 不附加租户ID头
 */
export type TenantScope = 'active' | 'operator' | 'none';

export interface ScopedRequestConfig extends RequestClientOptions {
  requestKey?: string;
  tenantScope?: TenantScope;
  silentAuthFailure?: boolean;
  suppressErrorMessage?: boolean;
}

const activeControllers = new Map<string, AbortController>();

/**
 * 取消单个请求
 * @param requestKey 请求唯一标识
 */
export function cancelRequest(requestKey: string) {
  activeControllers.get(requestKey)?.abort();
  activeControllers.delete(requestKey);
}

/**
 * 批量取消请求
 * @param requestKeys 请求唯一标识数组
 */
export function cancelRequests(requestKeys: string[]) {
  requestKeys.forEach((key) => cancelRequest(key));
}

/**
 * 创建请求作用域
 * 用于管理一组相关请求的生命周期，可以批量取消
 * @returns 请求作用域对象
 * @example
 * ```ts
 * const scope = createRequestScope();
 * await requestClient.get('/api/a', { requestKey: scope.track('a') });
 * await requestClient.get('/api/b', { requestKey: scope.track('b') });
 * scope.cancelAll(); // 取消所有跟踪的请求
 * ```
 */
export function createRequestScope() {
  const requestKeys = new Set<string>();
  return {
    /**
     * 跟踪请求并返回请求键
     * @param requestKey 请求唯一标识
     */
    track(requestKey: string) {
      requestKeys.add(requestKey);
      return requestKey;
    },
    /**
     * 取消作用域内所有请求
     */
    cancelAll() {
      cancelRequests([...requestKeys]);
      requestKeys.clear();
    },
  };
}

// ============ 请求客户端创建 ============

function createRequestClient(baseURL: string, options?: RequestClientOptions) {
  const client = new RequestClient({
    ...options,
    baseURL,
  });

  // 防止 token 失效时多个并发请求重复触发跳转
  let isHandlingTokenExpired = false;

  /**
   * 重新认证逻辑
   */
  async function doReAuthenticate() {
    // 防止多个并发请求同时触发重复处理
    if (isHandlingTokenExpired) {
      return;
    }
    isHandlingTokenExpired = true;

    try {
      console.warn('Access token or refresh token is invalid or expired. ');
      const accessStore = useAccessStore();
      const authStore = useAuthStore();
      accessStore.setAccessToken(null);

      if (
        preferences.app.loginExpiredMode === 'modal' &&
        accessStore.isAccessChecked
      ) {
        // modal 模式：显示弹窗，由组件内部处理倒计时和跳转
        accessStore.setLoginExpired(true);
      } else {
        // page 模式：提示 + 倒计时 + 自动跳转
        ElMessage.warning('登录已过期，请重新登录');
        await new Promise((resolve) => setTimeout(resolve, 2000));
        await authStore.logout();
      }
    } finally {
      isHandlingTokenExpired = false;
    }
  }

  /**
   * 后端使用 Sa-Token 会话续期，不提供前端 refresh token 接口。
   */
  async function doRefreshToken(): Promise<string> {
    throw new Error('Token refresh is disabled');
  }

  function formatToken(token: null | string) {
    return token ? `${token}` : null;
  }

  function removeInternalHeader(headers: any, name: string) {
    if (!headers) {
      return;
    }
    if (typeof headers.delete === 'function') {
      headers.delete(name);
    }
    delete headers[name];
  }

  function resolveTenantId(scope: TenantScope): string | undefined {
    const authStore = useAuthStore();
    if (scope === 'operator') {
      return authStore.operatorTenantId || authStore.tenantId || undefined;
    }
    if (scope === 'active') {
      return authStore.tenantId || undefined;
    }
    return undefined;
  }

  // 请求头处理
  client.addRequestInterceptor({
    fulfilled: async (config) => {
      const accessStore = useAccessStore();
      const scopedConfig = config as ScopedRequestConfig;
      config.headers = config.headers ?? {};
      const headers = config.headers as any;

      // 租户ID处理
      const skipTenantHeader = headers.isSwitchTenant === false;
      const skipToken = headers.isToken === false;
      removeInternalHeader(headers, 'isSwitchTenant');
      removeInternalHeader(headers, 'isToken');

      const scope = scopedConfig.tenantScope ?? 'active';
      const tenantId = resolveTenantId(scope);

      if (tenantId && !skipTenantHeader) {
        headers['X-Tenant-Id'] = tenantId;
      }

      // AbortController 绑定
      if (scopedConfig.requestKey) {
        cancelRequest(scopedConfig.requestKey);
        const controller = new AbortController();
        activeControllers.set(scopedConfig.requestKey, controller);
        (config as any).signal = controller.signal;
      }

      // enterprise-auth-platform 使用 SaToken：Authorization: Bearer <token>
      const token = formatToken(accessStore.accessToken);
      if (token && !skipToken) {
        headers.Authorization = `Bearer ${token}`;
      }
      headers['Accept-Language'] = preferences.app.locale;
      return config;
    },
  });

  // 响应拦截器：释放 AbortController
  // 注意：必须放在 defaultResponseInterceptor 之前添加，确保拿到的是原始 AxiosResponse，
  // 否则 defaultResponseInterceptor 返回 data 后，此处访问 response.config 会变为 undefined。
  client.addResponseInterceptor({
    fulfilled: (response) => {
      const requestKey = (response.config as ScopedRequestConfig)?.requestKey;
      if (
        requestKey &&
        activeControllers.get(requestKey)?.signal ===
          (response.config as any).signal
      ) {
        activeControllers.delete(requestKey);
      }
      return response;
    },
    rejected: (error) => {
      const requestConfig = error?.config as ScopedRequestConfig | undefined;
      const requestKey = requestConfig?.requestKey;
      if (
        requestKey &&
        activeControllers.get(requestKey)?.signal ===
          (requestConfig as any).signal
      ) {
        activeControllers.delete(requestKey);
      }
      return Promise.reject(error);
    },
  });

  // 处理返回的响应数据格式
  client.addResponseInterceptor(
    defaultResponseInterceptor({
      codeField: 'code',
      dataField: 'data',
      successCode: 'OK',
    }),
  );

  // token过期的处理
  client.addResponseInterceptor(
    authenticateResponseInterceptor({
      client,
      doReAuthenticate,
      doRefreshToken,
      enableRefreshToken: false,
      formatToken,
    }),
  );

  // 通用的错误处理,如果没有进入上面的错误处理逻辑，就会进入这里
  client.addResponseInterceptor(
    errorMessageResponseInterceptor((msg: string, error) => {
      // 这里可以根据业务进行定制,你可以拿到 error 内的信息进行定制化处理，根据不同的 code 做不同的提示，而不是直接使用 message.error 提示 msg
      // 401 错误由 doReAuthenticate 统一处理，此处不再重复提示
      const status = error?.response?.status;
      if (status === 401) {
        return Promise.reject(error);
      }
      // 当前mock接口返回的错误字段是 error 或者 message
      const responseData = error?.response?.data ?? {};
      const errorMessage = responseData?.message ?? '';
      // 如果没有错误信息，则会根据状态码进行提示
      ElMessage.error(errorMessage || msg);
    }),
  );

  return client;
}

export const requestClient = createRequestClient(apiURL, {
  responseReturn: 'data',
});

export const baseRequestClient = new RequestClient({ baseURL: apiURL });

/**
 * 切换租户
 * @param tenantId 租户ID
 */
export async function switchTenant(tenantId: string) {
  return requestClient.post(
    `/auth/tenants/${encodeURIComponent(tenantId)}/switch`,
    undefined,
    {
      headers: { isSwitchTenant: false },
    },
  );
}
