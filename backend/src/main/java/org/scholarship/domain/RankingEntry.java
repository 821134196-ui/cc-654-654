package org.scholarship.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * 榜单版本中的单个学生条目（快照）。
 * 重算时整条重建，不修改旧版本；prevRank 记录本学生在上一版本的名次，changed 标记变化方向，
 * 供前端标出“上升/下降/新进/掉出获奖线”。
 */
@Entity
@Table(name = "ranking_entries")
public class RankingEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id")
    private RankingVersion version;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    /** 按锁定的 TieRule 计算出的名次（可能并列） */
    @Column(nullable = false)
    private int rank;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalScore;

    /** 是否在获奖名额内（并列落线时按规则截断后的结果） */
    @Column(name = "in_award", nullable = false)
    private boolean inAward;

    /** 上一版本名次；首个版本为 null */
    @Column(name = "prev_rank")
    private Integer prevRank;

    /** SAME / UP / DOWN / NEW / OUT_AWARD（掉出获奖线但仍在榜单） */
    @Column(length = 16)
    private String change;

    protected RankingEntry() {
    }

    public RankingEntry(RankingVersion version, String studentId, int rank,
                        BigDecimal totalScore, boolean inAward, Integer prevRank, String change) {
        this.version = version;
        this.studentId = studentId;
        this.rank = rank;
        this.totalScore = totalScore;
        this.inAward = inAward;
        this.prevRank = prevRank;
        this.change = change;
    }

    public Long getId() { return id; }
    public RankingVersion getVersion() { return version; }
    public String getStudentId() { return studentId; }
    public int getRank() { return rank; }
    public BigDecimal getTotalScore() { return totalScore; }
    public boolean isInAward() { return inAward; }
    public Integer getPrevRank() { return prevRank; }
    public String getChange() { return change; }
}
