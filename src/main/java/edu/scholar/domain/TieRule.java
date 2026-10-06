package edu.scholar.domain;

/** 并列名次处理规则（随排名规则一并锁定） */
public enum TieRule {
    /** 标准竞赛排名：1,2,2,4（并列占用名次） */
    COMPETITION,
    /** 密集排名：1,2,2,3（并列不占名次） */
    DENSE
}
