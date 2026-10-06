package org.scholarship.domain;

/** 并列名次处理规则，公示前由教务锁定，之后重算一律按已锁定规则执行。 */
public enum TieRule {
    /** 竞赛排名：同分并列，后续跳号（1,1,3） */
    COMPETITION,
    /** 密集排名：同分并列，后续不跳号（1,1,2） */
    DENSE,
    /** 顺序排名：不并列，同分按学号决胜（1,2,3） */
    SEQUENTIAL
}
