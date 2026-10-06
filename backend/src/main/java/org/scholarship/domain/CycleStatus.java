package org.scholarship.domain;

/** 奖学金评定批次状态机：DRAFT（规则未锁定）→ PUBLISHED（公示中）→ CLOSED（已封榜）。 */
public enum CycleStatus {
    DRAFT,
    PUBLISHED,
    CLOSED
}
