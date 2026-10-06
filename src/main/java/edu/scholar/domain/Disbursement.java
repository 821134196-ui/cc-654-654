package edu.scholar.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 奖学金发放记录。一经生成不可修改、不可删除；差额只能通过补发/退回处理。 */
@Entity
public class Disbursement extends IdentifiedEntity {
    @Column(nullable = false)
    public Long versionId;

    @Column(nullable = false)
    public Long studentId;

    /** 发放金额，分 */
    @Column(nullable = false)
    public long amount;

    /** 发放时锁定的榜单名次 */
    @Column(nullable = false)
    public int rankAtPayment;

    public Instant paidAt;
    public String createdBy;

    @Column(length = 2000)
    public String note;
}
