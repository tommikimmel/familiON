package com.familion.vehicles.infrastructure.external;

import com.familion.common.money.Money;
import com.familion.finance.transactions.application.api.TransactionChangedEvent;
import com.familion.vehicles.infrastructure.persistence.FuelLogRepository;
import com.familion.vehicles.infrastructure.persistence.MaintenanceLogRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class VehicleTransactionSyncListener {

    private final FuelLogRepository fuelLogRepository;
    private final MaintenanceLogRepository maintenanceLogRepository;

    public VehicleTransactionSyncListener(
            FuelLogRepository fuelLogRepository,
            MaintenanceLogRepository maintenanceLogRepository) {
        this.fuelLogRepository = fuelLogRepository;
        this.maintenanceLogRepository = maintenanceLogRepository;
    }

    @EventListener
    @Transactional
    public void onTransactionChanged(TransactionChangedEvent event) {
        if (event == null || event.transactionId() == null) {
            return;
        }

        if (event.deleted()) {
            fuelLogRepository.findByTransactionId(event.transactionId())
                    .ifPresent(log -> {
                        fuelLogRepository.delete(log);
                        fuelLogRepository.flush();
                    });

            maintenanceLogRepository.findByTransactionId(event.transactionId())
                    .ifPresent(log -> {
                        log.setTransactionId(null);
                        log.setCostAmount(BigDecimal.ZERO);
                        maintenanceLogRepository.saveAndFlush(log);
                    });
        } else {
            fuelLogRepository.findByTransactionId(event.transactionId())
                    .ifPresent(log -> {
                        log.setTotalAmount(Money.scale(event.amount()));
                        log.setFuelDate(event.date());
                        log.setPricePerLiter(event.amount().divide(log.getLiters(), 4, RoundingMode.HALF_EVEN));
                        fuelLogRepository.saveAndFlush(log);
                    });

            maintenanceLogRepository.findByTransactionId(event.transactionId())
                    .ifPresent(log -> {
                        log.setCostAmount(Money.scale(event.amount()));
                        log.setMaintenanceDate(event.date());
                        maintenanceLogRepository.saveAndFlush(log);
                    });
        }
    }
}
