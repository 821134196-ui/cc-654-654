package org.scholarship.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 榜单版本：每次发布或重算都生成不可变快照（条目为 RankingEntry 副本）。
 * 旧版本永不覆盖，学生与教务可随时查阅；相邻版本的排名变化在条目上用 prevRank/changed 标出。
 */
@Entity
@Table(name = "ranking_versions")
public class RankingVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    /** 批次内自增的版本号，1 = 首次公示版本 */
    @Column(nullable = false)
    private int versionNo;

    /** INITIAL（公示首发）/ RECALC（异议重算）/ POST_CLOSE（封榜后更正） */
    @Column(nullable = false, length = 16)
    private String trigger;

    /** 触发本次重算的更正流水 id（首发可空） */
    @Column(name = "trigger_correction_id")
    private Long triggerCorrectionId;

    /** 关联异议 id（可空） */
    @Column(name = "appeal_id")
    private Long appealId;

    /** 本版本采用的并列规则（来自锁定快照） */
    @Enumerated(EnumType.STRING)
    @Column(name = "tie_rule", nullable = false, length = 16)
    private TieRule tieRule;

    @Column(nullable = false)
    private int awardQuota;

    /** 版本生成依据的文字说明 */
    @Lob
    private String basis;

    private String createdBy;
    private LocalDateTime createdAt;

    /** 该版本是否为封榜时的当前版本（用于发放）；封榜后更正不改变这个标记语义，发放以付款记录为准 */
    @Column(name = "sealed_flag", nullable = false)
    private boolean sealedFlag = false;

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    private List<RankingEntry> entries = new ArrayList<>();

    protected RankingVersion() {
    }

    public RankingVersion(Long cycleId, int versionNo, String trigger,
                          Long triggerCorrectionId, Long appealId,
                          TieRule tieRule, int awardQuota, String basis, String createdBy) {
        this.cycleId = cycleId;
        this.versionNo = versionNo;
        this.trigger = trigger;
        this.triggerCorrectionId = triggerCorrectionId;
        this.appealId = appealId;
        this.tieRule = tieRule;
        this.awardQuota = awardQuota;
        this.basis = basis;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getCycleId() { return cycleId; }
    public int getVersionNo() { return versionNo; }
    public String getTrigger() { return trigger; }
    public Long getTriggerCorrectionId() { return triggerCorrectionId; }
    public Long getAppealId() { return appealId; }
    public TieRule getTieRule() { return tieRule; }
    public int getAwardQuota() { return awardQuota; }
    public String getBasis() { return basis; }
    public String getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isSealedFlag() { return sealedFlag; }
    public void setSealedFlag(boolean sealedFlag) { this.sealedFlag = sealedFlag; }
    public List<RankingEntry> getEntries() { return entries; }
}
