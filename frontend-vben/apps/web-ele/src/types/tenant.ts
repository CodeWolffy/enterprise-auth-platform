export interface TenantView {
  tenantId: string;
  name: string;
  platformLevel?: boolean;
  tenantStatus?: number;
  authBeginAt?: string | null;
  expireAt?: string | null;
  packageCode?: string | null;
  packageName?: string | null;
  logoUrl?: string | null;
  contactName?: string | null;
  contactPhone?: string | null;
  contactEmail?: string | null;
  website?: string | null;
  address?: string | null;
  lifecycleNote?: string | null;
}

export interface SwitchableTenantView {
  tenantId: string;
  name: string;
  platformLevel: boolean;
  tenantStatus?: number | null;
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
  subtitle?: string | null;
  salesPrice?: number | null;
  originalPrice?: number | null;
  descriptionMd?: string | null;
  appKey?: string | null;
  orderNo?: number | null;
  packageDesc?: string | null;
  status: '0' | '1';
  updatedAt?: string | null;
  referencedTenantCount?: number;
  referencedTenantIds?: string[];
}

export interface TenantPackageImpactView {
  id: number;
  packageCode: string;
  packageName: string;
  status: '0' | '1';
  appKey?: string | null;
  referencedTenantCount: number;
  referencedTenantIds: string[];
  rules: ImpactRuleView[];
  recommendedActions: string[];
}
