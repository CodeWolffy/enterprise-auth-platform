import { requestClient } from '#/api/request';

export interface DataSourceView {
  id: number;
  name: string;
  jdbcUrl: string;
  username: string;
  password?: string | null;
  dbName?: string;
  host?: string;
  port?: number;
  enabled: boolean;
  external: boolean;
  externalAuthorized: boolean;
  authorizedAt?: string | null;
  authorizationNote?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export async function getDataSources() {
  return await requestClient.get<DataSourceView[]>('/codegen/datasources', {
    headers: { isSwitchTenant: false },
  });
}

export async function createDataSource(payload: {
  name: string;
  jdbcUrl: string;
  username: string;
  password?: string;
  dbName?: string;
  host?: string;
  port?: number;
  enabled?: boolean;
}) {
  return await requestClient.post<DataSourceView>('/codegen/datasources', payload, {
    headers: { isSwitchTenant: false },
  });
}

export async function updateDataSource(id: number, payload: {
  name: string;
  jdbcUrl: string;
  username: string;
  password?: string;
  dbName?: string;
  host?: string;
  port?: number;
  enabled?: boolean;
}) {
  return await requestClient.put<DataSourceView>(`/codegen/datasources/${id}`, payload, {
    headers: { isSwitchTenant: false },
  });
}

export async function deleteDataSource(id: number) {
  return await requestClient.delete<void>(`/codegen/datasources/${id}`, {
    headers: { isSwitchTenant: false },
  });
}

export async function testDataSource(id: number) {
  return await requestClient.post<{ dataSourceId: number; success: boolean; message: string }>(
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
