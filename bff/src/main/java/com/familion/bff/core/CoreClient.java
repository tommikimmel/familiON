package com.familion.bff.core;

import com.familion.bff.config.BffProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CoreClient {

    private static final String API_V1_PREFIX = "/api/v1";

    private final RestClient coreRestClient;
    private final BffProperties properties;

    public CoreClient(RestClient coreRestClient, BffProperties properties) {
        this.coreRestClient = coreRestClient;
        this.properties = properties;
    }

    public <T> T get(String pathAndQuery, String accessToken, ParameterizedTypeReference<T> type) {
        return prepareGet(pathAndQuery, accessToken).body(type);
    }

    public <T> T get(String pathAndQuery, String accessToken, Class<T> type) {
        return prepareGet(pathAndQuery, accessToken).body(type);
    }

    private RestClient.ResponseSpec prepareGet(String pathAndQuery, String accessToken) {
        URI targetUri = buildTargetUri(pathAndQuery);
        return coreRestClient.get()
                .uri(targetUri)
                .headers(headers -> {
                    if (accessToken != null && !accessToken.isBlank()) {
                        headers.setBearerAuth(accessToken);
                    }
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    String problemJson = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw new CoreClientException(response.getStatusCode().value(), problemJson);
                });
    }

    private URI buildTargetUri(String pathAndQuery) {
        String normalized = pathAndQuery == null ? "" : pathAndQuery;
        if (!normalized.isEmpty() && !normalized.startsWith("/") && !normalized.startsWith("?")) {
            normalized = "/" + normalized;
        }
        return URI.create(properties.coreUrl() + API_V1_PREFIX + normalized);
    }
}
