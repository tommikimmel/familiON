package com.familion.finance.exchanges.application;

import com.familion.common.security.CurrentUser;
import com.familion.common.security.View;
import com.familion.common.security.ViewAccess;
import com.familion.common.security.VisibilityPolicy;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.finance.exchanges.domain.ExchangeOperation;
import com.familion.finance.exchanges.domain.ExchangeRateCalculator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ExchangeQueryService {

    static final int MAX_PAGE_SIZE = 50;
    static final int DEFAULT_PAGE_SIZE = 20;

    private final ExchangeReadQuery readQuery;
    private final VisibilityPolicy visibilityPolicy;
    private final FamilyQueryApi familyQueryApi;
    private final ExchangeRateCalculator rateCalculator;

    public ExchangeQueryService(
            ExchangeReadQuery readQuery,
            VisibilityPolicy visibilityPolicy,
            FamilyQueryApi familyQueryApi) {
        this.readQuery = readQuery;
        this.visibilityPolicy = visibilityPolicy;
        this.familyQueryApi = familyQueryApi;
        this.rateCalculator = new ExchangeRateCalculator();
    }

    public Page<ExchangeView> list(CurrentUser user, View requestedView, Pageable pageable) {
        Objects.requireNonNull(user, "user must not be null");

        View view = requestedView != null ? requestedView : View.PERSONAL;
        ViewAccess access = visibilityPolicy.resolveForListing(user, view);
        UUID viewerUserId = access == ViewAccess.OWN ? user.userId() : null;

        Pageable effectivePageable = clampPageable(pageable);

        List<ExchangeReadRow> rows = readQuery.findPage(user.familyId(), viewerUserId, effectivePageable);
        long total = readQuery.count(user.familyId(), viewerUserId);

        if (rows.isEmpty()) {
            return new PageImpl<>(List.of(), effectivePageable, total);
        }

        FamilySettings settings = familyQueryApi.getSettings(user.familyId());
        String baseCurrency = settings.baseCurrency();

        List<ExchangeView> content = rows.stream()
                .map(row -> toExchangeView(row, baseCurrency))
                .toList();

        return new PageImpl<>(content, effectivePageable, total);
    }

    private ExchangeView toExchangeView(ExchangeReadRow row, String baseCurrency) {
        ExchangeOperation operation = rateCalculator.determineOperation(
                row.sourceCurrency(), row.targetCurrency(), baseCurrency);

        return new ExchangeView(
                row.id(),
                row.exchangeDate(),
                new ExchangeView.AccountSummary(row.sourceAccountId(), row.sourceAccountName()),
                new ExchangeView.AccountSummary(row.targetAccountId(), row.targetAccountName()),
                row.sourceAmount(),
                row.sourceCurrency(),
                row.targetAmount(),
                row.targetCurrency(),
                row.appliedExchangeRate(),
                row.feeAmount(),
                row.feeCurrency(),
                row.notes(),
                operation,
                new ExchangeView.UserSummary(row.createdById(), row.createdByFullName()));
    }

    private static Pageable clampPageable(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, DEFAULT_PAGE_SIZE);
        }
        int pageNumber = Math.max(pageable.getPageNumber(), 0);
        int pageSize = Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE);
        Sort sort = pageable.getSort() != null ? pageable.getSort() : Sort.unsorted();
        return PageRequest.of(pageNumber, pageSize, sort);
    }
}
