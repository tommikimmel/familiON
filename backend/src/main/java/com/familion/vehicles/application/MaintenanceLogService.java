package com.familion.vehicles.application;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.error.ForbiddenException;
import com.familion.common.error.NotFoundException;
import com.familion.common.money.Money;
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
import com.familion.vehicles.domain.MaintenanceLog;
import com.familion.vehicles.domain.MaintenanceType;
import com.familion.vehicles.domain.Vehicle;
import com.familion.vehicles.infrastructure.persistence.MaintenanceLogRepository;
import com.familion.vehicles.infrastructure.persistence.VehicleRepository;
import com.familion.vehicles.infrastructure.web.MaintenanceLogCreateRequest;
import com.familion.vehicles.infrastructure.web.MaintenanceLogResponse;
import com.familion.vehicles.infrastructure.web.UserSummary;
import java.math.BigDecimal;
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
public class MaintenanceLogService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final VehicleRepository vehicleRepository;
    private final MaintenanceLogRepository maintenanceLogRepository;
    private final TransactionCommandApi transactionCommandApi;
    private final CategoryQueryApi categoryQueryApi;
    private final AccountQueryApi accountQueryApi;
    private final FamilyQueryApi familyQueryApi;
    private final VehicleService vehicleService;

    public MaintenanceLogService(
            VehicleRepository vehicleRepository,
            MaintenanceLogRepository maintenanceLogRepository,
            TransactionCommandApi transactionCommandApi,
            CategoryQueryApi categoryQueryApi,
            AccountQueryApi accountQueryApi,
            FamilyQueryApi familyQueryApi,
            VehicleService vehicleService) {
        this.vehicleRepository = vehicleRepository;
        this.maintenanceLogRepository = maintenanceLogRepository;
        this.transactionCommandApi = transactionCommandApi;
        this.categoryQueryApi = categoryQueryApi;
        this.accountQueryApi = accountQueryApi;
        this.familyQueryApi = familyQueryApi;
        this.vehicleService = vehicleService;
    }

    @Transactional
    public MaintenanceLogResponse createMaintenanceLog(
            CurrentUser user, UUID vehicleId, MaintenanceLogCreateRequest req) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        Objects.requireNonNull(req, "req must not be null");

        Vehicle vehicle = vehicleRepository
                .findByIdAndFamilyIdAndDeletedAtIsNull(vehicleId, user.familyId())
                .orElseThrow(() -> new NotFoundException("Vehicle not found"));

        if (req.nextDueKm() != null && req.nextDueKm() <= req.odometerKm()) {
            throw new BusinessRuleException("Next due km must be greater than current odometer km");
        }

        LocalDate maintenanceDate = req.maintenanceDate() != null
                ? req.maintenanceDate()
                : vehicleService.resolveToday(user.familyId());

        BigDecimal scaledCost = Money.scale(req.costAmount());
        UUID transactionId = null;

        if (scaledCost.compareTo(BigDecimal.ZERO) > 0) {
            if (req.accountId() == null) {
                throw new BusinessRuleException(
                        "Account is required when maintenance cost is greater than zero");
            }
            accountQueryApi.requireUsable(user, req.accountId());
            CategoryRef category = categoryQueryApi.requireSystemCategory("VEHICLES_MAINTENANCE");

            String currency = req.currencyCode().trim().toUpperCase(Locale.ROOT);
            String description = req.maintenanceType().getLabel() + " · " + vehicle.getNickname();

            NewTransactionCommand txCmd = new NewTransactionCommand(
                    user.familyId(),
                    user.userId(),
                    user.userId(),
                    req.accountId(),
                    category.id(),
                    TransactionType.EXPENSE,
                    Scope.PERSONAL,
                    TransactionStatus.POSTED,
                    scaledCost,
                    currency,
                    maintenanceDate,
                    description,
                    req.notes(),
                    null, null, null, null, null, null, null);

            transactionId = transactionCommandApi.record(txCmd);
        }

        MaintenanceLog log = new MaintenanceLog(
                user.familyId(),
                vehicleId,
                transactionId,
                user.userId(),
                req.maintenanceType(),
                maintenanceDate,
                req.odometerKm(),
                req.description().trim(),
                req.workshopName() != null ? req.workshopName().trim() : null,
                scaledCost,
                req.currencyCode().trim().toUpperCase(Locale.ROOT),
                req.nextDueKm(),
                req.nextDueDate(),
                req.notes() != null ? req.notes().trim() : null);

        MaintenanceLog saved = maintenanceLogRepository.save(log);

        vehicle.updateOdometerIfGreater(req.odometerKm());
        if (req.maintenanceType() == MaintenanceType.VTV && req.nextDueDate() != null) {
            vehicle.setVtvExpirationDate(req.nextDueDate());
        }
        vehicleRepository.save(vehicle);

        MemberRef author = familyQueryApi.requireMember(user.familyId(), user.userId());
        UserSummary userSummary = new UserSummary(author.userId(), author.fullName());

        return toResponse(saved, req.accountId(), userSummary);
    }

    @Transactional(readOnly = true)
    public Page<MaintenanceLogResponse> listMaintenanceLogs(
            CurrentUser user, UUID vehicleId, Pageable pageable) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");

        vehicleRepository
                .findByIdAndFamilyIdAndDeletedAtIsNull(vehicleId, user.familyId())
                .orElseThrow(() -> new NotFoundException("Vehicle not found"));

        Pageable effectivePageable = clampPageable(pageable);
        Page<MaintenanceLog> page = maintenanceLogRepository
                .findAllByVehicleIdOrderByMaintenanceDateDescCreatedAtDesc(vehicleId, effectivePageable);

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
            return toResponse(log, null, summary);
        });
    }

    @Transactional
    public void deleteMaintenanceLog(CurrentUser user, UUID vehicleId, UUID logId) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        Objects.requireNonNull(logId, "logId must not be null");

        vehicleRepository
                .findByIdAndFamilyIdAndDeletedAtIsNull(vehicleId, user.familyId())
                .orElseThrow(() -> new NotFoundException("Vehicle not found"));

        MaintenanceLog log = maintenanceLogRepository
                .findByIdAndVehicleId(logId, vehicleId)
                .orElseThrow(() -> new NotFoundException("Maintenance log not found"));

        if (!user.isAdult() && !user.userId().equals(log.getUserId())) {
            throw new ForbiddenException(
                    "Only the author or an adult user can delete this maintenance log");
        }

        if (log.getTransactionId() != null) {
            transactionCommandApi.softDelete(user.familyId(), log.getTransactionId());
        }
        maintenanceLogRepository.delete(log);
    }

    private static Pageable clampPageable(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, DEFAULT_PAGE_SIZE);
        }
        int pageNumber = Math.max(pageable.getPageNumber(), 0);
        int pageSize = Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE);
        return PageRequest.of(pageNumber, pageSize, pageable.getSort());
    }

    public static MaintenanceLogResponse toResponse(
            MaintenanceLog log, UUID accountId, UserSummary user) {
        return new MaintenanceLogResponse(
                log.getId(),
                log.getMaintenanceType(),
                log.getMaintenanceDate(),
                log.getOdometerKm(),
                log.getDescription(),
                log.getWorkshopName(),
                log.getCostAmount(),
                log.getCurrencyCode(),
                accountId,
                log.getNextDueKm(),
                log.getNextDueDate(),
                log.getNotes(),
                log.getTransactionId(),
                user);
    }
}
