package com.familion.finance.installments.infrastructure.external;

import com.familion.finance.installments.application.InstallmentPostingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InstallmentPostingScheduler {

    private static final Logger log = LoggerFactory.getLogger(InstallmentPostingScheduler.class);

    private final InstallmentPostingService postingService;

    public InstallmentPostingScheduler(InstallmentPostingService postingService) {
        this.postingService = postingService;
    }

    @Scheduled(
            cron = "${familion.installments.posting-cron:0 15 3 * * *}",
            zone = "America/Argentina/Buenos_Aires")
    public void postDueInstallmentsDaily() {
        try {
            InstallmentPostingService.PostingResult result = postingService.postDueInstallments();
            if (result.postedInstallments() > 0 || result.completedPlans() > 0) {
                log.info(
                        "Posted {} due installments and completed {} installment plans",
                        result.postedInstallments(),
                        result.completedPlans());
            }
        } catch (Exception ex) {
            log.error("Failed to post due installment transactions: {}", ex.getMessage(), ex);
        }
    }
}
