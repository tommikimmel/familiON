export type AllowanceFrequency = 'WEEKLY' | 'MONTHLY';
export type AllowanceAdjustmentRule = 'MANUAL' | 'IPC' | 'REFERENCE_CURRENCY';

export interface AllowanceChild {
  id: string;
  fullName: string;
  accentColor: string;
}

export interface AllowanceAssignedBy {
  id: string;
  fullName: string;
}

export interface AllowanceAccount {
  id: string;
  name: string;
}

export interface Allowance {
  id: string;
  child: AllowanceChild;
  assignedBy: AllowanceAssignedBy;
  sourceAccount: AllowanceAccount | null;
  targetAccount: AllowanceAccount;
  amount: string;
  currencyCode: string;
  frequency: AllowanceFrequency;
  dayOfPeriod: number;
  adjustmentRule: AllowanceAdjustmentRule;
  isActive: boolean;
  currentAmount: string;
  nextDisbursementDate: string;
  lastDisbursementDate: string | null;
  childBalance: string;
}

export interface AllowanceCreate {
  childUserId: string;
  sourceAccountId?: string | null;
  targetAccountId: string;
  amount: string;
  currencyCode: string;
  frequency: AllowanceFrequency;
  dayOfPeriod: number;
  adjustmentRule: AllowanceAdjustmentRule;
}

export interface AllowancePatch {
  childUserId?: string;
  sourceAccountId?: string | null;
  targetAccountId?: string;
  amount?: string;
  currencyCode?: string;
  frequency?: AllowanceFrequency;
  dayOfPeriod?: number;
  adjustmentRule?: AllowanceAdjustmentRule;
  isActive?: boolean;
}

export interface DisbursePayload {
  date?: string;
  amount?: string;
}

export interface DisburseResult {
  allowance: Allowance;
  outTransactionId: string | null;
  inTransactionId: string | null;
}

export interface FamilyMemberSummary {
  id: string;
  fullName: string;
  role: 'ADULTO' | 'HIJO';
  accentColor: string;
}

export interface AccountOption {
  id: string;
  name: string;
  currencyCode: string;
  ownerUserId?: string | null;
}
