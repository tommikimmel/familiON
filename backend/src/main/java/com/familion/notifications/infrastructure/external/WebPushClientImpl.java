package com.familion.notifications.infrastructure.external;

import com.familion.notifications.application.WebPushClient;
import com.familion.notifications.application.WebPushProperties;
import com.familion.notifications.domain.PushSubscription;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WebPushClientImpl implements WebPushClient {

    private static final Logger log = LoggerFactory.getLogger(WebPushClientImpl.class);

    private final WebPushProperties properties;
    private PushService pushService;

    public WebPushClientImpl(WebPushProperties properties) {
        this.properties = properties;
        if (properties.isConfigured()) {
            try {
                if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                    Security.addProvider(new BouncyCastleProvider());
                }
                this.pushService = new PushService(
                        properties.getPublicKey(),
                        properties.getPrivateKey(),
                        properties.getSubject());
            } catch (Exception e) {
                log.error("Failed to initialize WebPush PushService", e);
            }
        }
    }

    @Override
    public int send(PushSubscription subscription, String payloadJson) {
        if (!properties.isConfigured() || pushService == null) {
            return -1;
        }
        try {
            Notification notification = new Notification(
                    subscription.getEndpoint(),
                    subscription.getP256dhKey(),
                    subscription.getAuthKey(),
                    payloadJson.getBytes(StandardCharsets.UTF_8));
            HttpResponse response = pushService.send(notification);
            int statusCode = response.getStatusLine().getStatusCode();
            log.debug("Push sent to {}: status {}", subscription.getEndpoint(), statusCode);
            return statusCode;
        } catch (Exception e) {
            log.warn("Web push send error for {}: {}", subscription.getEndpoint(), e.getMessage());
            String msg = e.getMessage();
            if (msg != null && (msg.contains("410") || msg.contains("404") || msg.contains("Gone") || msg.contains("Not Found"))) {
                return 410;
            }
            return 500;
        }
    }
}
