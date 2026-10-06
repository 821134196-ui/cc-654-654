package org.scholarship.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 奖学金发放记录。封榜后按封榜版本生成；一经创建不可修改、不可删除（系统内不提供更新入口）。
 * 发放后若发现错误，只能通过 PaymentAdjustment（补发/退回）处理差额，原发放记录保持原样作为凭据。
 */
@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    /** 发放所依据的封榜版本（留痕） */
    @Column(name = "ranking_version_id", nullable = false)
    private Long rankingVersionId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** PAID（已发放） */
    @Column(nullable = false, length = 16)
    private String status = "PAID";

    private String createdBy;
    private LocalDateTime paidAt;

    /** 发放批次号，便于整批追溯 */
    @Column(name = "batch_no", nullable = false)
    private String batchNo;

    protected Payment() {
    }

    public Payment(Long cycleId, String studentId, Long rankingVersionId,
                   BigDecimal amount, String createdBy, String batchNo) {
        this.cycleId = cycleId;
        this.studentId = studentId;
        this.rankingVersionId = rankingVersionId;
        this.amount = amount;
        this.createdBy = createdBy;
        this.batchNo = batchNo;
        this.paidAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getCycleId() { return cycleId; }
    public String getStudentId() { return studentId; }
    public Long getRankingVersionId() { return rankingVersionId; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public String getBatchNo() { return batchNo; }
}
