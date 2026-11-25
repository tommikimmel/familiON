package com.familion.vehicles.application;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.error.NotFoundException;
import com.familion.common.money.Money;
import com.familion.common.security.CurrentUser;
import com.familion.economics.application.api.EconomicsApi;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.vehicles.domain.FullTankConsumptionCalculator;
import com.familion.vehicles.domain.FuelLog;
import com.familion.vehicles.domain.MaintenanceLog;
import com.familion.vehicles.domain.Vehicle;
import com.familion.vehicles.domain.VehicleLens;
import com.familion.vehicles.infrastructure.persistence.FuelLogRepository;
import com.familion.vehicles.infrastructure.persistence.MaintenanceLogRepository;
import com.familion.vehicles.infrastructure.persistence.VehicleRepository;
import com.familion.vehicles.infrastructure.web.VehicleSummaryResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Service
public class VehicleSummaryService {

    private static final String IPC_INDEX = "IPC_ACUMULADO";

    private final VehicleRepository vehicleRepository;
    private final FuelLogRepository fuelLogRepository;
    private final MaintenanceLogRepository maintenanceLogRepository;
    private final FamilyQueryApi familyQueryApi;
    private final EconomicsApi economicsApi;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final VehicleService vehicleService;
    private final Clock clock;

    public VehicleSummaryService(
            VehicleRepository vehicleRepository,
            FuelLogRepository fuelLogRepository,
            MaintenanceLogRepository maintenanceLogRepository,
            FamilyQueryApi familyQueryApi,
            EconomicsApi economicsApi,
            NamedParameterJdbcTemplate jdbcTemplate,
            VehicleService vehicleService,
            Clock clock) {
        this.vehicleRepository = vehicleRepository;
        this.fuelLogRepository = fuelLogRepository;
        this.maintenanceLogRepository = maintenanceLogRepository;
        this.familyQueryApi = familyQueryApi;
        this.economicsApi = economicsApi;
        this.jdbcTemplate = jdbcTemplate;
        this.vehicleService = vehicleService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public VehicleSummaryResponse getSummary(
            CurrentUser user, UUID vehicleId, VehicleLens lens, LocalDate from, LocalDate to) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");

        if (lens == VehicleLens.INCOME_PCT) {
            throw new MethodArgumentTypeMismatchException(
                    lens.name(),
                    VehicleLens.class,
                    "lens",
                    null,
                    new IllegalArgumentException("INCOME_PCT lens is not applicable for vehicles"));
        }

        VehicleLens effectiveLens = lens != null ? lens : VehicleLens.NOMINAL;

        Vehicle vehicle = vehicleRepository
                .findByIdAndFamilyIdAndDeletedAtIsNull(vehicleId, user.familyId())
                .orElseThrow(() -> new NotFoundException("Vehicle not found"));

        LocalDate effectiveTo = to != null ? to : vehicleService.resolveToday(user.familyId());
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusMonths(12);

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BusinessRuleException("From date cannot be after to date");
        }

        FamilySettings settings = familyQueryApi.getSettings(user.familyId());
        String unit = effectiveLens == VehicleLens.REFERENCE_CURRENCY
                ? settings.referenceCurrency()
                : settings.baseCurrency();

        List<FuelLog> fuelLogs = fuelLogRepository
                .findAllByVehicleIdAndFuelDateBetweenOrderByFuelDateAscCreatedAtAsc(
                        vehicleId, effectiveFrom, effectiveTo);

        List<MaintenanceLog> maintenanceLogs = maintenanceLogRepository
                .findAllByVehicleIdAndMaintenanceDateBetweenOrderByMaintenanceDateAscCreatedAtAsc(
                        vehicleId, effectiveFrom, effectiveTo);

        // Distance in range
        List<Integer> odometers = new ArrayList<>();
        for (FuelLog fl : fuelLogs) {
            odometers.add(fl.getOdometerKm());
        }
        for (MaintenanceLog ml : maintenanceLogs) {
            odometers.add(ml.getOdometerKm());
        }

        int distanceKm = 0;
        if (!odometers.isEmpty()) {
            int minOdo = odometers.stream().min(Integer::compare).orElse(0);
            int maxOdo = odometers.stream().max(Integer::compare).orElse(0);
            distanceKm = Math.max(0, maxOdo - minOdo);
        }

        // Total liters
        BigDecimal totalLiters = fuelLogs.stream()
                .map(FuelLog::getLiters)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(3, RoundingMode.HALF_EVEN);

