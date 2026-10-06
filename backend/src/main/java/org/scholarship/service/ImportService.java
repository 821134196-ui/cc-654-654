package org.scholarship.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.scholarship.domain.BaseScore;
import org.scholarship.domain.ScholarshipCycle;

import java.math.BigDecimal;
import java.util.List;

/**
 * 本地模拟的成绩导入接口（真实环境对接教务系统，这里接收 JSON 批量写入）。
 * 规则锁定前允许重复导入（覆盖旧值）；锁定后导入被拒绝——改分必须走更正/异议流程并留痕。
 */
@ApplicationScoped
public class ImportService {

    @Inject
    EntityManager em;

    public record ImportRow(String studentId, String itemKey, String itemName,
                            BigDecimal score, String evidence) {
    }

    @Transactional
    public int importScores(Long cycleId, List<ImportRow> rows, String operator) {
        ScholarshipCycle c = em.find(ScholarshipCycle.class, cycleId);
        if (c == null) {
            throw new BusinessException(404, "批次不存在");
        }
        if (c.isRulesLocked()) {
            throw new BusinessException(409,
                    "排名规则已锁定，不能再整批导入；如需勘误请使用更正功能（保留更正依据）");
        }
        int n = 0;
        for (ImportRow r : rows) {
            if (r.studentId() == null || r.itemKey() == null || r.score() == null) {
                throw new BusinessException(400, "导入行缺少 studentId/itemKey/score");
            }
            List<BaseScore> existing = em.createQuery(
                            "select b from BaseScore b where b.cycleId=:c and b.studentId=:s and b.itemKey=:k",
                            BaseScore.class)
                    .setParameter("c", cycleId).setParameter("s", r.studentId())
                    .setParameter("k", r.itemKey()).getResultList();
            String evidence = (r.evidence() != null ? r.evidence() : "本地模拟导入")
                    + "（导入人 " + operator + "）";
            if (existing.isEmpty()) {
                em.persist(new BaseScore(cycleId, r.studentId(), r.itemKey(),
                        r.itemName() != null ? r.itemName() : r.itemKey(), r.score(), evidence));
            } else {
                BaseScore b = existing.get(0);
                b.setScore(r.score());
                b.setEvidence(evidence);
            }
            n++;
        }
        return n;
    }
}
