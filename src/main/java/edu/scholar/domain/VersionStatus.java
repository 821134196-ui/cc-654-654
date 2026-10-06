package edu.scholar.domain;

/**
 * 榜单版本状态：
 * DRAFT 草稿；PUBLISHED 公示中；SUPERSEDED 被异议重算后的新版取代（永久保留）；
 * FINALIZED 封榜终版。更正版本直接以 FINALIZED 状态生成，旧封榜版本同样保留。
 */
public enum VersionStatus {
    DRAFT,
    PUBLISHED,
    SUPERSEDED,
    FINALIZED
}
