package com.familion.bff.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/bff")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

    private final RestClient coreRestClient;
    private final AuthCookieService cookieService;
    private final AccessTokenResolver accessTokenResolver;
    private final ObjectMapper objectMapper;

    public AuthController(
            RestClient coreRestClient,
            AuthCookieService cookieService,
            AccessTokenResolver accessTokenResolver,
            ObjectMapper objectMapper) {
        this.coreRestClient = coreRestClient;
        this.cookieService = cookieService;
        this.accessTokenResolver = accessTokenResolver;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(HttpServletRequest request) throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        Optional<String> deviceToken = cookieService.readCookie(request, AuthCookieService.DEVICE_TOKEN_COOKIE);
        if (deviceToken.isPresent()) {
            body = injectDeviceToken(body, deviceToken.get());
        }
        return handleTokenAuth("/api/v1/auth/login", body, request);
    }

    @PostMapping("/auth/2fa/verify")
    public ResponseEntity<?> verify2fa(HttpServletRequest request) throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        return handleTokenAuth("/api/v1/auth/2fa/verify", body, request);
    }

    @PostMapping("/auth/register-invite")
    public ResponseEntity<?> registerInvite(HttpServletRequest request) throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        return handleTokenAuth("/api/v1/auth/register-invite", body, request);
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request) {
        Optional<String> refreshToken = cookieService.readCookie(request, AuthCookieService.REFRESH_TOKEN_COOKIE);
        if (refreshToken.isEmpty()) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED, "Missing refresh token.");
            problem.setTitle("Unauthorized");
            problem.setInstance(URI.create(request.getRequestURI()));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem);
        }

        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(Map.of("refreshToken", refreshToken.get()));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to serialize refresh payload", ex);
        }

        CoreRawResponse coreResponse = postToCore(
                "/api/v1/auth/refresh",
                payload,
                MediaType.APPLICATION_JSON_VALUE,
                null,
                request);

        if (coreResponse.status() >= 200 && coreResponse.status() < 300) {
            CoreTokenResponse tokens = parseTokenResponse(coreResponse.body());
            HttpHeaders headers = new HttpHeaders();
            cookieService.addSessionCookies(headers, tokens);
            return ResponseEntity.noContent().headers(headers).build();
        }

        HttpHeaders responseHeaders = propagateResponseHeaders(coreResponse);
        if (coreResponse.status() == HttpStatus.UNAUTHORIZED.value()) {
            cookieService.clearSessionCookies(responseHeaders);
        }
        return ResponseEntity.status(coreResponse.status())
                .headers(responseHeaders)
                .body(coreResponse.body());
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        Optional<String> accessToken = accessTokenResolver.resolve(request);
        Optional<String> refreshToken = cookieService.readCookie(request, AuthCookieService.REFRESH_TOKEN_COOKIE);

        try {
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("refreshToken", refreshToken.orElse(null));
            byte[] payload = objectMapper.writeValueAsBytes(bodyMap);
            postToCore(
                    "/api/v1/auth/logout",
                    payload,
                    MediaType.APPLICATION_JSON_VALUE,
                    accessToken.orElse(null),
                    request);
        } catch (Exception ex) {
            log.warn("Best-effort Core logout failed: {}", ex.getMessage());
        }

        HttpHeaders headers = new HttpHeaders();
        cookieService.clearSessionCookies(headers);
        return ResponseEntity.noContent().headers(headers).build();
    }

    @PostMapping("/auth/register")
    public ResponseEntity<byte[]> register(HttpServletRequest request) throws IOException {
        return passthrough("/api/v1/auth/register", request);
    }

    @PostMapping("/auth/verify-email")
    public ResponseEntity<byte[]> verifyEmail(HttpServletRequest request) throws IOException {
        return passthrough("/api/v1/auth/verify-email", request);
    }

    @PostMapping("/auth/2fa/resend")
    public ResponseEntity<byte[]> resend2fa(HttpServletRequest request) throws IOException {
        return passthrough("/api/v1/auth/2fa/resend", request);
    }

    @PostMapping("/auth/forgot-password")
    public ResponseEntity<byte[]> forgotPassword(HttpServletRequest request) throws IOException {
        return passthrough("/api/v1/auth/forgot-password", request);
    }

    @PostMapping("/auth/reset-password")
    public ResponseEntity<byte[]> resetPassword(HttpServletRequest request) throws IOException {
        return passthrough("/api/v1/auth/reset-password", request);
    }

    private ResponseEntity<?> handleTokenAuth(
            String corePath, byte[] body, HttpServletRequest request) {
        String contentType = request.getContentType() != null
                ? request.getContentType()
                : MediaType.APPLICATION_JSON_VALUE;

        CoreRawResponse coreResponse = postToCore(corePath, body, contentType, null, request);

        if (coreResponse.status() >= 200 && coreResponse.status() < 300) {
            CoreTokenResponse tokens = parseTokenResponse(coreResponse.body());
            if (tokens.requires2fa()) {
                return ResponseEntity.ok(AuthSessionResponse.challenge(tokens.challengeToken()));
            }
            HttpHeaders headers = new HttpHeaders();
            cookieService.addSessionCookies(headers, tokens);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(AuthSessionResponse.authenticated());
        }

        return ResponseEntity.status(coreResponse.status())
                .headers(propagateResponseHeaders(coreResponse))
                .body(coreResponse.body());
    }

    private ResponseEntity<byte[]> passthrough(String corePath, HttpServletRequest request) throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        String contentType = request.getContentType() != null
                ? request.getContentType()
                : MediaType.APPLICATION_JSON_VALUE;

        CoreRawResponse coreResponse = postToCore(corePath, body, contentType, null, request);
        return ResponseEntity.status(coreResponse.status())
                .headers(propagateResponseHeaders(coreResponse))
                .body(coreResponse.body());
    }

    private CoreRawResponse postToCore(
            String corePath,
            byte[] body,
            String contentType,
            String bearerToken,
            HttpServletRequest request) {
        RestClient.RequestBodySpec spec = coreRestClient.post()
                .uri(corePath)
                .headers(headers -> {
                    if (contentType != null && !contentType.isBlank()) {
                        headers.set(HttpHeaders.CONTENT_TYPE, contentType);
                    }
                    if (bearerToken != null && !bearerToken.isBlank()) {
                        headers.setBearerAuth(bearerToken);
                    }
                    String xff = resolveForwardedFor(request);
                    if (xff != null) {
                        headers.set(HEADER_X_FORWARDED_FOR, xff);
                    }
                });

        if (body != null && body.length > 0) {
            spec = spec.body(body);
        }

        return spec.exchange((req, res) -> new CoreRawResponse(
                res.getStatusCode().value(),
                res.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE),
                res.getHeaders().getFirst(HttpHeaders.RETRY_AFTER),
                res.getBody().readAllBytes()));
    }

    private HttpHeaders propagateResponseHeaders(CoreRawResponse coreResponse) {
        HttpHeaders headers = new HttpHeaders();
        if (coreResponse.contentType() != null && !coreResponse.contentType().isBlank()) {
            headers.set(HttpHeaders.CONTENT_TYPE, coreResponse.contentType());
        }
        if (coreResponse.retryAfter() != null && !coreResponse.retryAfter().isBlank()) {
            headers.set(HttpHeaders.RETRY_AFTER, coreResponse.retryAfter());
        }
        return headers;
    }

    private byte[] injectDeviceToken(byte[] body, String deviceToken) {
        if (body == null || body.length == 0) {
            return body;
        }
        try {
            JsonNode tree = objectMapper.readTree(body);
            if (tree instanceof ObjectNode objectNode) {
                objectNode.put("deviceToken", deviceToken);
                return objectMapper.writeValueAsBytes(objectNode);
            }
        } catch (IOException ex) {
            log.debug("Could not inject deviceToken into non-JSON login payload: {}", ex.getMessage());
        }
        return body;
    }

    private CoreTokenResponse parseTokenResponse(byte[] body) {
        try {
            return objectMapper.readValue(body, CoreTokenResponse.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse Core token response", ex);
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

    private record CoreRawResponse(int status, String contentType, String retryAfter, byte[] body) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AuthSessionResponse(boolean requires2fa, String challengeToken) {
        static AuthSessionResponse authenticated() {
            return new AuthSessionResponse(false, null);
        }

        static AuthSessionResponse challenge(String challengeToken) {
            return new AuthSessionResponse(true, challengeToken);
        }
    }
}
