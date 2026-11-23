package com.familion.bff.ratelimit;

import com.familion.bff.auth.AccessTokenResolver;
import com.familion.bff.config.BffProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String USER_KEY_PREFIX = "rl:bff:user:";
    private static final String IP_KEY_PREFIX = "rl:bff:ip:";
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final long RECONNECT_BACKOFF_NANOS = TimeUnit.SECONDS.toNanos(2);

    private final AccessTokenResolver accessTokenResolver;
    private final ObjectMapper objectMapper;
    private final Supplier<BucketConfiguration> bucketConfigurationSupplier;
    private final RedisClient redisClient;
    private final Object connectionLock = new Object();

    private volatile StatefulRedisConnection<String, byte[]> connection;
    private volatile ProxyManager<String> proxyManager;
    private volatile long lastFailureNanos;

    public RateLimitFilter(
            BffProperties bffProperties,
            RedisProperties redisProperties,
            AccessTokenResolver accessTokenResolver,
            ObjectMapper objectMapper) {
        this.accessTokenResolver = accessTokenResolver;
        this.objectMapper = objectMapper;
        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(bffProperties.rateLimitPerMin())
                        .refillGreedy(bffProperties.rateLimitPerMin(), Duration.ofMinutes(1))
                        .build())
                .build();
        this.bucketConfigurationSupplier = () -> config;
        this.redisClient = createRedisClient(redisProperties);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/actuator") || path.startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        ProxyManager<String> manager = getOrCreateProxyManager();
        if (manager == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = resolveRateLimitKey(request);
        ConsumptionProbe probe;
        try {
            probe = manager.builder()
                    .build(key, bucketConfigurationSupplier)
                    .tryConsumeAndReturnRemaining(1);
        } catch (Exception ex) {
            log.warn("Rate limit check failed open (Redis unavailable): {}", ex.getMessage());
            resetIfClosed();
            filterChain.doFilter(request, response);
            return;
        }

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long nanosToWait = probe.getNanosToWaitForRefill();
        long retryAfterSeconds = Math.max(1L, (nanosToWait + 999_999_999L) / 1_000_000_000L);
        writeTooManyRequests(request, response, retryAfterSeconds);
    }

    String resolveRateLimitKey(HttpServletRequest request) {
        Optional<String> subject = accessTokenResolver.resolve(request)
                .flatMap(this::extractSubjectWithoutVerification);
        if (subject.isPresent()) {
            return USER_KEY_PREFIX + subject.get();
        }
        return IP_KEY_PREFIX + resolveClientIp(request);
    }

    Optional<String> extractSubjectWithoutVerification(String jwt) {
        if (jwt == null || jwt.isBlank()) {
            return Optional.empty();
        }
        String[] parts = jwt.split("\\.");
        if (parts.length < 2 || parts[1].isBlank()) {
            return Optional.empty();
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = objectMapper.readTree(decoded);
            JsonNode sub = payload.get("sub");
            if (sub != null && sub.isTextual() && !sub.asText().isBlank()) {
                return Optional.of(sub.asText().trim());
            }
        } catch (IllegalArgumentException | IOException ex) {
            log.debug("Could not decode JWT payload for rate limit key: {}", ex.getMessage());
        }
        return Optional.empty();
    }

    String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(HEADER_X_FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String first = forwardedFor.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null && !remoteAddr.isBlank() ? remoteAddr : "unknown";
    }

    private ProxyManager<String> getOrCreateProxyManager() {
        ProxyManager<String> current = this.proxyManager;
        StatefulRedisConnection<String, byte[]> conn = this.connection;
        if (current != null && conn != null && conn.isOpen()) {
            return current;
        }
        long now = System.nanoTime();
        if (lastFailureNanos != 0L && (now - lastFailureNanos) < RECONNECT_BACKOFF_NANOS) {
            return null;
        }
        synchronized (connectionLock) {
            if (this.proxyManager != null && this.connection != null && this.connection.isOpen()) {
                return this.proxyManager;
            }
            if (lastFailureNanos != 0L && (System.nanoTime() - lastFailureNanos) < RECONNECT_BACKOFF_NANOS) {
                return null;
            }
            try {
                StatefulRedisConnection<String, byte[]> newConn = redisClient.connect(
                        RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
                LettuceBasedProxyManager<String> newManager = Bucket4jLettuce.casBasedBuilder(newConn)
                        .expirationAfterWrite(ExpirationAfterWriteStrategy
                                .basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10)))
                        .build();
                this.connection = newConn;
                this.proxyManager = newManager;
                this.lastFailureNanos = 0L;
                return newManager;
            } catch (Exception ex) {
                this.lastFailureNanos = System.nanoTime();
                log.warn("Rate limit Redis connection failed (failing open): {}", ex.getMessage());
                return null;
            }
        }
    }

    private void resetIfClosed() {
        StatefulRedisConnection<String, byte[]> conn = this.connection;
        if (conn == null || !conn.isOpen()) {
            synchronized (connectionLock) {
                if (this.connection != null && !this.connection.isOpen()) {
                    try {
                        this.connection.close();
                    } catch (Exception ignored) {
                        // Best-effort cleanup
                    }
                    this.connection = null;
                    this.proxyManager = null;
                    this.lastFailureNanos = System.nanoTime();
                }
            }
        }
    }

    private void writeTooManyRequests(
            HttpServletRequest request,
            HttpServletResponse response,
            long retryAfterSeconds)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded. Try again later.");
        problem.setTitle("Too Many Requests");
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private RedisClient createRedisClient(RedisProperties redisProperties) {
        Duration connectTimeout = redisProperties.getConnectTimeout() != null
                ? redisProperties.getConnectTimeout()
                : Duration.ofMillis(500);
        Duration commandTimeout = redisProperties.getTimeout() != null
                ? redisProperties.getTimeout()
                : Duration.ofMillis(500);

        RedisURI.Builder uriBuilder = RedisURI.builder()
                .withHost(redisProperties.getHost())
                .withPort(redisProperties.getPort())
                .withTimeout(commandTimeout);

        if (redisProperties.getPassword() != null && !redisProperties.getPassword().isEmpty()) {
            if (redisProperties.getUsername() != null && !redisProperties.getUsername().isEmpty()) {
                uriBuilder.withAuthentication(redisProperties.getUsername(), redisProperties.getPassword());
            } else {
                uriBuilder.withPassword(redisProperties.getPassword().toCharArray());
            }
        }

        RedisClient client = RedisClient.create(uriBuilder.build());
        client.setOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build())
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build());
        return client;
    }

    @PreDestroy
    public void shutdown() {
        synchronized (connectionLock) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ignored) {
                    // Best-effort cleanup
                }
                connection = null;
                proxyManager = null;
            }
        }
        try {
            redisClient.shutdown(Duration.ZERO, Duration.ofSeconds(1));
        } catch (Exception ignored) {
            // Best-effort cleanup
        }
    }
}