        // Consumption (full-tank to full-tank)
        List<FullTankConsumptionCalculator.FuelLogEntry> entries = fuelLogs.stream()
                .map(fl -> new FullTankConsumptionCalculator.FuelLogEntry(
                        fl.getFuelDate(),
                        fl.getOdometerKm(),
                        fl.getFuelType(),
                        fl.getLiters(),
                        fl.isFullTank()))
                .toList();
        FullTankConsumptionCalculator.ConsumptionResult consumption =
                FullTankConsumptionCalculator.calculate(entries);

        // Lens calculations
        BigDecimal latestIpc = effectiveLens == VehicleLens.CONSTANT_BASE
                ? economicsApi.latestValue(IPC_INDEX).orElse(null)
                : null;

        CostResult fuelCostResult = calculateFuelCosts(vehicleId, effectiveFrom, effectiveTo, effectiveLens, latestIpc);
        CostResult maintenanceCostResult = calculateMaintenanceCosts(
                vehicleId, effectiveFrom, effectiveTo, effectiveLens, latestIpc);

        BigDecimal fuelCost = fuelCostResult.totalCost();
        BigDecimal maintenanceCost = maintenanceCostResult.totalCost();
        BigDecimal totalCost = fuelCost.add(maintenanceCost).setScale(4, RoundingMode.HALF_EVEN);

        boolean lensApproximated = fuelCostResult.hasApproximation() || maintenanceCostResult.hasApproximation();

        BigDecimal costPerKm = distanceKm > 0
                ? totalCost.divide(BigDecimal.valueOf(distanceKm), 4, RoundingMode.HALF_EVEN)
                : null;

        // Price per liter trend
        List<VehicleSummaryResponse.PriceTrendPoint> priceTrend = fuelLogs.stream()
                .map(fl -> new VehicleSummaryResponse.PriceTrendPoint(
                        fl.getFuelDate().toString(),
                        fl.getPricePerLiter(),
                        fl.getFuelType().name()))
                .toList();

        // Monthly buckets
        List<VehicleSummaryResponse.MonthlySummary> monthly = buildMonthlySummaries(
                effectiveFrom,
                effectiveTo,
                fuelCostResult.monthlyCosts(),
                maintenanceCostResult.monthlyCosts(),
                fuelLogs,
                maintenanceLogs);

