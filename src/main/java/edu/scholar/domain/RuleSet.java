package edu.scholar.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 排名规则集。教务在公示前必须锁定；锁定后不可修改，
 * 每个榜单版本都会保存规则快照，作为排名变化的处理依据。
 */
@Entity
public class RuleSet extends IdentifiedEntity {
    /** 学期，如 2026-2027-1 */
    @Column(nullable = false, unique = true)
    public String term;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public RuleStatus status = RuleStatus.DRAFT;

    /** 并列名次处理规则 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TieRule tieRule = TieRule.COMPETITION;

    /** 获奖名额 */
    @Column(nullable = false)
    public int scholarshipCount = 3;

    /** 每人奖学金额（分，避免浮点误差） */
    @Column(nullable = false)
    public long awardAmount = 5000_00;

    /** 公示期天数 */
    @Column(nullable = false)
    public int appealWindowDays = 7;

    public Instant lockedAt;
    public String lockedBy;

    public static RuleSet current() {
        return find("order by id desc").firstResult();
    }
}
