package org.scholarship.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 得分更正流水：异议成立、导入勘误或封榜后更正，每次改分都写一条，作为排名变化的依据。
 * 与 RankingVersion.triggerCorrectionId 互相关联，可从榜单版本追溯到改分，再追溯到异议。
 */
@Entity
@Table(name = "score_corrections")
public class ScoreCorrection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    @Column(name = "item_key", nullable = false)
    private String itemKey;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "old_score", nullable = false, precision = 8, scale = 2)
    private BigDecimal oldScore;

    @Column(name = "new_score", nullable = false, precision = 8, scale = 2)
    private BigDecimal newScore;

    /** 来源：APPEAL（公示异议）/ IMPORT_FIX（导入勘误）/ POST_CLOSE（封榜后更正） */
    @Column(nullable = false, length = 16)
    private String source;

    /** 关联异议 id（可空） */
    @Column(name = "appeal_id")
    private Long appealId;

    private String operator;
    private LocalDateTime createdAt;

    /** 处理依据说明 */
    @Lob
    @Column(nullable = false)
    private String reason;

    protected ScoreCorrection() {
    }

    public ScoreCorrection(Long cycleId, String studentId, String itemKey, String itemName,
                           BigDecimal oldScore, BigDecimal newScore, String source,
                           Long appealId, String operator, String reason) {
        this.cycleId = cycleId;
        this.studentId = studentId;
        this.itemKey = itemKey;
        this.itemName = itemName;
        this.oldScore = oldScore;
        this.newScore = newScore;
        this.source = source;
        this.appealId = appealId;
        this.operator = operator;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getCycleId() { return cycleId; }
    public String getStudentId() { return studentId; }
    public String getItemKey() { return itemKey; }
    public String getItemName() { return itemName; }
    public BigDecimal getOldScore() { return oldScore; }
    public BigDecimal getNewScore() { return newScore; }
    public String getSource() { return source; }
    public Long getAppealId() { return appealId; }
    public String getOperator() { return operator; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getReason() { return reason; }
}