        return new VehicleSummaryResponse(
                vehicleId,
                effectiveFrom,
                effectiveTo,
                effectiveLens.name(),
                unit,
                distanceKm,
                totalLiters,
                consumption.avgKmPerLiter(),
                consumption.avgLitersPer100Km(),
                costPerKm,
                totalCost,
                fuelCost,
                maintenanceCost,
                lensApproximated,
                monthly,
                priceTrend);
    }

    private CostResult calculateFuelCosts(
            UUID vehicleId,
            LocalDate from,
            LocalDate to,
            VehicleLens lens,
            BigDecimal latestIpc) {
        String sql = """
                SELECT f.fuel_date, t.amount AS raw_amount,
                       es.base_currency_amount, es.amount_in_reference, es.ipc_index_value
                FROM fuel_logs f
                JOIN transactions t ON f.transaction_id = t.id
                LEFT JOIN economic_snapshots es ON t.id = es.transaction_id
                WHERE f.vehicle_id = :vehicleId
                  AND f.fuel_date BETWEEN :from AND :to
                  AND t.deleted_at IS NULL
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("vehicleId", vehicleId)
                .addValue("from", from)
                .addValue("to", to);

        return computeCostResult(sql, params, "fuel_date", lens, latestIpc);
    }

    private CostResult calculateMaintenanceCosts(
            UUID vehicleId,
            LocalDate from,
            LocalDate to,
            VehicleLens lens,
            BigDecimal latestIpc) {
        String sql = """
                SELECT m.maintenance_date, m.cost_amount, t.amount AS raw_amount,
                       es.base_currency_amount, es.amount_in_reference, es.ipc_index_value
                FROM maintenance_logs m
                LEFT JOIN transactions t ON m.transaction_id = t.id AND t.deleted_at IS NULL
                LEFT JOIN economic_snapshots es ON t.id = es.transaction_id
                WHERE m.vehicle_id = :vehicleId
                  AND m.maintenance_date BETWEEN :from AND :to
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("vehicleId", vehicleId)
                .addValue("from", from)
                .addValue("to", to);

        return computeCostResult(sql, params, "maintenance_date", lens, latestIpc);
    }

    private CostResult computeCostResult(
            String sql,
            MapSqlParameterSource params,
            String dateColumn,
            VehicleLens lens,
            BigDecimal latestIpc) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
        BigDecimal total = BigDecimal.ZERO;
        boolean hasApprox = false;
        Map<YearMonth, BigDecimal> monthly = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            LocalDate date = ((java.sql.Date) row.get(dateColumn)).toLocalDate();
            BigDecimal rawAmount = (BigDecimal) row.get("raw_amount");
            BigDecimal baseCurrencyAmount = (BigDecimal) row.get("base_currency_amount");
            BigDecimal amountInReference = (BigDecimal) row.get("amount_in_reference");
            BigDecimal snapshotIpc = (BigDecimal) row.get("ipc_index_value");

            if (row.containsKey("cost_amount") && ((BigDecimal) row.get("cost_amount")).compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            if (rawAmount == null) {
                continue;
            }

            LensCalculation calc = calculateLensCost(
                    lens, rawAmount, baseCurrencyAmount, amountInReference, snapshotIpc, latestIpc);

            total = total.add(calc.amount());
            if (calc.approximated()) {
                hasApprox = true;
            }

            YearMonth ym = YearMonth.from(date);
            monthly.merge(ym, calc.amount(), BigDecimal::add);
        }

        return new CostResult(total.setScale(4, RoundingMode.HALF_EVEN), hasApprox, monthly);
    }

    private LensCalculation calculateLensCost(
            VehicleLens lens,
            BigDecimal rawAmount,
            BigDecimal baseCurrencyAmount,
            BigDecimal amountInReference,
            BigDecimal snapshotIpc,
            BigDecimal latestIpc) {
        return switch (lens) {
            case NOMINAL -> {
                if (baseCurrencyAmount != null) {
                    yield new LensCalculation(Money.scale(baseCurrencyAmount), false);
                }
                yield new LensCalculation(Money.scale(rawAmount), true);
            }
            case CONSTANT_BASE -> {
                if (baseCurrencyAmount != null
                        && latestIpc != null && latestIpc.compareTo(BigDecimal.ZERO) > 0
                        && snapshotIpc != null && snapshotIpc.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal adjusted = Money.divide(baseCurrencyAmount.multiply(latestIpc), snapshotIpc);
                    yield new LensCalculation(adjusted, false);
                }
                BigDecimal fallback = baseCurrencyAmount != null ? Money.scale(baseCurrencyAmount) : Money.scale(rawAmount);
                yield new LensCalculation(fallback, true);
            }
            case REFERENCE_CURRENCY -> {
                if (amountInReference != null) {
                    yield new LensCalculation(Money.scale(amountInReference), false);
                }
                yield new LensCalculation(Money.scale(rawAmount), true);
            }
            case INCOME_PCT -> throw new IllegalArgumentException("INCOME_PCT not supported");
        };
    }

    private List<VehicleSummaryResponse.MonthlySummary> buildMonthlySummaries(
            LocalDate from,
            LocalDate to,
            Map<YearMonth, BigDecimal> fuelMonthly,
            Map<YearMonth, BigDecimal> maintenanceMonthly,
            List<FuelLog> fuelLogs,
            List<MaintenanceLog> maintenanceLogs) {
        Map<YearMonth, List<Integer>> odometersByMonth = new LinkedHashMap<>();
        for (FuelLog fl : fuelLogs) {
            odometersByMonth.computeIfAbsent(YearMonth.from(fl.getFuelDate()), k -> new ArrayList<>())
                    .add(fl.getOdometerKm());
        }
        for (MaintenanceLog ml : maintenanceLogs) {
            odometersByMonth.computeIfAbsent(YearMonth.from(ml.getMaintenanceDate()), k -> new ArrayList<>())
                    .add(ml.getOdometerKm());
        }

        List<VehicleSummaryResponse.MonthlySummary> list = new ArrayList<>();
        YearMonth start = YearMonth.from(from);
        YearMonth end = YearMonth.from(to);

        YearMonth curr = start;
        while (!curr.isAfter(end)) {
            BigDecimal fCost = fuelMonthly.getOrDefault(curr, BigDecimal.ZERO).setScale(4, RoundingMode.HALF_EVEN);
            BigDecimal mCost = maintenanceMonthly.getOrDefault(curr, BigDecimal.ZERO).setScale(4, RoundingMode.HALF_EVEN);

            List<Integer> monthOdos = odometersByMonth.getOrDefault(curr, List.of());
            int km = 0;
            if (monthOdos.size() >= 2) {
                int min = monthOdos.stream().min(Integer::compare).orElse(0);
                int max = monthOdos.stream().max(Integer::compare).orElse(0);
                km = Math.max(0, max - min);
            }

            list.add(new VehicleSummaryResponse.MonthlySummary(
                    curr.toString(),
                    fCost,
                    mCost,
                    km));
            curr = curr.plusMonths(1);
        }

        return list;
    }

    private record LensCalculation(BigDecimal amount, boolean approximated) {}

    private record CostResult(
            BigDecimal totalCost,
            boolean hasApproximation,
            Map<YearMonth, BigDecimal> monthlyCosts) {}
}
