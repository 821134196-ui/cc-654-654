package org.scholarship.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生提交的加分材料。审核人不得是提交人本人（服务层强制，见 AppealService / MaterialService）。
 * approved 且属于当前批次的材料，其 awardedPoints 计入对应分项总分。
 */
@Entity
@Table(name = "bonus_materials")
public class BonusMaterial {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    /** 加分归入的分项，如 COMPETITION / VOLUNTEER */
    @Column(name = "item_key", nullable = false)
    private String itemKey;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(nullable = false)
    private String title;

    /** 学生申报加分 */
    @Column(name = "claimed_points", nullable = false, precision = 8, scale = 2)
    private BigDecimal claimedPoints;

    /** 辅导员核定加分（可能与申报不同） */
    @Column(name = "awarded_points", precision = 8, scale = 2)
    private BigDecimal awardedPoints;

    /** PENDING / APPROVED / REJECTED */
    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    private String submittedBy;
    private LocalDateTime submittedAt;

    /** 审核人；服务层保证 reviewer != submitter */
    private String reviewedBy;
    private LocalDateTime reviewedAt;

    /** 审核意见（处理依据） */
    @Lob
    private String reviewComment;

    /** 本地保存的附件相对路径 */
    @Column(name = "attachment_path")
    private String attachmentPath;

    @Column(name = "attachment_name")
    private String attachmentName;

    protected BonusMaterial() {
    }

    public BonusMaterial(Long cycleId, String studentId, String itemKey, String itemName,
                         String title, BigDecimal claimedPoints, String submittedBy) {
        this.cycleId = cycleId;
        this.studentId = studentId;
        this.itemKey = itemKey;
        this.itemName = itemName;
        this.title = title;
        this.claimedPoints = claimedPoints;
        this.submittedBy = submittedBy;
        this.submittedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getCycleId() { return cycleId; }
    public String getStudentId() { return studentId; }
    public String getItemKey() { return itemKey; }
    public String getItemName() { return itemName; }
    public String getTitle() { return title; }
    public BigDecimal getClaimedPoints() { return claimedPoints; }
    public BigDecimal getAwardedPoints() { return awardedPoints; }
    public void setAwardedPoints(BigDecimal awardedPoints) { this.awardedPoints = awardedPoints; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSubmittedBy() { return submittedBy; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public String getAttachmentPath() { return attachmentPath; }
    public void setAttachmentPath(String attachmentPath) { this.attachmentPath = attachmentPath; }
    public String getAttachmentName() { return attachmentName; }
    public void setAttachmentName(String attachmentName) { this.attachmentName = attachmentName; }
}
