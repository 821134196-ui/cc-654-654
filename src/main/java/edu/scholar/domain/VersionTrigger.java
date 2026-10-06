package edu.scholar.domain;

/** 榜单版本产生原因 */
public enum VersionTrigger {
    /** 首次公示 */
    INITIAL,
    /** 公示期内异议成立后重算 */
    APPEAL,
    /** 封榜/发放后发现错误而更正 */
    CORRECTION
}
