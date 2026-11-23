package com.familion.common.cache;

import com.familion.common.events.FamilyFinancialDataChangedEvent;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AnalyticsCacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsCacheInvalidator.class);
    private static final int SCAN_BATCH_SIZE = 100;

    private final StringRedisTemplate redisTemplate;

    public AnalyticsCacheInvalidator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFamilyFinancialDataChanged(FamilyFinancialDataChangedEvent event) {
        if (event == null || event.familyId() == null) {
            return;
        }
        String pattern = CacheKeys.analyticsPattern(event.familyId());
        try {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(SCAN_BATCH_SIZE)
                    .build();
            List<String> keysToDelete = new ArrayList<>();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    keysToDelete.add(cursor.next());
                }
            }
            if (!keysToDelete.isEmpty()) {
                redisTemplate.delete(keysToDelete);
            }
        } catch (RuntimeException ex) {
            log.warn(
                    "Failed to invalidate analytics cache for family {}: {}",
                    event.familyId(),
                    ex.getMessage());
        }
    }
}
