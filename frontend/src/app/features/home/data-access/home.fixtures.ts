import {
  Granularity,
  Lens,
  View,
  ViewContextQueryParams,
} from '../../../core/state/view-context.store';
import { PeriodLabelPipe } from '../../../shared/pipes/period-label.pipe';
import {
  CategorySlice,
  HomePayload,
  IndexValue,
  Period,
  PersonSlice,
  Series,
  TransactionItem,
} from './home.models';

const MEP_RATE = 1185;
const INFLATION_FACTOR = 1.042;
const periodLabelPipe = new PeriodLabelPipe();

function toDecimal(value: number): string {
  return value.toFixed(4);
}

function buildPeriod(date: string, granularity: Granularity): Period {
  const parts = date.split('-').map(Number);
  const year = Number.isFinite(parts[0]) ? parts[0] : 2026;
  const month = Number.isFinite(parts[1]) ? parts[1] : 11;
  const paddedMonth = String(month).padStart(2, '0');
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();

  return {
    granularity,
    from: `${year}-${paddedMonth}-01`,
    to: `${year}-${paddedMonth}-${String(lastDay).padStart(2, '0')}`,
    label: periodLabelPipe.transform(date, granularity) || 'Noviembre 2026',
  };
}

function scaleForLens(amountArs: number, lens: Lens, totalIncomeArs: number): number {
  switch (lens) {
    case 'REFERENCE_CURRENCY':
      return amountArs / MEP_RATE;
    case 'CONSTANT_BASE':
      return amountArs * INFLATION_FACTOR;
    case 'INCOME_PCT':
      return totalIncomeArs > 0 ? (amountArs / totalIncomeArs) * 100 : 0;
    default:
      return amountArs;
  }
}

function unitForLens(lens: Lens): string {
  switch (lens) {
    case 'REFERENCE_CURRENCY':
      return 'USD';
    case 'INCOME_PCT':
      return '%';
    default:
      return 'ARS';
  }
}

const DEMO_RATES: IndexValue[] = [
  {
    indexCode: 'USD_MEP',
    effectiveDate: '2026-11-23',
    value: '1185.0000',
    source: 'API',
  },
  {
    indexCode: 'USD_OFICIAL',
    effectiveDate: '2026-11-23',
    value: '1012.5000',
    source: 'API',
  },
  {
    indexCode: 'UVA',
    effectiveDate: '2026-11-23',
    value: '1248.4000',
    source: 'API',
  },
];

