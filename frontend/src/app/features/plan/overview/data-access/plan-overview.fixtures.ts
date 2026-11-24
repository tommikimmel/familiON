import { PlanPayload } from './plan-overview.models';

export const FULL_PLAN_FIXTURE: PlanPayload = {
  positions: [
    {
      currencyCode: 'USD',
      units: '1250.0000',
      weightedAvgRate: '1150.000000',
      currentRate: '1247.750000',
      unrealizedResultPct: '8.500000',
    },
    {
      currencyCode: 'EUR',
      units: '300.0000',
      weightedAvgRate: '1320.000000',
      currentRate: '1289.640000',
      unrealizedResultPct: '-2.300000',
    },
  ],
  installments: [
    {
      id: 'inst-1',
      description: 'Notebook trabajo',
      totalInstallments: 12,
      paidInstallments: 4,
      remainingAmount: '480000.0000',
      currencyCode: 'ARS',
      nextInstallment: {
        number: 5,
        date: '2026-12-05',
        amount: '60000.0000',
      },
    },
    {
      id: 'inst-2',
      description: 'Heladera inverter',
      totalInstallments: 6,
      paidInstallments: 2,
      remainingAmount: '200000.0000',
      currencyCode: 'ARS',
      nextInstallment: {
        number: 3,
        date: '2026-11-28',
        amount: '50000.0000',
      },
    },
  ],
  commitment: [
    { month: '2026-11', amount: '110000.0000', currencyCode: 'ARS' },
    { month: '2026-12', amount: '110000.0000', currencyCode: 'ARS' },
    { month: '2027-01', amount: '110000.0000', currencyCode: 'ARS' },
    { month: '2027-02', amount: '110000.0000', currencyCode: 'ARS' },
    { month: '2027-03', amount: '60000.0000', currencyCode: 'ARS' },
    { month: '2027-04', amount: '60000.0000', currencyCode: 'ARS' },
  ],
  recurring: [
    {
      id: 'rec-1',
      name: 'Expensas',
      currencyCode: 'ARS',
      month: {
        dueDate: '2026-11-10',
        estimatedAmount: '95000.0000',
        status: 'OVERDUE',
      },
    },
    {
      id: 'rec-2',
      name: 'Internet fibra',
      currencyCode: 'ARS',
      month: {
        dueDate: '2026-11-26',
        estimatedAmount: '32000.0000',
        status: 'PENDING',
      },
    },
    {
      id: 'rec-3',
      name: 'Luz Edenor',
      currencyCode: 'ARS',
      month: {
        dueDate: '2026-11-29',
        estimatedAmount: '28000.0000',
        status: 'PENDING',
      },
    },
    {
      id: 'rec-4',
      name: 'Alquiler',
      currencyCode: 'ARS',
      month: {
        dueDate: '2026-11-05',
        estimatedAmount: '420000.0000',
        status: 'PAID',
      },
    },
  ],
  goals: [
    {
      id: 'goal-1',
      name: 'Vacaciones verano',
      progressPct: '65.0000',
      targetUnit: 'USD',
      accumulatedInUnit: '1300.0000',
      targetAmount: '2000.0000',
    },
    {
      id: 'goal-2',
      name: 'Fondo de emergencia',
      progressPct: '40.0000',
      targetUnit: 'ARS_IPC',
      accumulatedInUnit: '400000.0000',
      targetAmount: '1000000.0000',
    },
    {
      id: 'goal-3',
      name: 'Renovar compu',
      progressPct: '100.0000',
      targetUnit: 'UVA',
      accumulatedInUnit: '500.0000',
      targetAmount: '500.0000',
    },
    {
      id: 'goal-4',
      name: 'Bicicleta nueva',
      progressPct: '15.0000',
      targetUnit: 'ARS',
      accumulatedInUnit: '45000.0000',
      targetAmount: '300000.0000',
    },
  ],
  allowances: [
    {
      id: 'all-1',
      child: { id: 'child-1', fullName: 'Mateo Gómez', accentColor: '#2563EB' },
      currentAmount: '25000.0000',
      currencyCode: 'ARS',
      nextDisbursementDate: '2026-12-01',
    },
    {
      id: 'all-2',
      child: { id: 'child-2', fullName: 'Sofía Gómez', accentColor: '#DB2777' },
      currentAmount: '20000.0000',
      currencyCode: 'ARS',
      nextDisbursementDate: '2026-12-01',
    },
  ],
};

export const PARTIAL_NULL_PLAN_FIXTURE: PlanPayload = {
  positions: null,
  installments: null,
  commitment: null,
  recurring: null,
  goals: null,
  allowances: null,
};

export const EMPTY_PLAN_FIXTURE: PlanPayload = {
  positions: [],
  installments: [],
  commitment: [],
  recurring: [],
  goals: [],
  allowances: [],
};
