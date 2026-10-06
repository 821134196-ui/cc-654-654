package edu.scholar.domain;

import jakarta.persistence.*;

/** 导入的课程成绩（本地模拟导入接口写入） */
@Entity
public class CourseScore extends IdentifiedEntity {
    @Column(nullable = false)
    public Long studentId;

    @Column(nullable = false)
    public String term;

    @Column(nullable = false)
    public String courseName;

    public double credit;

    /** 百分制成绩 */
    public double score;
}
