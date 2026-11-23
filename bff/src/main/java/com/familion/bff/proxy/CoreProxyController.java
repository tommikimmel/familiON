package com.familion.bff.proxy;

import com.familion.bff.auth.AccessTokenResolver;
import com.familion.bff.config.BffProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
public class CoreProxyController {

    private static final String BFF_API_PREFIX = "/bff/api";
    private static final String CORE_API_PREFIX = "/api/v1";
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";

    private final RestClient coreRestClient;
    private final BffProperties properties;
    private final AccessTokenResolver accessTokenResolver;

    public CoreProxyController(
            RestClient coreRestClient,
            BffProperties properties,
            AccessTokenResolver accessTokenResolver) {
        this.coreRestClient = coreRestClient;
        this.properties = properties;
        this.accessTokenResolver = accessTokenResolver;
    }

    @RequestMapping("/bff/api/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws IOException {
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        URI targetUri = buildTargetUri(request);
        byte[] body = request.getInputStream().readAllBytes();
        Optional<String> accessToken = accessTokenResolver.resolve(request);

        RestClient.RequestBodySpec spec = coreRestClient.method(method)
                .uri(targetUri)
                .headers(headers -> {
                    accessToken.ifPresent(headers::setBearerAuth);
                    copyHeaderIfPresent(request, headers, HttpHeaders.CONTENT_TYPE);
                    copyHeaderIfPresent(request, headers, HttpHeaders.ACCEPT);
                    copyHeaderIfPresent(request, headers, HttpHeaders.ACCEPT_LANGUAGE);
                    copyHeaderIfPresent(request, headers, HEADER_IDEMPOTENCY_KEY);
                    String xff = resolveForwardedFor(request);
                    if (xff != null && !xff.isBlank()) {
                        headers.set(HEADER_X_FORWARDED_FOR, xff);
                    }
                });

        if (body.length > 0) {
            spec = spec.body(body);
        }

        return spec.exchange((req, res) -> {
            HttpHeaders responseHeaders = new HttpHeaders();
            copyResponseHeader(res.getHeaders(), responseHeaders, HttpHeaders.CONTENT_TYPE);
            copyResponseHeader(res.getHeaders(), responseHeaders, HttpHeaders.CONTENT_DISPOSITION);
            copyResponseHeader(res.getHeaders(), responseHeaders, HttpHeaders.RETRY_AFTER);
            byte[] responseBody = res.getBody().readAllBytes();
            return ResponseEntity.status(res.getStatusCode().value())
                    .headers(responseHeaders)
                    .body(responseBody);
        });
    }

    private URI buildTargetUri(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String suffix = requestUri.startsWith(BFF_API_PREFIX)
                ? requestUri.substring(BFF_API_PREFIX.length())
                : requestUri;
        String query = request.getQueryString();
        StringBuilder url = new StringBuilder(properties.coreUrl())
                .append(CORE_API_PREFIX)
                .append(suffix);
        if (query != null && !query.isEmpty()) {
            url.append('?').append(query);
        }
        return URI.create(url.toString());
    }

    private void copyHeaderIfPresent(HttpServletRequest request, HttpHeaders target, String headerName) {
        String value = request.getHeader(headerName);
        if (value != null && !value.isBlank()) {
            target.set(headerName, value);
        }
    }

    private void copyResponseHeader(HttpHeaders source, HttpHeaders target, String headerName) {
        String value = source.getFirst(headerName);
        if (value != null && !value.isBlank()) {
            target.set(headerName, value);
        }
    }

    private String resolveForwardedFor(HttpServletRequest request) {
        String existing = request.getHeader(HEADER_X_FORWARDED_FOR);
        String remoteAddr = request.getRemoteAddr();
        if (existing != null && !existing.isBlank()) {
            return remoteAddr != null && !remoteAddr.isBlank()
                    ? existing + ", " + remoteAddr
                    : existing;
        }
        return remoteAddr;
    }
}
