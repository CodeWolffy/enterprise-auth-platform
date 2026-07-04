import type {
  MailChannel,
  MailChannelPreset,
  MailChannelSaveRequest,
} from '#/types/system';

import { requestClient } from '#/api/request';

/**
 * 获取邮件渠道预设列表
 * 后端：GET /api/system/mail-channel/presets
 */
export async function getPresets() {
  return requestClient.get<MailChannelPreset[]>(
    '/system/mail-channel/presets',
    {
      headers: {
        isSwitchTenant: false,
      },
    },
  );
}

/**
 * 获取当前租户邮件渠道配置
 * 后端：GET /api/system/mail-channel
 */
export async function getList() {
  return requestClient.get<MailChannel | null>('/system/mail-channel', {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 保存邮件渠道配置
 * 后端：POST /api/system/mail-channel
 */
export async function addObj(data: MailChannelSaveRequest) {
  return requestClient.post<MailChannel>('/system/mail-channel', data, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 修改邮件渠道配置
 * 后端：POST /api/system/mail-channel（同保存接口）
 */
export async function editObj(data: MailChannelSaveRequest) {
  return addObj(data);
}

/**
 * 删除邮件渠道配置
 * 后端：DELETE /api/system/mail-channel
 */
export async function delObj() {
  return requestClient.delete('/system/mail-channel', {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 发送测试邮件
 * 后端：POST /api/system/mail-channel/test?toEmail=xxx
 */
export async function testSend(toEmail: string) {
  return requestClient.post('/system/mail-channel/test', null, {
    params: { toEmail },
    headers: {
      isSwitchTenant: false,
    },
  });
}
