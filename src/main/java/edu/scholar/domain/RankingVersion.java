package edu.scholar.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 榜单版本。每次公示、异议重算、封榜后更正都生成不可变的新版本，
 * 旧版本永久保留可查；规则快照与处理依据随版本保存。
 */
@Entity
public class RankingVersion extends IdentifiedEntity {
    @Column(nullable = false)
    public String term;

    @Column(nullable = false)
    public int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public VersionStatus status = VersionStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public VersionTrigger trigger = VersionTrigger.INITIAL;

    public Long parentVersionId;

    /** 触发本版本的申诉（trigger=APPEAL 时） */
    public Long appealId;

    /** 锁定规则的快照 JSON（含分项、并列规则、名额、奖金额），作为排名依据 */
    @Column(length = 8000, nullable = false)
    public String ruleSnapshotJson;

    /** 版本产生的处理依据（公示备注 / 申诉成立结论 / 更正原因） */
    @Column(length = 2000)
    public String reason;

    /** 生成时锁定的并列规则 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TieRule tieRule;

    @Column(nullable = false)
    public int scholarshipCount;

    /** 每人奖金额，分 */
    @Column(nullable = false)
    public long awardAmount;

    public Instant createdAt;
    public Instant publishedAt;
    /** 公示截止时间（publishedAt + appealWindowDays） */
    public Instant appealDeadline;
    public Instant finalizedAt;

    public String createdBy;

    public static RankingVersion findLatest(String term) {
        return find("term = ?1 order by versionNo desc", term).firstResult();
    }
}
