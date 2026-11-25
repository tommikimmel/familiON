export type GoalScope = 'PERSONAL' | 'FAMILY';
export type TargetUnit = 'ARS' | 'USD' | 'EUR' | 'UVA' | 'ARS_IPC';
export type GoalStatus = 'ACTIVE' | 'COMPLETED' | 'PAUSED' | 'CANCELLED';

export interface SavingsGoalUserSummary {
  id: string;
  fullName: string;
}

export interface SavingsGoal {
  id: string;
  name: string;
  scope: GoalScope;
  owner: SavingsGoalUserSummary | null;
  targetAmount: string;
  targetUnit: TargetUnit;
  targetDate: string | null;
  status: GoalStatus;
  accumulatedInUnit: string;
  progressPct: string;
  remainingInUnit: string;
  monthsLeft: number | null;
  suggestedMonthlyInUnit: string | null;
  contributionsCount: number;
  lastContributionDate: string | null;
  accumulatedInBaseToday: string | null;
}

export interface Contribution {
  id: string;
  user: SavingsGoalUserSummary;
  amount: string;
  currencyCode: string;
  amountInGoalUnit: string;
  contributionDate: string;
  notes: string | null;
  transactionId: string | null;
}

export interface SavingsGoalCreate {
  name: string;
  scope: GoalScope;
  targetAmount: string;
  targetUnit: TargetUnit;
  targetDate?: string | null;
}

export interface SavingsGoalPatch {
  name?: string;
  targetAmount?: string;
  targetDate?: string | null;
  status?: GoalStatus;
}

export interface ContributionCreate {
  amount: string;
  currencyCode: string;
  contributionDate?: string;
  notes?: string | null;
}

export interface TargetUnitOption {
  value: TargetUnit;
  label: string;
  explanation: string;
  badge: string;
}

export const TARGET_UNIT_OPTIONS: readonly TargetUnitOption[] = [
  {
    value: 'USD',
    label: 'Dólares (USD)',
    explanation: 'USD: protege contra la devaluación',
    badge: 'USD',
  },
  {
    value: 'UVA',
    label: 'Unidades de Valor Adquisitivo (UVA)',
    explanation: 'UVA: sigue la inflación',
    badge: 'UVA',
  },
  {
    value: 'ARS_IPC',
    label: 'Pesos ajustados por IPC',
    explanation: 'Pesos ajustados: se actualiza por IPC',
    badge: '$ de hoy',
  },
  {
    value: 'ARS',
    label: 'Pesos (ARS)',
    explanation: 'Moneda nominal local',
    badge: 'ARS',
  },
  {
    value: 'EUR',
    label: 'Euros (EUR)',
    explanation: 'Moneda europea',
    badge: 'EUR',
  },
] as const;
