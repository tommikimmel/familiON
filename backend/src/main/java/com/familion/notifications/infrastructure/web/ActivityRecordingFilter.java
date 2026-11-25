package com.familion.notifications.infrastructure.web;

import com.familion.common.events.FamilyActivityEvent;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.CurrentUserProvider;
import com.familion.notifications.application.ActivityDescriber;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * Publishes a {@link FamilyActivityEvent} after every successful user-facing mutation, so the activity
 * feed covers all changes without each service having to remember to emit one. Registered after the
 * Spring Security chain (default filter order), so the authenticated user is available.
 */
@Component
public class ActivityRecordingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ActivityRecordingFilter.class);
    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final int BODY_CACHE_LIMIT = 16 * 1024;
    private static final List<String> LABEL_FIELDS =
            List.of("name", "nickname", "description", "email", "stationName", "currencyCode");

    private final ApplicationEventPublisher eventPublisher;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;

    public ActivityRecordingFilter(
            ApplicationEventPublisher eventPublisher,
            CurrentUserProvider currentUserProvider,
            ObjectMapper objectMapper) {
        this.eventPublisher = eventPublisher;
        this.currentUserProvider = currentUserProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !MUTATING.contains(request.getMethod())
                || ActivityDescriber.describe(request.getMethod(), pathOf(request)).isEmpty();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request, BODY_CACHE_LIMIT);
        chain.doFilter(wrapped, response);

        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            return;
        }
        try {
            CurrentUser user = currentUserProvider.get();
            eventPublisher.publishEvent(new FamilyActivityEvent(
                    user.familyId(), user.userId(), request.getMethod(), pathOf(request), labelOf(wrapped)));
        } catch (RuntimeException e) {
            // Never fail a request that already succeeded because the feed could not be written.
            log.warn("Could not record activity for {} {}: {}", request.getMethod(), pathOf(request), e.getMessage());
        }
    }

    private String labelOf(ContentCachingRequestWrapper request) {
        byte[] body = request.getContentAsByteArray();
        if (body.length == 0) {
            return null;
        }
        try {
            JsonNode json = objectMapper.readTree(body);
            for (String field : LABEL_FIELDS) {
                JsonNode value = json.get(field);
                if (value != null && value.isTextual() && !value.asText().isBlank()) {
                    return value.asText().trim();
                }
            }
        } catch (IOException | RuntimeException e) {
            log.debug("Activity body is not JSON, recording without label");
        }
        return null;
    }

    private static String pathOf(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