export function buildDemoHomePayload(
  params: ViewContextQueryParams = {
    view: 'FAMILY',
    lens: 'NOMINAL',
    granularity: 'MONTH',
    date: '2026-11-23',
  },
): HomePayload {
  const view: View = params.view;
  const lens: Lens = params.lens;
  const isFamily = view === 'FAMILY';
  const unit = unitForLens(lens);
  const period = buildPeriod(params.date, params.granularity);
  const lensApproximated = lens === 'CONSTANT_BASE';

  const incomeArs = isFamily ? 3_200_000 : 1_850_000;
  const expenseArs = isFamily ? 2_464_000 : 1_387_500;
  const balanceArs = incomeArs - expenseArs;
  const prevIncomeArs = isFamily ? 3_050_000 : 1_780_000;
  const prevExpenseArs = isFamily ? 2_320_000 : 1_410_000;

  const rawCategories: {
    id: string;
    name: string;
    colorHex: string | null;
    icon: string;
    amountArs: number;
    variationPct: string | null;
  }[] = [
    {
      id: 'cat-super',
      name: 'Supermercado',
      colorHex: '#0F766E',
      icon: 'shopping-cart',
      amountArs: isFamily ? 760_000 : 420_000,
      variationPct: '8.2000',
    },
    {
      id: 'cat-home',
      name: 'Vivienda y servicios',
      colorHex: '#2563EB',
      icon: 'house',
      amountArs: isFamily ? 680_000 : 390_000,
      variationPct: '-3.4000',
    },
    {
      id: 'cat-auto',
      name: 'Movilidad y combustible',
      colorHex: '#D97706',
      icon: 'fuel',
      amountArs: isFamily ? 410_000 : 245_000,
      variationPct: '5.1000',
    },
    {
      id: 'cat-edu',
      name: 'Educación',
      colorHex: '#7C3AED',
      icon: 'graduation-cap',
      amountArs: isFamily ? 344_000 : 165_000,
      variationPct: '0.0000',
    },
    {
      id: 'cat-leisure',
      name: 'Salidas y ocio',
      colorHex: null,
      icon: 'party-popper',
      amountArs: isFamily ? 270_000 : 167_500,
      variationPct: '-6.5000',
    },
  ];

  const categorySlices: CategorySlice[] = rawCategories.map((cat) => {
    const scaled = scaleForLens(cat.amountArs, lens, incomeArs);
    const pct = (cat.amountArs / expenseArs) * 100;
    return {
      categoryId: cat.id,
      name: cat.name,
      colorHex: cat.colorHex,
      icon: cat.icon,
      amount: toDecimal(scaled),
      pct: toDecimal(pct),
      variationPct: cat.variationPct,
    };
  });

  const personSlices: PersonSlice[] = [
    {
      userId: 'user-ana',
      fullName: 'Ana García',
      accentColor: '#0F766E',
      amount: toDecimal(scaleForLens(1_182_720, lens, incomeArs)),
      pct: '48.0000',
    },
    {
      userId: 'user-carlos',
      fullName: 'Carlos García',
      accentColor: '#2563EB',
      amount: toDecimal(scaleForLens(936_320, lens, incomeArs)),
      pct: '38.0000',
    },
    {
      userId: 'user-sofia',
      fullName: 'Sofía García',
      accentColor: '#D97706',
      amount: toDecimal(scaleForLens(344_960, lens, incomeArs)),
      pct: '14.0000',
    },
  ];

  const buildTx = (
    id: string,
    type: 'INCOME' | 'EXPENSE' | 'TRANSFER',
    amountArs: number,
    date: string,
    description: string,
    category: TransactionItem['category'],
    account: TransactionItem['account'],
    paidBy: TransactionItem['paidBy'],
    attributedTo: TransactionItem['attributedTo'],
    installment: TransactionItem['installment'] = null,
    notes: string | null = null,
  ): TransactionItem => ({
    id,
    type,
    scope: isFamily ? 'FAMILY' : 'PERSONAL',
    status: 'CONFIRMED',
    amount: toDecimal(amountArs),
    currencyCode: 'ARS',
    transactionDate: date,
    description,
    notes,
    category,
    account,
    paidBy,
    attributedTo,
    installment,
    lens,
    lensAmount:
      lens === 'NOMINAL' ? null : toDecimal(scaleForLens(amountArs, lens, incomeArs)),
    lensUnit: lens === 'NOMINAL' ? null : unit,
    lensApproximated,
  });

  const ana = { id: 'user-ana', fullName: 'Ana García' };
  const carlos = { id: 'user-carlos', fullName: 'Carlos García' };
  const sofia = { id: 'user-sofia', fullName: 'Sofía García' };

  const recentTransactions: TransactionItem[] = [
    buildTx(
      'tx-1',
      'EXPENSE',
      148_500,
      '2026-11-22',
      'Supermercado Coto',
      { id: 'cat-super', name: 'Supermercado', icon: 'shopping-cart', colorHex: '#0F766E' },
      { id: 'acc-visa', name: 'Visa Galicia', accountType: 'CREDIT_CARD' },
      ana,
      ana,
      null,
      'Compra semanal',
    ),
    buildTx(
      'tx-2',
      'EXPENSE',
      95_000,
      '2026-11-21',
      'Smart TV 55"',
      { id: 'cat-home', name: 'Vivienda y servicios', icon: 'laptop', colorHex: '#2563EB' },
      { id: 'acc-visa', name: 'Visa Galicia', accountType: 'CREDIT_CARD' },
      carlos,
      ana,
      { planId: 'plan-tv', number: 2, total: 6 },
    ),
    buildTx(
      'tx-3',
      'INCOME',
      1_850_000,
      '2026-11-20',
      'Sueldo mensual',
      { id: 'cat-salary', name: 'Salario', icon: 'briefcase', colorHex: '#16A34A' },
      { id: 'acc-bank', name: 'Cuenta Sueldo', accountType: 'BANK' },
      ana,
      ana,
    ),
    buildTx(
      'tx-4',
      'EXPENSE',
      62_400,
      '2026-11-19',
      'YPF Infinia',
      { id: 'cat-auto', name: 'Movilidad y combustible', icon: 'fuel', colorHex: '#D97706' },
      { id: 'acc-mp', name: 'Mercado Pago', accountType: 'WALLET' },
      carlos,
      carlos,
    ),
    buildTx(
      'tx-5',
      'EXPENSE',
      215_000,
      '2026-11-18',
      'Expensas departamento',
      { id: 'cat-home', name: 'Vivienda y servicios', icon: 'house', colorHex: '#2563EB' },
      { id: 'acc-bank', name: 'Cuenta Sueldo', accountType: 'BANK' },
      ana,
      ana,
    ),
    buildTx(
      'tx-6',
      'EXPENSE',
      172_000,
      '2026-11-16',
      'Cuota colegio',
      { id: 'cat-edu', name: 'Educación', icon: 'graduation-cap', colorHex: '#7C3AED' },
      { id: 'acc-bank', name: 'Cuenta Sueldo', accountType: 'BANK' },
      ana,
      sofia,
    ),
    buildTx(
      'tx-7',
      'EXPENSE',
      48_900,
      '2026-11-15',
      'Farmacity',
      { id: 'cat-health', name: 'Salud', icon: 'heart-pulse', colorHex: '#E11D48' },
      { id: 'acc-mp', name: 'Mercado Pago', accountType: 'WALLET' },
      carlos,
      sofia,
    ),
    buildTx(
      'tx-8',
      'EXPENSE',
      38_500,
      '2026-11-13',
      'Internet fibra óptica',
      { id: 'cat-home', name: 'Vivienda y servicios', icon: 'plug-zap', colorHex: '#2563EB' },
      { id: 'acc-visa', name: 'Visa Galicia', accountType: 'CREDIT_CARD' },
      ana,
      ana,
    ),
    buildTx(
      'tx-9',
      'TRANSFER',
      250_000,
      '2026-11-11',
      'Ahorro mensual USD',
      null,
      { id: 'acc-bank', name: 'Cuenta Sueldo', accountType: 'BANK' },
      ana,
      ana,
    ),
    buildTx(
      'tx-10',
      'EXPENSE',
      54_200,
      '2026-11-10',
      'Cena familiar',
      { id: 'cat-leisure', name: 'Salidas y ocio', icon: 'party-popper', colorHex: '#EC4899' },
      { id: 'acc-visa', name: 'Visa Galicia', accountType: 'CREDIT_CARD' },
      carlos,
      carlos,
      { planId: 'plan-dinner', number: 1, total: 3 },
    ),
  ];

  return {
    summary: {
      period,
      view,
      lens,
      unit,
      income: toDecimal(scaleForLens(incomeArs, lens, incomeArs)),
      expense: toDecimal(scaleForLens(expenseArs, lens, incomeArs)),
      balance: toDecimal(scaleForLens(balanceArs, lens, incomeArs)),
      savingsRatePct: toDecimal((balanceArs / incomeArs) * 100),
      installmentCommitmentPct: isFamily ? '18.0000' : '14.5000',
      previous: {
        income: toDecimal(scaleForLens(prevIncomeArs, lens, prevIncomeArs)),
        expense: toDecimal(scaleForLens(prevExpenseArs, lens, prevIncomeArs)),
      },
      realVariationPct: '4.2000',
      lensApproximated,
    },
    byCategory: {
      period,
      lens,
      unit,
      total: toDecimal(scaleForLens(expenseArs, lens, incomeArs)),
      slices: categorySlices,
    },
    byPerson: isFamily
      ? {
          period,
          lens,
          unit,
          total: toDecimal(scaleForLens(expenseArs, lens, incomeArs)),
          slices: personSlices,
        }
      : null,
    recentTransactions,
    latestRates: DEMO_RATES,
    unreadNotifications: 3,
  };
}

