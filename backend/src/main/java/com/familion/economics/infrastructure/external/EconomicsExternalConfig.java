package com.familion.economics.infrastructure.external;

import com.familion.economics.domain.IpcChainCalculator;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@Configuration
@EnableScheduling
public class EconomicsExternalConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    @Qualifier("dolarApiRestClient")
    public RestClient dolarApiRestClient(
            RestClient.Builder restClientBuilder,
            @Value("${ECON_DOLARAPI_URL:https://dolarapi.com}") String baseUrl) {
        return buildRestClient(restClientBuilder, baseUrl);
    }

    @Bean
    @Qualifier("argentinaDatosRestClient")
    public RestClient argentinaDatosRestClient(
            RestClient.Builder restClientBuilder,
            @Value("${ECON_ARGDATOS_URL:https://api.argentinadatos.com}") String baseUrl) {
        return buildRestClient(restClientBuilder, baseUrl);
    }

    @Bean
    public IpcChainCalculator ipcChainCalculator() {
        return new IpcChainCalculator();
    }

    private RestClient buildRestClient(RestClient.Builder restClientBuilder, String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return restClientBuilder
                .clone()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
    }
}
