import { requestClient } from '#/api/request';

/**
 * 获取文件分页列表
 * 后端：GET /api/files?keyword&contentType&storageType&visibility&page&size
 */
export async function getPage(query: any) {
  const { page, size, keyword, contentType, storageType, visibility } =
    query ?? {};
  return requestClient.get('/files', {
    params: {
      keyword,
      contentType,
      storageType,
      visibility,
      page: page ?? 1,
      size: size ?? 10,
    },
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 上传文件
 * 后端：POST /api/files/upload (multipart/form-data)
 */
export async function upload(file: File, visibility?: string) {
  const formData = new FormData();
  formData.append('file', file);
  if (visibility) {
    formData.append('visibility', visibility);
  }
  return requestClient.post('/files/upload', formData, {
    headers: {
      isSwitchTenant: false,
      'Content-Type': 'multipart/form-data',
    },
  });
}

/**
 * 删除文件
 * 后端：DELETE /api/files/{fileKey}
 */
export async function delObj(fileKey: string) {
  return requestClient.delete(`/files/${fileKey}`, {
    headers: {
      isSwitchTenant: false,
    },
  });
}

/**
 * 下载/预览存储文件
 * 后端：GET /api/files/{fileKey} (blob)
 */
export async function downloadFile(fileKey: string) {
  return requestClient.get(`/files/${fileKey}`, {
    headers: {
      isSwitchTenant: false,
    },
    responseReturn: 'raw',
  });
}
