package org.scholarship.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * 基础成绩分项（如学业成绩、德育基础分），由导入接口写入。
 * (cycle, student, itemKey) 唯一，重算更正时原地更新，但每次变更另写 ScoreCorrection 留痕。
 */
@Entity
@Table(name = "base_scores",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cycle_id", "student_id", "item_key"}))
public class BaseScore {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    /** 分项标识，如 ACADEMIC / MORALITY */
    @Column(name = "item_key", nullable = false)
    private String itemKey;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal score;

    /** 得分依据，如“教务导入 2025-2026 学年 GPA 3.8”；更正时追加更正说明 */
    @Lob
    private String evidence;

    protected BaseScore() {
    }

    public BaseScore(Long cycleId, String studentId, String itemKey, String itemName,
                     BigDecimal score, String evidence) {
        this.cycleId = cycleId;
        this.studentId = studentId;
        this.itemKey = itemKey;
        this.itemName = itemName;
        this.score = score;
        this.evidence = evidence;
    }

    public Long getId() { return id; }
    public Long getCycleId() { return cycleId; }
    public String getStudentId() { return studentId; }
    public String getItemKey() { return itemKey; }
    public String getItemName() { return itemName; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
}
