package edu.scholar.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 封榜发放后的差额处理记录（补发 / 退回）。
 * 原 {@link Disbursement} 永不修改；更正产生的差额全部在这里留痕。
 */
@Entity
public class DisbursementAdjustment extends IdentifiedEntity {
    @Column(nullable = false)
    public Long studentId;

    /** 触发差额的更正版本 */
    @Column(nullable = false)
    public Long correctionVersionId;

    /** 关联的原发放记录；新上榜补发时可能为空 */
    public Long disbursementId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public AdjustmentKind kind;

    /** 差额金额，分（正数） */
    @Column(nullable = false)
    public long amount;

    /** 处理依据 */
    @Column(nullable = false, length = 2000)
    public String reason;

    public Instant createdAt;
    public String createdBy;
}
