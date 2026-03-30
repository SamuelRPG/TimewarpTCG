package com.saroga.timewarp.domain.model.vo.card;

import com.saroga.timewarp.domain.model.enums.TargetGroup;

public record TargetingRequirement(
        int requiredCount,
        TargetGroup targetGroup
) {
    @Override
    public int requiredCount() {
        return requiredCount;
    }

    @Override
    public TargetGroup targetGroup() {
        return targetGroup;
    }
}
