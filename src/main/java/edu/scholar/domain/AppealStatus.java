package edu.scholar.domain;

public enum AppealStatus {
    /** 公示期内待处理 */
    OPEN,
    /** 异议成立，已触发重算 */
    UPHELD,
    /** 异议驳回 */
    REJECTED
}
