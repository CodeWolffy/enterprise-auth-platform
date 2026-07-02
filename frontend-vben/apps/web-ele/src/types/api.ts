export interface ApiResponse<T> {
  code?: string;
  message?: string;
  data: T;
}

export interface PageResult<T> {
  total: number;
  page: number;
  size: number;
  records: T[];
}

export interface FeatureFlags {
  gatewayEnabled: boolean;
  nacosEnabled: boolean;
  mqEnabled: boolean;
  seataEnabled: boolean;
  jobEnabled: boolean;
  lokiEnabled: boolean;
}
