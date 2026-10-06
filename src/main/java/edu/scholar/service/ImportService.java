package edu.scholar.service;

import edu.scholar.domain.CourseScore;
import edu.scholar.domain.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * 本地模拟“成绩导入接口”：真实系统中由教务系统推送，这里以 JSON 请求体模拟。
 * 同一学期重复导入采用“先清后导”，便于演示重算。
 */
@ApplicationScoped
public class ImportService {

    @Inject
    AuditService audit;

    public static class ScoreIn {
        public String studentUsername;
        public String courseName;
        public double credit;
        public double score;
    }

    @Transactional
    public int importScores(User actor, String term, List<ScoreIn> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException("导入数据为空");
        }
        // 收集涉及学生 -> 先删其本学期旧数据
        java.util.Set<Long> studentIds = new java.util.HashSet<>();
        for (ScoreIn in : rows) {
            User u = User.findByUsername(in.studentUsername);
            if (u == null) throw new BusinessException("导入失败：学生账号不存在 " + in.studentUsername);
            studentIds.add(u.id);
        }
        for (Long sid : studentIds) {
            CourseScore.delete("studentId = ?1 and term = ?2", sid, term);
        }
        int n = 0;
        for (ScoreIn in : rows) {
            User u = User.findByUsername(in.studentUsername);
            CourseScore cs = new CourseScore();
            cs.studentId = u.id;
            cs.term = term;
            cs.courseName = in.courseName;
            cs.credit = in.credit;
            cs.score = in.score;
            cs.persist();
            n++;
        }
        audit.log(actor, "SCORE_IMPORT", "CourseScore", term,
                "本地模拟导入 " + n + " 条成绩记录（学期 " + term + "，覆盖学生 " + studentIds.size() + " 人）");
        return n;
    }
}
