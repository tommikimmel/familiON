package com.familion.auth.infrastructure.web;

import com.familion.auth.application.RequestIpProvider;
import com.familion.auth.domain.EmailSecurityCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class ServletRequestIpProvider implements RequestIpProvider {

    @Override
    public String currentRequestIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return extractClientIp(servletAttributes.getRequest());
        }
        return null;
    }

    public static String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int commaIndex = forwardedFor.indexOf(',');
            String first = commaIndex >= 0 ? forwardedFor.substring(0, commaIndex) : forwardedFor;
            String trimmed = first.trim();
            if (!trimmed.isEmpty()) {
                return EmailSecurityCode.truncateIp(trimmed);
            }
        }
        return EmailSecurityCode.truncateIp(request.getRemoteAddr());
    }
}
