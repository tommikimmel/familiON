package com.familion.finance.transactions.infrastructure.web;

import com.familion.common.security.CurrentUserProvider;
import com.familion.common.security.View;
import com.familion.finance.transactions.application.CreateTransactionCommand;
import com.familion.finance.transactions.application.TransactionFilter;
import com.familion.finance.transactions.application.TransactionQueryService;
import com.familion.finance.transactions.application.TransactionService;
import com.familion.finance.transactions.application.api.TransactionStatus;
import com.familion.finance.transactions.application.api.TransactionType;
import com.familion.finance.transactions.domain.Lens;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionQueryService transactionQueryService;
    private final CurrentUserProvider currentUserProvider;

    public TransactionController(
            TransactionService transactionService,
            TransactionQueryService transactionQueryService,
            CurrentUserProvider currentUserProvider) {
        this.transactionService = transactionService;
        this.transactionQueryService = transactionQueryService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public Page<TransactionItemResponse> listTransactions(
            @RequestParam(defaultValue = "PERSONAL") View view,
            @RequestParam(defaultValue = "NOMINAL") Lens lens,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) List<TransactionType> type,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID attributedToUserId,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) List<TransactionStatus> status,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        TransactionFilter filter = new TransactionFilter(
                view,
                lens,
                from,
                to,
                type,
                categoryId,
                accountId,
                attributedToUserId,
                currency,
                status,
                q);
        return transactionQueryService
                .list(currentUserProvider.get(), filter, pageable)
                .map(TransactionItemResponse::from);
    }

    @GetMapping("/{id}")
    public TransactionItemResponse getTransaction(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "NOMINAL") Lens lens,
            @RequestParam(required = false) View view) {
        return TransactionItemResponse.from(
                transactionQueryService.getById(currentUserProvider.get(), id, lens, view));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionItemResponse createTransaction(
            @Valid @RequestBody TransactionCreateRequest request) {
        return TransactionItemResponse.from(transactionService.create(
                currentUserProvider.get(),
                new CreateTransactionCommand(
                        request.type(),
                        request.amount(),
                        request.currencyCode(),
                        request.transactionDate(),
                        request.accountId(),
                        request.targetAccountId(),
                        request.categoryId(),
                        request.description(),
                        request.notes(),
                        request.scope(),
                        request.attributedToUserId(),
                        request.receiptAttachmentId())));
    }
}
