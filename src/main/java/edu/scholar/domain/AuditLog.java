package edu.scholar.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 通用审计日志：材料审核、公示重算、封榜后更正、补退发放均保留处理依据。 */
@Entity
public class AuditLog extends IdentifiedEntity {
    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    public Long actorUserId;
    public String actorName;

    /** MATERIAL_REVIEW / RULE_LOCK / RANKING_PUBLISH / APPEAL_HANDLE / RANKING_FINALIZE / RANKING_CORRECT / DISBURSE_PAY / DISBURSEMENT_ADJUST 等 */
    @Column(nullable = false)
    public String action;

    public String targetType;
    public String targetId;

    /** 处理依据与动作详情 */
    @Column(length = 4000)
    public String detail;
}
