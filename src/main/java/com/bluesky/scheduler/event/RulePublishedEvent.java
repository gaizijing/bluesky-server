package com.bluesky.scheduler.event;

import com.bluesky.scheduler.RuleType;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class RulePublishedEvent extends ApplicationEvent {

    private final RuleType ruleType;
    private final String ruleSetId;
    /** 可选；null 表示全部 enabled Region */
    private final String regionId;

    public RulePublishedEvent(Object source, RuleType ruleType, String ruleSetId, String regionId) {
        super(source);
        this.ruleType = ruleType;
        this.ruleSetId = ruleSetId;
        this.regionId = regionId;
    }
}
