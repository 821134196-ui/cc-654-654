package org.scholarship.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发放后的差额处理：补发（SUPPLEMENT，金额为正）或退回（REFUND，金额为正，方向为负向调整）。
 * 只能追加，不能修改或删除；netAmount = 原发放 + 补发 - 退回。
 * 封榜后更正若产生差额，同样通过此表登记处理依据。
 */
@Entity
@Table(name = "payment_adjustments")
public class PaymentAdjustment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 对应的原发放记录（不可变的 Payment）；封榜后新进获奖线者的补发可空 */
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    /** SUPPLEMENT（补发）/ REFUND（退回） */
    @Column(nullable = false, length = 16)
    private String type;

    /** 调整金额，始终为正数；方向由 type 表达 */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** 若差额源于封榜后更正，关联更正流水 */
    @Column(name = "correction_id")
    private Long correctionId;

    private String createdBy;
    private LocalDateTime createdAt;

    /** 处理依据（错误说明、审批依据） */
    @Lob
    @Column(nullable = false)
    private String reason;

    protected PaymentAdjustment() {
    }

    public PaymentAdjustment(Long paymentId, Long cycleId, String studentId, String type,
                             BigDecimal amount, Long correctionId, String createdBy, String reason) {
        this.paymentId = paymentId;
        this.cycleId = cycleId;
        this.studentId = studentId;
        this.type = type;
        this.amount = amount;
        this.correctionId = correctionId;
        this.createdBy = createdBy;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getPaymentId() { return paymentId; }
    public Long getCycleId() { return cycleId; }
    public String getStudentId() { return studentId; }
    public String getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public Long getCorrectionId() { return correctionId; }
    public String getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getReason() { return reason; }
}
