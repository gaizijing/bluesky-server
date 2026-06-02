package com.bluesky.scheduler.listener;

import com.bluesky.scheduler.event.RulePublishedEvent;
import com.bluesky.scheduler.service.RecomputeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SchedulerEventListener {

    private final RecomputeService recomputeService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRulePublished(RulePublishedEvent event) {
        recomputeService.enqueue(event);
    }
}
