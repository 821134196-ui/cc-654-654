package edu.scholar.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 公示期内学生针对具体分项提出的异议 */
@Entity
public class Appeal extends IdentifiedEntity {
    @Column(nullable = false)
    public Long studentId;

    /** 针对的公示版本 */
    @Column(nullable = false)
    public Long versionId;

    /** 异议成立后生成的新版本 */
    public Long newVersionId;

    /** 具体分项 code；为空表示对总分/排名有异议 */
    public String itemCode;

    @Column(nullable = false, length = 2000)
    public String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public AppealStatus status = AppealStatus.OPEN;

    public Instant createdAt;

    public Long handledBy;
    public Instant handledAt;

    /** 处理结论与依据（成立/驳回均需写明） */
    @Column(length = 2000)
    public String handleComment;
}
