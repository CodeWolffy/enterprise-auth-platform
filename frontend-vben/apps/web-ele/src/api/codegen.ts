import { requestClient } from '#/api/request';

export interface DataSourceView {
  id: number;
  name: string;
  jdbcUrl: string;
  username: string;
  password?: null | string;
  dbName?: string;
  host?: string;
  port?: number;
  enabled: boolean;
  external: boolean;
  externalAuthorized: boolean;
  authorizedAt?: null | string;
  authorizationNote?: null | string;
  createdAt?: null | string;
  updatedAt?: null | string;
}

export async function getDataSources() {
  return await requestClient.get<DataSourceView[]>('/codegen/datasources', {
    headers: { isSwitchTenant: false },
  });
}

export async function createDataSource(payload: {
  dbName?: string;
  enabled?: boolean;
  host?: string;
  jdbcUrl: string;
  name: string;
  password?: string;
  port?: number;
  username: string;
}) {
  return await requestClient.post<DataSourceView>(
    '/codegen/datasources',
    payload,
    {
      headers: { isSwitchTenant: false },
    },
  );
}

export async function updateDataSource(
  id: number,
  payload: {
    dbName?: string;
    enabled?: boolean;
    host?: string;
    jdbcUrl: string;
    name: string;
    password?: string;
    port?: number;
    username: string;
  },
) {
  return await requestClient.put<DataSourceView>(
    `/codegen/datasources/${id}`,
    payload,
    {
      headers: { isSwitchTenant: false },
    },
  );
}

export async function deleteDataSource(id: number) {
  return await requestClient.delete(`/codegen/datasources/${id}`, {
    headers: { isSwitchTenant: false },
  });
}

export async function testDataSource(id: number) {
  return await requestClient.post<{
    dataSourceId: number;
    message: string;
    success: boolean;
  }>(
    `/codegen/datasources/${id}/test`,
    {},
    { headers: { isSwitchTenant: false } },
  );
}

export async function authorizeDataSource(id: number, note?: string) {
  return await requestClient.post<DataSourceView>(
    `/codegen/datasources/${id}/authorize`,
    note ? { note } : {},
    { headers: { isSwitchTenant: false } },
  );
}
