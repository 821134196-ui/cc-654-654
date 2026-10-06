package edu.scholar.domain;

/** 发放差额处理类型：补发 / 退回。原发放记录永不修改。 */
public enum AdjustmentKind {
    MAKEUP,
    REFUND
}
