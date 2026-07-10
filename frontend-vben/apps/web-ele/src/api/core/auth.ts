import type { ScopedRequestConfig } from '#/api/request';
import type { PermissionSnapshot } from '#/types/auth-models';
import type { SwitchableTenantView } from '#/types/tenant';

import { requestClient } from '#/api/request';

export namespace AuthApi {
  /** 登录接口参数（适配 enterprise-auth-platform） */
  export interface LoginParams {
    username?: string;
    password?: string;
    /** 滑块验证码 ID */
    captchaId?: string;
    /** 滑块验证码内容 */
    captchaCode?: string;
    /** 租户编码，为空时由请求头/默认解析 */
    tenantId?: string;
    /** 登录设备标识 */
    device?: string;
    [key: string]: any;
  }

  /** 登录接口返回值（已适配后端 TokenSessionResponse） */
  export interface LoginResult {
    tokenValue: string;
    tenantId?: string;
    expiresAt?: string;
    passwordChangeRequired?: boolean;
    passwordChangeReason?: string;
  }
}

/**
 * 账号密码登录
 * 后端：POST /api/auth/login -> ApiResponse<TokenSessionResponse{ token, ... }>
 * 注意：后端为明文密码 + 服务端 BCrypt 校验，前端不做 AES 加密。
 */
export async function loginApi(data: AuthApi.LoginParams) {
  const config: ScopedRequestConfig = {
    headers: {
      isToken: false,
      isSwitchTenant: false,
    },
    suppressErrorMessage: true,
  };
  const res = await requestClient.post<any>('/auth/login', data, config);
  return {
    tokenValue: res?.token,
    tenantId: res?.tenantId,
    expiresAt: res?.expiresAt,
    passwordChangeRequired: res?.passwordChangeRequired,
    passwordChangeReason: res?.passwordChangeReason,
  } as AuthApi.LoginResult;
}

/**
 * 退出登录
 * 后端：POST /api/auth/logout
 */
export async function logoutApi() {
  return requestClient.post(
    '/auth/logout',
    {},
    {
      headers: {
        isSwitchTenant: false,
      },
    },
  );
}

/**
 * 获取当前用户权限快照
 * 后端：GET /api/auth/me
 */
export async function getPermissionSnapshotApi() {
  return requestClient.get<PermissionSnapshot>('/auth/me', {
    headers: { isSwitchTenant: false },
  });
}

/**
 * 获取当前用户权限码集合（grants）
 * 后端：GET /api/auth/me -> PermissionSnapshotResponse.grants
 */
export async function getAccessCodesApi() {
  const me = await getPermissionSnapshotApi();
  return (me?.grants ?? []) as string[];
}

/**
 * 获取当前用户可切换租户列表
 * 后端：GET /api/auth/tenants/switchable
 */
export async function getSwitchableTenantsApi() {
  return requestClient.get<SwitchableTenantView[]>('/auth/tenants/switchable', {
    headers: { isSwitchTenant: false },
  });
}

/**
 * 切换当前会话活跃租户
 * 后端：POST /api/auth/tenants/{tenantId}/switch
 */
export async function switchTenantApi(tenantId: string) {
  return requestClient.post<PermissionSnapshot>(
    `/auth/tenants/${encodeURIComponent(tenantId)}/switch`,
    undefined,
    {
      headers: { isSwitchTenant: false },
    },
  );
}

/**
 * 获取登录验证码
 * 后端：GET /api/auth/captcha
 * 传入 username 后，后端会根据该账号近期失败情况决定下发滑块或文字点选(风险升级)。
 */
export async function getCaptchaApi(username?: string) {
  return requestClient.get<any>('/auth/captcha', {
    headers: { isToken: false, isSwitchTenant: false },
    params: username ? { username } : undefined,
  });
}

/**
 * 校验滑块验证码，成功后返回可用于登录阶段二次校验的 token。
 * 后端：POST /api/auth/captcha/verify
 */
export async function verifyCaptchaApi(captchaId: string, captchaCode: string) {
  return requestClient.post<{ token: string }>(
    '/auth/captcha/verify',
    { captchaId, captchaCode },
    {
      headers: { isToken: false, isSwitchTenant: false },
    },
  );
}
