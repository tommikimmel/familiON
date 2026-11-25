package com.familion.notifications.application;

import com.familion.notifications.domain.PushSubscription;

public interface WebPushClient {

    int send(PushSubscription subscription, String payloadJson);
}
