package edu.scholar.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 学生提交的加分材料；审核人不得是提交人。 */
@Entity
public class Material extends IdentifiedEntity {
    @Column(nullable = false)
    public Long studentId;

    /** 对应规则分项 code */
    @Column(nullable = false)
    public String itemCode;

    @Column(nullable = false)
    public String title;

    /** 申报分值 */
    public double claimedPoints;

    /** 文字依据说明 */
    @Column(length = 2000)
    public String evidence;

    public String attachmentName;
    public String attachmentPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public MaterialStatus status = MaterialStatus.SUBMITTED;

    /** 提交人 userId（学生本人或代提交的辅导员） */
    public Long submittedBy;
    public Instant submittedAt;

    /** 审核人 userId：必须与 submittedBy 不同 */
    public Long reviewedBy;
    public Instant reviewedAt;

    /** 审核处理依据 */
    @Column(length = 2000)
    public String reviewComment;

    /** 审核认定的有效分值 */
    public Double approvedPoints;
}
