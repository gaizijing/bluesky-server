package com.bluesky.scheduler.service;

import com.bluesky.scheduler.RuleType;
import com.bluesky.scheduler.event.RulePublishedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RulePublishNotifier {

    private final ApplicationEventPublisher eventPublisher;

    public void notifyPublished(RuleType ruleType, String ruleSetId) {
        eventPublisher.publishEvent(new RulePublishedEvent(this, ruleType, ruleSetId, null));
    }
}