export function buildDemoSeries(
  params: Pick<ViewContextQueryParams, 'view' | 'lens' | 'date'> = {
    view: 'FAMILY',
    lens: 'NOMINAL',
    date: '2026-11-23',
  },
): Series {
  const isFamily = params.view === 'FAMILY';
  const factor = isFamily ? 1 : 0.58;
  const lens = params.lens;
  const unit = unitForLens(lens);

  const baseMonths: { periodStart: string; label: string; income: number; expense: number }[] = [
    { periodStart: '2026-06-01', label: 'Jun', income: 2_650_000, expense: 2_180_000 },
    { periodStart: '2026-07-01', label: 'Jul', income: 2_820_000, expense: 2_350_000 },
    { periodStart: '2026-08-01', label: 'Ago', income: 2_900_000, expense: 2_640_000 },
    { periodStart: '2026-09-01', label: 'Sep', income: 2_980_000, expense: 2_210_000 },
    { periodStart: '2026-10-01', label: 'Oct', income: 3_050_000, expense: 2_320_000 },
    { periodStart: '2026-11-01', label: 'Nov', income: 3_200_000, expense: 2_464_000 },
  ];

  return {
    lens,
    unit,
    granularity: 'MONTH',
    points: baseMonths.map((item) => {
      const incomeArs = item.income * factor;
      const expenseArs = item.expense * factor;
      const balanceArs = incomeArs - expenseArs;
      return {
        periodStart: item.periodStart,
        label: item.label,
        income: toDecimal(scaleForLens(incomeArs, lens, incomeArs)),
        expense: toDecimal(scaleForLens(expenseArs, lens, incomeArs)),
        balance: toDecimal(scaleForLens(balanceArs, lens, incomeArs)),
      };
    }),
  };
}

export const DEMO_HOME_PAYLOAD: HomePayload = buildDemoHomePayload();
export const DEMO_SERIES: Series = buildDemoSeries();
