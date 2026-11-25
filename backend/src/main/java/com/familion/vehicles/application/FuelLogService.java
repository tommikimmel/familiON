package com.familion.vehicles.application;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.error.ForbiddenException;
import com.familion.common.error.NotFoundException;
import com.familion.common.security.CurrentUser;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.MemberRef;
import com.familion.finance.accounts.application.api.AccountQueryApi;
import com.familion.finance.categories.application.api.CategoryQueryApi;
import com.familion.finance.categories.application.api.CategoryRef;
import com.familion.finance.transactions.application.api.NewTransactionCommand;
import com.familion.finance.transactions.application.api.Scope;
import com.familion.finance.transactions.application.api.TransactionCommandApi;
import com.familion.finance.transactions.application.api.TransactionStatus;
import com.familion.finance.transactions.application.api.TransactionType;
import com.familion.vehicles.domain.FuelCalculator;
import com.familion.vehicles.domain.FuelLog;
import com.familion.vehicles.domain.Vehicle;
import com.familion.vehicles.infrastructure.persistence.FuelLogRepository;
import com.familion.vehicles.infrastructure.persistence.VehicleRepository;
import com.familion.vehicles.infrastructure.web.FuelLogCreateRequest;
import com.familion.vehicles.infrastructure.web.FuelLogResponse;
import com.familion.vehicles.infrastructure.web.UserSummary;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FuelLogService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final VehicleRepository vehicleRepository;
    private final FuelLogRepository fuelLogRepository;
    private final TransactionCommandApi transactionCommandApi;
    private final CategoryQueryApi categoryQueryApi;
    private final AccountQueryApi accountQueryApi;
    private final FamilyQueryApi familyQueryApi;
    private final VehicleService vehicleService;

    public FuelLogService(
            VehicleRepository vehicleRepository,
            FuelLogRepository fuelLogRepository,
            TransactionCommandApi transactionCommandApi,
            CategoryQueryApi categoryQueryApi,
            AccountQueryApi accountQueryApi,
            FamilyQueryApi familyQueryApi,
            VehicleService vehicleService) {
        this.vehicleRepository = vehicleRepository;
        this.fuelLogRepository = fuelLogRepository;
        this.transactionCommandApi = transactionCommandApi;
        this.categoryQueryApi = categoryQueryApi;
        this.accountQueryApi = accountQueryApi;
        this.familyQueryApi = familyQueryApi;
        this.vehicleService = vehicleService;
    }

    @Transactional
    public FuelLogResponse createFuelLog(CurrentUser user, UUID vehicleId, FuelLogCreateRequest req) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        Objects.requireNonNull(req, "req must not be null");

        Vehicle vehicle = vehicleRepository
                .findByIdAndFamilyIdAndDeletedAtIsNull(vehicleId, user.familyId())
                .orElseThrow(() -> new NotFoundException("Vehicle not found"));

        if (!req.fuelType().isValidForFuelLog()) {
            throw new BusinessRuleException("Fuel type cannot be HIBRIDO for a fuel log");
        }

        LocalDate fuelDate = req.fuelDate() != null
                ? req.fuelDate()
                : vehicleService.resolveToday(user.familyId());

        Integer maxBefore = fuelLogRepository.findMaxOdometerBefore(vehicleId, fuelDate);
        if (maxBefore != null && req.odometerKm() < maxBefore) {
            throw new BusinessRuleException(
                    "Odometer must be greater than or equal to previous fuel log odometer");
        }

        Integer minAfter = fuelLogRepository.findMinOdometerAfter(vehicleId, fuelDate);
        if (minAfter != null && req.odometerKm() > minAfter) {
            throw new BusinessRuleException(
                    "Odometer must be less than or equal to next fuel log odometer");
        }

        FuelCalculator.FuelAmounts amounts = FuelCalculator.resolveAmounts(
                req.liters(), req.pricePerLiter(), req.totalAmount());

        accountQueryApi.requireUsable(user, req.accountId());
        CategoryRef category = categoryQueryApi.requireSystemCategory("VEHICLES_FUEL");

        String currency = req.currencyCode().trim().toUpperCase(Locale.ROOT);
        UUID attributedTo = req.attributedToUserId() != null ? req.attributedToUserId() : user.userId();

        NewTransactionCommand txCmd = new NewTransactionCommand(
                user.familyId(),
                user.userId(),
                attributedTo,
                req.accountId(),
                category.id(),
                TransactionType.EXPENSE,
                Scope.PERSONAL,
                TransactionStatus.POSTED,
                amounts.totalAmount(),
                currency,
                fuelDate,
                "Combustible · " + vehicle.getNickname(),
                req.notes(),
                null, null, null, null, null, null, null);

        UUID transactionId = transactionCommandApi.record(txCmd);

        FuelLog fuelLog = new FuelLog(
                user.familyId(),
                vehicleId,
                transactionId,
                user.userId(),
                fuelDate,
                req.odometerKm(),
                req.fuelType(),
                amounts.liters(),
                amounts.pricePerLiter(),
                amounts.totalAmount(),
                currency,
                req.fullTank() == null || req.fullTank(),
                req.stationName() != null ? req.stationName().trim() : null,
                req.notes() != null ? req.notes().trim() : null);

        FuelLog saved = fuelLogRepository.save(fuelLog);

        vehicle.updateOdometerIfGreater(req.odometerKm());
        vehicleRepository.save(vehicle);

        MemberRef author = familyQueryApi.requireMember(user.familyId(), user.userId());
        UserSummary userSummary = new UserSummary(author.userId(), author.fullName());

        return toResponse(saved, userSummary);
    }

    @Transactional(readOnly = true)
    public Page<FuelLogResponse> listFuelLogs(CurrentUser user, UUID vehicleId, Pageable pageable) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");

        vehicleRepository
                .findByIdAndFamilyIdAndDeletedAtIsNull(vehicleId, user.familyId())
                .orElseThrow(() -> new NotFoundException("Vehicle not found"));

        Pageable effectivePageable = clampPageable(pageable);
        Page<FuelLog> page = fuelLogRepository
                .findAllByVehicleIdOrderByFuelDateDescCreatedAtDesc(vehicleId, effectivePageable);

        if (page.isEmpty()) {
            return new PageImpl<>(java.util.List.of(), effectivePageable, page.getTotalElements());
        }

        Map<UUID, UserSummary> userMap = new HashMap<>();
        return page.map(log -> {
            UserSummary summary = userMap.computeIfAbsent(log.getUserId(), uid -> {
                try {
                    MemberRef m = familyQueryApi.requireMember(user.familyId(), uid);
                    return new UserSummary(m.userId(), m.fullName());
                } catch (Exception e) {
                    return new UserSummary(uid, "Usuario");
                }
            });
            return toResponse(log, summary);
        });
    }

    @Transactional
    public void deleteFuelLog(CurrentUser user, UUID vehicleId, UUID logId) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        Objects.requireNonNull(logId, "logId must not be null");

        vehicleRepository
                .findByIdAndFamilyIdAndDeletedAtIsNull(vehicleId, user.familyId())
                .orElseThrow(() -> new NotFoundException("Vehicle not found"));

        FuelLog log = fuelLogRepository
                .findByIdAndVehicleId(logId, vehicleId)
                .orElseThrow(() -> new NotFoundException("Fuel log not found"));

        if (!user.isAdult() && !user.userId().equals(log.getUserId())) {
            throw new ForbiddenException("Only the author or an adult user can delete this fuel log");
        }

        transactionCommandApi.softDelete(user.familyId(), log.getTransactionId());
        if (fuelLogRepository.existsById(logId)) {
            fuelLogRepository.delete(log);
        }
    }

    private static Pageable clampPageable(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, DEFAULT_PAGE_SIZE);
        }
        int pageNumber = Math.max(pageable.getPageNumber(), 0);
        int pageSize = Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE);
        return PageRequest.of(pageNumber, pageSize, pageable.getSort());
    }

    public static FuelLogResponse toResponse(FuelLog log, UserSummary user) {
        return new FuelLogResponse(
                log.getId(),
                log.getFuelDate(),
                log.getOdometerKm(),
                log.getFuelType(),
                log.getLiters(),
                log.getPricePerLiter(),
                log.getTotalAmount(),
                log.getCurrencyCode(),
                log.isFullTank(),
                log.getStationName(),
                log.getNotes(),
                log.getTransactionId(),
                user);
    }
}
