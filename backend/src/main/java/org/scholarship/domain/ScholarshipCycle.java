package org.scholarship.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 奖学金评定批次。
 * 锁定（lock）时固化排名规则快照、并列规则与名额；封榜（close）后当前榜单成为封榜版本，
 * 仍允许“封榜后更正”，但每次更正都生成新版本并留存依据。
 */
@Entity
@Table(name = "scholarship_cycles")
public class ScholarshipCycle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CycleStatus status = CycleStatus.DRAFT;

    /** 规则是否已锁定；锁定后 tieRule / quota / awardAmount / rulesSnapshot 不可再改 */
    @Column(nullable = false)
    private boolean rulesLocked = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private TieRule tieRule;

    /** 获奖名额 */
    private Integer awardQuota;

    /** 每人奖金额（元） */
    @Column(precision = 12, scale = 2)
    private BigDecimal awardAmount;

    /** 排名规则全文快照，锁定时固化，作为各版本榜单的依据 */
    @Lob
    private String rulesSnapshot;

    private String lockedBy;
    private LocalDateTime lockedAt;
    private LocalDateTime publishedAt;
    /** 公示异议截止时间 */
    private LocalDateTime objectionDeadline;
    private LocalDateTime closedAt;

    protected ScholarshipCycle() {
    }

    public ScholarshipCycle(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public CycleStatus getStatus() { return status; }
    public void setStatus(CycleStatus status) { this.status = status; }
    public boolean isRulesLocked() { return rulesLocked; }
    public void setRulesLocked(boolean rulesLocked) { this.rulesLocked = rulesLocked; }
    public TieRule getTieRule() { return tieRule; }
    public void setTieRule(TieRule tieRule) { this.tieRule = tieRule; }
    public Integer getAwardQuota() { return awardQuota; }
    public void setAwardQuota(Integer awardQuota) { this.awardQuota = awardQuota; }
    public BigDecimal getAwardAmount() { return awardAmount; }
    public void setAwardAmount(BigDecimal awardAmount) { this.awardAmount = awardAmount; }
    public String getRulesSnapshot() { return rulesSnapshot; }
    public void setRulesSnapshot(String rulesSnapshot) { this.rulesSnapshot = rulesSnapshot; }
    public String getLockedBy() { return lockedBy; }
    public void setLockedBy(String lockedBy) { this.lockedBy = lockedBy; }
    public LocalDateTime getLockedAt() { return lockedAt; }
    public void setLockedAt(LocalDateTime lockedAt) { this.lockedAt = lockedAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public LocalDateTime getObjectionDeadline() { return objectionDeadline; }
    public void setObjectionDeadline(LocalDateTime objectionDeadline) { this.objectionDeadline = objectionDeadline; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
}
