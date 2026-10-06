package edu.scholar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.scholar.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.*;

/**
 * 依据已锁定规则计算各学生分项得分：
 * GPA 项取导入成绩的学分加权平均（封顶 maxPoints）；
 * 加分项取“已通过”材料的认定分值之和（封顶 maxPoints）。
 * 每个分项都生成文字依据，供学生页面与榜单版本留存。
 */
@ApplicationScoped
public class ScoringService {

    @Inject
    ObjectMapper mapper;

    public static class ItemScore {
        public String code, name, category;
        public double score, maxPoints;
        public String basis;
    }

    public static class StudentScore {
        public Long studentId;
        public String studentName;
        public String studentNo;
        public double total;
        public List<ItemScore> items = new ArrayList<>();
    }

    public List<StudentScore> computeAll(String term, List<RuleItem> ruleItems) {
        List<User> students = User.find("role = ?1 order by id", Role.STUDENT).list();
        List<StudentScore> result = new ArrayList<>();
        for (User s : students) {
            result.add(computeOne(s, term, ruleItems));
        }
        return result;
    }

    public StudentScore computeOne(User student, String term, List<RuleItem> ruleItems) {
        StudentScore ss = new StudentScore();
        ss.studentId = student.id;
        ss.studentName = student.name;
        ss.studentNo = student.studentNo;

        List<CourseScore> scores = CourseScore.find("studentId = ?1 and term = ?2", student.id, term).list();
        List<Material> approved = Material.find(
                "studentId = ?1 and status = ?2", student.id, MaterialStatus.APPROVED).list();

        for (RuleItem ri : ruleItems.stream().sorted(Comparator.comparingInt(r -> r.sortOrder)).toList()) {
            ItemScore is = new ItemScore();
            is.code = ri.code;
            is.name = ri.name;
            is.category = ri.category;
            is.maxPoints = ri.maxPoints;
            if (RuleItem.CATEGORY_GPA.equals(ri.category)) {
                fillGpa(is, scores);
            } else {
                fillMaterial(is, ri, approved);
            }
            is.score = round2(Math.min(is.score, ri.maxPoints));
            ss.items.add(is);
            ss.total += is.score;
        }
        ss.total = round2(ss.total);
        return ss;
    }

    private void fillGpa(ItemScore is, List<CourseScore> scores) {
        if (scores.isEmpty()) {
            is.score = 0;
            is.basis = "无导入成绩，按 0 分计";
            return;
        }
        double creditSum = scores.stream().mapToDouble(s -> s.credit).sum();
        double weighted = creditSum == 0 ? 0
                : scores.stream().mapToDouble(s -> s.score * s.credit).sum() / creditSum;
        is.score = round2(weighted);
        is.basis = String.format("学分加权平均 %.2f（%d 门课程，学分合计 %.1f）；来源：教务导入成绩单",
                weighted, scores.size(), creditSum);
    }

    private void fillMaterial(ItemScore is, RuleItem ri, List<Material> approved) {
        List<Material> matched = approved.stream()
                .filter(m -> ri.code.equals(m.itemCode))
                .toList();
        double raw = matched.stream().mapToDouble(m -> m.approvedPoints == null ? 0 : m.approvedPoints).sum();
        StringBuilder sb = new StringBuilder();
        if (matched.isEmpty()) {
            sb.append("无已通过材料，按 0 分计");
        } else {
            sb.append(String.format("已批材料认定分值合计 %.2f（上限 %.2f）；", round2(raw), ri.maxPoints));
            for (Material m : matched) {
                User reviewer = m.reviewedBy == null ? null : User.findById(m.reviewedBy);
                sb.append("\n· 《").append(m.title).append("》认定 ")
                  .append(m.approvedPoints).append(" 分");
                if (reviewer != null) {
                    sb.append("，审核人：").append(reviewer.name);
                }
                if (m.reviewComment != null && !m.reviewComment.isBlank()) {
                    sb.append("，依据：").append(m.reviewComment);
                }
            }
        }
        is.score = round2(raw);
        is.basis = sb.toString();
    }

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
