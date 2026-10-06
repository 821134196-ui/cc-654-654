package edu.scholar.domain;

import jakarta.persistence.*;

/** 榜单版本中的单个学生行：名次、总分、各分项得分与依据、与上一版本的变化标记。 */
@Entity
public class RankingRow extends IdentifiedEntity {
    @Column(nullable = false)
    public Long versionId;

    @Column(nullable = false)
    public Long studentId;

    public String studentName;
    public String studentNo;

    @Column(nullable = false)
    public int rank;

    @Column(nullable = false)
    public double totalScore;

    /** [{code,name,category,score,maxPoints,basis}] 各分项得分及依据 */
    @Column(length = 8000, nullable = false)
    public String itemBreakdownJson;

    /** 上一版本名次/总分（首版为空） */
    public Integer prevRank;
    public Double prevScore;

    /** 与上一版本相比名次或总分是否变化 */
    @Column(nullable = false)
    public boolean changed;

    /** 是否在获奖名额内（并列规则下按锁定规则判定） */
    @Column(nullable = false)
    public boolean awardEligible;
}
