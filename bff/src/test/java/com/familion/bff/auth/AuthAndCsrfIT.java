package com.familion.bff.auth;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.http.HttpClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthAndCsrfIT {

    @RegisterExtension
    static WireMockExtension coreMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("familion.core-url", coreMock::baseUrl);
        registry.add("familion.cookie-secure", () -> "false");
    }

    @LocalServerPort
    private int port;

    private RestClient client;

    @BeforeEach
    void setUp() {
        coreMock.resetAll();
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    @Test
    void csrfEndpointReturns204AndSetsNonHttpOnlyStrictCookie() {
        RawResponse response = get("/bff/csrf");

        assertThat(response.status()).isEqualTo(204);
        String xsrfCookie = findSetCookie(response.setCookies(), "XSRF-TOKEN");
        assertThat(xsrfCookie).isNotNull();
        assertThat(xsrfCookie).contains("Path=/");
        assertThat(xsrfCookie).contains("SameSite=Strict");
        assertThat(xsrfCookie).doesNotContainIgnoringCase("HttpOnly");
    }

    @Test
    void postWithoutCsrfHeaderReturns403AndPassesWithMatchingToken() {
        RawResponse forbidden = postRaw("/bff/auth/login", "{\"email\":\"a@b.com\",\"password\":\"pw\"}", null, null);
        assertThat(forbidden.status()).isEqualTo(403);
        assertThat(forbidden.contentType()).contains("application/problem+json");

        String csrfToken = fetchCsrfToken();
        coreMock.stubFor(post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "requires2fa": false,
                                  "challengeToken": null,
                                  "accessToken": "jwt-access-123",
                                  "accessTokenExpiresIn": 900,
                                  "refreshToken": "jwt-refresh-456",
                                  "refreshTokenExpiresIn": 2592000,
                                  "deviceToken": null
                                }
                                """)));

        RawResponse ok = postRaw(
                "/bff/auth/login",
                "{\"email\":\"a@b.com\",\"password\":\"pw\"}",
                "XSRF-TOKEN=" + csrfToken,
                csrfToken);
        assertThat(ok.status()).isEqualTo(200);
    }

    @Test
    void loginWithTokensSetsHttpOnlyCookiesAndHidesTokensFromBody() {
        String csrfToken = fetchCsrfToken();
        coreMock.stubFor(post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "requires2fa": false,
                                  "challengeToken": null,
                                  "accessToken": "secret-access-jwt",
                                  "accessTokenExpiresIn": 900,
                                  "refreshToken": "secret-refresh-jwt",
                                  "refreshTokenExpiresIn": 2592000,
                                  "deviceToken": null
                                }
                                """)));

        RawResponse response = postRaw(
                "/bff/auth/login",
                "{\"email\":\"ana@familion.app\",\"password\":\"secret\"}",
                "XSRF-TOKEN=" + csrfToken,
                csrfToken);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("\"requires2fa\":false");
        assertThat(response.body()).doesNotContain("secret-access-jwt");
        assertThat(response.body()).doesNotContain("secret-refresh-jwt");
        assertThat(response.body()).doesNotContain("accessToken");
        assertThat(response.body()).doesNotContain("refreshToken");

        String atCookie = findSetCookie(response.setCookies(), "FAMILION_AT");
        String rtCookie = findSetCookie(response.setCookies(), "FAMILION_RT");
        assertThat(atCookie)
                .contains("FAMILION_AT=secret-access-jwt")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Path=/bff")
                .contains("Max-Age=900");
        assertThat(rtCookie)
                .contains("FAMILION_RT=secret-refresh-jwt")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Path=/bff/auth")
                .contains("Max-Age=2592000");
    }

    @Test
    void loginWith2faChallengeReturnsChallengeTokenWithoutSessionCookies() {
        String csrfToken = fetchCsrfToken();
        coreMock.stubFor(post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "requires2fa": true,
                                  "challengeToken": "chal-xyz-999",
                                  "accessToken": null,
                                  "accessTokenExpiresIn": null,
                                  "refreshToken": null,
                                  "refreshTokenExpiresIn": null,
                                  "deviceToken": null
                                }
                                """)));

        RawResponse response = postRaw(
                "/bff/auth/login",
                "{\"email\":\"ana@familion.app\",\"password\":\"secret\"}",
                "XSRF-TOKEN=" + csrfToken,
                csrfToken);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("\"requires2fa\":true");
        assertThat(response.body()).contains("\"challengeToken\":\"chal-xyz-999\"");
        assertThat(findSetCookie(response.setCookies(), "FAMILION_AT")).isNull();
        assertThat(findSetCookie(response.setCookies(), "FAMILION_RT")).isNull();
    }

    @Test
    void loginWithDeviceTokenCookieInjectsDeviceTokenIntoCorePayload() {
        String csrfToken = fetchCsrfToken();
        coreMock.stubFor(post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "requires2fa": false,
                                  "challengeToken": null,
                                  "accessToken": "at-dt",
                                  "accessTokenExpiresIn": 900,
                                  "refreshToken": "rt-dt",
                                  "refreshTokenExpiresIn": 2592000,
                                  "deviceToken": null
                                }
                                """)));

        RawResponse response = postRaw(
                "/bff/auth/login",
                "{\"email\":\"ana@familion.app\",\"password\":\"secret\"}",
                "XSRF-TOKEN=" + csrfToken + "; FAMILION_DT=trusted-device-777",
                csrfToken);

        assertThat(response.status()).isEqualTo(200);
        coreMock.verify(postRequestedFor(urlEqualTo("/api/v1/auth/login"))
                .withRequestBody(equalToJson("""
                        {"email":"ana@familion.app","password":"secret","deviceToken":"trusted-device-777"}
                        """))
                .withHeader("Cookie", absent()));
    }

    @Test
    void verify2faWithDeviceTokenSetsDeviceCookieAndHidesTokens() {
        String csrfToken = fetchCsrfToken();
        coreMock.stubFor(post(urlEqualTo("/api/v1/auth/2fa/verify"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "requires2fa": false,
                                  "challengeToken": null,
                                  "accessToken": "at-2fa",
                                  "accessTokenExpiresIn": 900,
                                  "refreshToken": "rt-2fa",
                                  "refreshTokenExpiresIn": 2592000,
                                  "deviceToken": "dev-token-abc"
                                }
                                """)));

        RawResponse response = postRaw(
                "/bff/auth/2fa/verify",
                "{\"challengeToken\":\"chal-1\",\"code\":\"123456\",\"rememberDevice\":true}",
                "XSRF-TOKEN=" + csrfToken,
                csrfToken);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("\"requires2fa\":false");
        assertThat(response.body()).doesNotContain("dev-token-abc");
        assertThat(response.body()).doesNotContain("at-2fa");

        String dtCookie = findSetCookie(response.setCookies(), "FAMILION_DT");
        assertThat(dtCookie)
                .contains("FAMILION_DT=dev-token-abc")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Path=/bff/auth")
                .contains("Max-Age=2592000");
    }

    @Test
    void loginPropagatesCoreErrorStatusAndProblemJson() {
        String csrfToken = fetchCsrfToken();
        String problemJson = "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"Bad credentials\"}";
        coreMock.stubFor(post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody(problemJson)));

        RawResponse response = postRaw(
                "/bff/auth/login",
                "{\"email\":\"ana@familion.app\",\"password\":\"wrong\"}",
                "XSRF-TOKEN=" + csrfToken,
                csrfToken);

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.contentType()).contains("application/problem+json");
        assertThat(response.body()).isEqualTo(problemJson);
    }

    @Test
    void refreshHandlesMissingCookieSuccessRotationAndCore401CookieClearing() {
        String csrfToken = fetchCsrfToken();

        RawResponse missing = postRaw("/bff/auth/refresh", "", "XSRF-TOKEN=" + csrfToken, csrfToken);
        assertThat(missing.status()).isEqualTo(401);
        assertThat(missing.contentType()).contains("application/problem+json");

        coreMock.stubFor(post(urlEqualTo("/api/v1/auth/refresh"))
                .withRequestBody(equalToJson("{\"refreshToken\":\"old-rt\"}"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "requires2fa": false,
                                  "challengeToken": null,
                                  "accessToken": "new-at",
                                  "accessTokenExpiresIn": 900,
                                  "refreshToken": "new-rt",
                                  "refreshTokenExpiresIn": 2592000,
                                  "deviceToken": null
                                }
                                """)));

        RawResponse rotated = postRaw(
                "/bff/auth/refresh",
                "",
                "XSRF-TOKEN=" + csrfToken + "; FAMILION_RT=old-rt",
                csrfToken);
        assertThat(rotated.status()).isEqualTo(204);
        assertThat(rotated.body()).isEmpty();
        assertThat(findSetCookie(rotated.setCookies(), "FAMILION_AT")).contains("FAMILION_AT=new-at");
        assertThat(findSetCookie(rotated.setCookies(), "FAMILION_RT")).contains("FAMILION_RT=new-rt");

        coreMock.stubFor(post(urlEqualTo("/api/v1/auth/refresh"))
                .withRequestBody(equalToJson("{\"refreshToken\":\"revoked-rt\"}"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"title\":\"Unauthorized\",\"status\":401}")));

        RawResponse revoked = postRaw(
                "/bff/auth/refresh",
                "",
                "XSRF-TOKEN=" + csrfToken + "; FAMILION_RT=revoked-rt",
                csrfToken);
        assertThat(revoked.status()).isEqualTo(401);
        assertThat(findSetCookie(revoked.setCookies(), "FAMILION_AT")).contains("Max-Age=0");
        assertThat(findSetCookie(revoked.setCookies(), "FAMILION_RT")).contains("Max-Age=0");
    }

    @Test
    void logoutClearsCookiesEvenWhenCoreFails() {
        String csrfToken = fetchCsrfToken();
        coreMock.stubFor(post(urlEqualTo("/api/v1/auth/logout"))
                .willReturn(aResponse().withStatus(500).withBody("internal failure")));

        RawResponse response = postRaw(
                "/bff/auth/logout",
                "",
                "XSRF-TOKEN=" + csrfToken + "; FAMILION_AT=at-logout; FAMILION_RT=rt-logout",
                csrfToken);

        assertThat(response.status()).isEqualTo(204);
        assertThat(findSetCookie(response.setCookies(), "FAMILION_AT")).contains("Max-Age=0");
        assertThat(findSetCookie(response.setCookies(), "FAMILION_RT")).contains("Max-Age=0");
        coreMock.verify(postRequestedFor(urlEqualTo("/api/v1/auth/logout"))
                .withHeader("Authorization", equalTo("Bearer at-logout"))
                .withRequestBody(equalToJson("{\"refreshToken\":\"rt-logout\"}")));
    }

    @Test
    void logoutClearsCookiesEvenWhenCoreConnectionFails() {
        String csrfToken = fetchCsrfToken();
        coreMock.stubFor(post(urlEqualTo("/api/v1/auth/logout"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        RawResponse response = postRaw(
                "/bff/auth/logout",
                "",
                "XSRF-TOKEN=" + csrfToken + "; FAMILION_AT=at-logout; FAMILION_RT=rt-logout",
                csrfToken);

        assertThat(response.status()).isEqualTo(204);
        assertThat(findSetCookie(response.setCookies(), "FAMILION_AT")).contains("Max-Age=0");
        assertThat(findSetCookie(response.setCookies(), "FAMILION_RT")).contains("Max-Age=0");
    }

    @Test
    void registerInviteSetsCookiesWithoutExposingTokens() {
        String csrfToken = fetchCsrfToken();
        coreMock.stubFor(post(urlEqualTo("/api/v1/auth/register-invite"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "requires2fa": false,
                                  "challengeToken": null,
                                  "accessToken": "at-inv",
                                  "accessTokenExpiresIn": 900,
                                  "refreshToken": "rt-inv",
                                  "refreshTokenExpiresIn": 2592000,
                                  "deviceToken": null
                                }
                                """)));

        RawResponse response = postRaw(
                "/bff/auth/register-invite",
                "{\"token\":\"inv-123\",\"password\":\"secret\"}",
                "XSRF-TOKEN=" + csrfToken,
                csrfToken);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("\"requires2fa\":false");
        assertThat(response.body()).doesNotContain("at-inv");
        assertThat(findSetCookie(response.setCookies(), "FAMILION_AT")).contains("FAMILION_AT=at-inv");
        assertThat(findSetCookie(response.setCookies(), "FAMILION_RT")).contains("FAMILION_RT=rt-inv");
    }

    @Test
    void passthroughAuthRoutesForwardBodyStatusAndContentTypeWithoutBrowserCookies() {
        String csrfToken = fetchCsrfToken();
        coreMock.stubFor(post(urlEqualTo("/api/v1/auth/register"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"verification_sent\"}")));

        RawResponse response = postRaw(
                "/bff/auth/register",
                "{\"email\":\"new@familion.app\"}",
                "XSRF-TOKEN=" + csrfToken + "; FAMILION_AT=ignored",
                csrfToken);

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.contentType()).contains("application/json");
        assertThat(response.body()).isEqualTo("{\"message\":\"verification_sent\"}");
        coreMock.verify(postRequestedFor(urlEqualTo("/api/v1/auth/register"))
                .withHeader("Cookie", absent()));
    }

    private String fetchCsrfToken() {
        RawResponse res = get("/bff/csrf");
        String header = findSetCookie(res.setCookies(), "XSRF-TOKEN");
        assertThat(header).isNotNull();
        String firstPart = header.split(";")[0];
        return firstPart.substring("XSRF-TOKEN=".length());
    }

    private RawResponse get(String path) {
        return client.get()
                .uri(path)
                .exchange((req, res) -> new RawResponse(
                        res.getStatusCode().value(),
                        res.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE),
                        res.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE),
                        new String(res.getBody().readAllBytes())));
    }

    private RawResponse postRaw(String path, String body, String cookieHeader, String csrfHeader) {
        RestClient.RequestBodySpec spec = client.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON);
        if (cookieHeader != null) {
            spec = spec.header(HttpHeaders.COOKIE, cookieHeader);
        }
        if (csrfHeader != null) {
            spec = spec.header("X-XSRF-TOKEN", csrfHeader);
        }
        return spec.body(body)
                .exchange((req, res) -> new RawResponse(
                        res.getStatusCode().value(),
                        res.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE),
                        res.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE),
                        new String(res.getBody().readAllBytes())));
    }

    private String findSetCookie(List<String> setCookies, String name) {
        return setCookies.stream()
                .filter(c -> c.startsWith(name + "="))
                .findFirst()
                .orElse(null);
    }

    private record RawResponse(int status, String contentType, List<String> setCookies, String body) {
    }
}
