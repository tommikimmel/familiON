package com.familion.finance.exchanges.infrastructure.web;

import com.familion.common.security.CurrentUserProvider;
import com.familion.common.security.View;
import com.familion.finance.exchanges.application.CreateExchangeCommand;
import com.familion.finance.exchanges.application.ExchangeQueryService;
import com.familion.finance.exchanges.application.ExchangeService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/currency-exchanges")
public class CurrencyExchangeController {

    private final ExchangeService exchangeService;
    private final ExchangeQueryService exchangeQueryService;
    private final CurrentUserProvider currentUserProvider;

    public CurrencyExchangeController(
            ExchangeService exchangeService,
            ExchangeQueryService exchangeQueryService,
            CurrentUserProvider currentUserProvider) {
        this.exchangeService = exchangeService;
        this.exchangeQueryService = exchangeQueryService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExchangeResponse createExchange(@Valid @RequestBody ExchangeCreateRequest request) {
        return ExchangeResponse.from(exchangeService.create(
                currentUserProvider.get(),
                new CreateExchangeCommand(
                        request.sourceAccountId(),
                        request.targetAccountId(),
                        request.sourceAmount(),
                        request.targetAmount(),
                        request.appliedExchangeRate(),
                        request.feeAmount(),
                        request.feeCurrency(),
                        request.exchangeDate(),
                        request.notes())));
    }

    @GetMapping
    public Page<ExchangeResponse> listExchanges(
            @RequestParam(defaultValue = "PERSONAL") View view,
            @PageableDefault(size = 20) Pageable pageable) {
        return exchangeQueryService
                .list(currentUserProvider.get(), view, pageable)
                .map(ExchangeResponse::from);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExchange(@PathVariable UUID id) {
        exchangeService.delete(currentUserProvider.get(), id);
    }
}
