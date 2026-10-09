package mk.ukim.finki.aibotbackend.listener;

import lombok.extern.slf4j.Slf4j;
import mk.ukim.finki.aibotbackend.events.DonationBatchSubmittedEvent;
import mk.ukim.finki.aibotbackend.service.domain.DonationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Asks Vezilka for the outcome right after a donation batch is submitted,
 * instead of waiting for the hourly {@code DonationStatusScheduler}.
 */
@Component
@Slf4j
public class DonationBatchSubmittedListener {
    private final DonationService donationService;

    public DonationBatchSubmittedListener(DonationService donationService) {
        this.donationService = donationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onDonationBatchSubmitted(DonationBatchSubmittedEvent event) {
        log.info("[ASYNC - thread: {}] Checking the Vezilka status of donation batch {}.",
            Thread.currentThread().getName(), event.batchId());
        donationService.refreshSubmittedStatuses();
    }
}
