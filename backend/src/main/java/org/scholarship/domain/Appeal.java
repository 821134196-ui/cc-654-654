package org.scholarship.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 学生在公示期内针对具体分项提交的异议。
 * 成立（UPHELD）时必须给出更正后的分值，服务层据此写 ScoreCorrection 并重算新版本榜单。
 */
@Entity
@Table(name = "appeals")
public class Appeal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    /** 异议针对的分项 */
    @Column(name = "item_key", nullable = false)
    private String itemKey;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Lob
    @Column(nullable = false)
    private String reason;

    /** PENDING / UPHELD（成立）/ REJECTED（驳回）/ WITHDRAWN */
    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    private String submittedBy;
    private LocalDateTime submittedAt;

    /** 裁定人（教务） */
    private String decidedBy;
    private LocalDateTime decidedAt;

    /** 裁定依据/说明 */
    @Lob
    private String decisionComment;

    /** 异议成立时写入的更正分值；同时生成 ScoreCorrection 与新榜单版本 */
    private java.math.BigDecimal correctedScore;

    /** 裁定成立后产生的新榜单版本号 */
    private Integer resultingVersion;

    protected Appeal() {
    }

    public Appeal(Long cycleId, String studentId, String itemKey, String itemName,
                  String reason, String submittedBy) {
        this.cycleId = cycleId;
        this.studentId = studentId;
        this.itemKey = itemKey;
        this.itemName = itemName;
        this.reason = reason;
        this.submittedBy = submittedBy;
        this.submittedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getCycleId() { return cycleId; }
    public String getStudentId() { return studentId; }
    public String getItemKey() { return itemKey; }
    public String getItemName() { return itemName; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSubmittedBy() { return submittedBy; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
    public String getDecisionComment() { return decisionComment; }
    public void setDecisionComment(String decisionComment) { this.decisionComment = decisionComment; }
    public java.math.BigDecimal getCorrectedScore() { return correctedScore; }
    public void setCorrectedScore(java.math.BigDecimal correctedScore) { this.correctedScore = correctedScore; }
    public Integer getResultingVersion() { return resultingVersion; }
    public void setResultingVersion(Integer resultingVersion) { this.resultingVersion = resultingVersion; }
}
