package edu.scholar.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 本地模拟通知（不接外部渠道，仅入库，前端轮询/查询） */
@Entity
public class Notification extends IdentifiedEntity {
    @Column(nullable = false)
    public Long userId;

    /** MATERIAL_REVIEWED / APPEAL_UPHELD / RANKING_PUBLISHED / ADJUSTMENT 等 */
    @Column(nullable = false)
    public String type;

    @Column(nullable = false)
    public String title;

    @Column(length = 2000)
    public String content;

    @Column(nullable = false)
    public boolean readFlag = false;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();
}
