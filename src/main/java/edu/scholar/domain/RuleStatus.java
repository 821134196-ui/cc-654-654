package edu.scholar.domain;

/** 规则集状态：教务在公示前必须先 LOCKED，锁定后不可修改 */
public enum RuleStatus {
    DRAFT,
    LOCKED
}
