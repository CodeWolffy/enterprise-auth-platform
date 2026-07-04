export interface TenantView {
  tenantId: string;
  name: string;
  platformLevel?: boolean;
  tenantStatus?: number;
  authBeginAt?: null | string;
  expireAt?: null | string;
  packageCode?: null | string;
  packageName?: null | string;
  logoUrl?: null | string;
  contactName?: null | string;
  contactPhone?: null | string;
  contactEmail?: null | string;
  website?: null | string;
  address?: null | string;
  lifecycleNote?: null | string;
}

export interface SwitchableTenantView {
  tenantId: string;
  name: string;
  platformLevel: boolean;
  tenantStatus?: null | number;
  active: boolean;
  origin: boolean;
  switchable: boolean;
  disabledReason?: null | string;
}

export interface ImpactRuleView {
  ruleCode: string;
  level: 'ERROR' | 'WARN' | string;
  hit: boolean;
  message: string;
  relatedCount: number;
  blocking: boolean;
}

export interface TenantPackageView {
  id: number;
  packageCode: string;
  packageName: string;
  subtitle?: null | string;
  salesPrice?: null | number;
  originalPrice?: null | number;
  descriptionMd?: null | string;
  appKey?: null | string;
  orderNo?: null | number;
  packageDesc?: null | string;
  status: '0' | '1';
  updatedAt?: null | string;
  referencedTenantCount?: number;
  referencedTenantIds?: string[];
}

export interface TenantPackageImpactView {
  id: number;
  packageCode: string;
  packageName: string;
  status: '0' | '1';
  appKey?: null | string;
  referencedTenantCount: number;
  referencedTenantIds: string[];
  rules: ImpactRuleView[];
  recommendedActions: string[];
}
