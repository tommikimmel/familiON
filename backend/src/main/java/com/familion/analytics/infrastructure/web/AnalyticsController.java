package com.familion.analytics.infrastructure.web;

import com.familion.analytics.application.AnalyticsSeries;
import com.familion.analytics.application.AnalyticsService;
import com.familion.analytics.application.AnalyticsSummary;
import com.familion.analytics.application.CalendarView;
import com.familion.analytics.application.CategoryBreakdown;
import com.familion.analytics.application.PersonBreakdown;
import com.familion.analytics.domain.AnalyticsGranularity;
import com.familion.analytics.domain.AnalyticsLens;
import com.familion.common.security.CurrentUserProvider;
import com.familion.common.security.View;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final CurrentUserProvider currentUserProvider;

    public AnalyticsController(
            AnalyticsService analyticsService, CurrentUserProvider currentUserProvider) {
        this.analyticsService = analyticsService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/summary")
    public AnalyticsSummary getSummary(
            @RequestParam(defaultValue = "PERSONAL") View view,
            @RequestParam(defaultValue = "NOMINAL") AnalyticsLens lens,
            @RequestParam(defaultValue = "MONTH") AnalyticsGranularity granularity,
            @RequestParam(required = false) LocalDate date) {
        return analyticsService.getSummary(
                currentUserProvider.get(), view, lens, granularity, date);
    }

    @GetMapping("/series")
    public AnalyticsSeries getSeries(
            @RequestParam(defaultValue = "PERSONAL") View view,
            @RequestParam(defaultValue = "NOMINAL") AnalyticsLens lens,
            @RequestParam(defaultValue = "MONTH") AnalyticsGranularity granularity,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(defaultValue = "12") int periods) {
        return analyticsService.getSeries(
                currentUserProvider.get(), view, lens, granularity, date, periods);
    }

    @GetMapping("/by-category")
    public CategoryBreakdown getByCategory(
            @RequestParam(defaultValue = "PERSONAL") View view,
            @RequestParam(defaultValue = "NOMINAL") AnalyticsLens lens,
            @RequestParam(defaultValue = "MONTH") AnalyticsGranularity granularity,
            @RequestParam(required = false) LocalDate date) {
        return analyticsService.getByCategory(
                currentUserProvider.get(), view, lens, granularity, date);
    }

    @GetMapping("/by-person")
    public PersonBreakdown getByPerson(
            @RequestParam(defaultValue = "FAMILY") View view,
            @RequestParam(defaultValue = "NOMINAL") AnalyticsLens lens,
            @RequestParam(defaultValue = "MONTH") AnalyticsGranularity granularity,
            @RequestParam(required = false) LocalDate date) {
        return analyticsService.getByPerson(
                currentUserProvider.get(), view, lens, granularity, date);
    }

    @GetMapping("/calendar")
    public CalendarView getCalendar(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(defaultValue = "PERSONAL") View view) {
        return analyticsService.getCalendar(currentUserProvider.get(), view, from, to);
    }
}
