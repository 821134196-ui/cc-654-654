package edu.scholar.domain;

import jakarta.persistence.*;

/** 规则分项：GPA 或加分项及其封顶分值，随规则集一并锁定。 */
@Entity
public class RuleItem extends IdentifiedEntity {
    @Column(nullable = false)
    public Long ruleSetId;

    /** 分项代码，如 GPA / COMP_SCI / COMP_SPORT */
    @Column(nullable = false)
    public String code;

    @Column(nullable = false)
    public String name;

    /** GPA=绩点成绩项，MATERIAL=凭材料审核的加分项 */
    @Column(nullable = false)
    public String category;

    /** 该项封顶分值（百分制下的分值上限） */
    @Column(nullable = false)
    public double maxPoints;

    public int sortOrder;

    public static final String CATEGORY_GPA = "GPA";
    public static final String CATEGORY_MATERIAL = "MATERIAL";
}
