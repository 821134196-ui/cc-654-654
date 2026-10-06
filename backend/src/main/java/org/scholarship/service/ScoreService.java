package org.scholarship.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.scholarship.domain.BaseScore;
import org.scholarship.domain.BonusMaterial;

import java.math.BigDecimal;
import java.util.*;

/**
 * 汇总学生各分项得分：基础分（导入/更正后）+ 已审核通过材料的核定加分之和。
 * 数据口径对所有版本一致；改分通过 ScoreCorrection 落到 BaseScore 后，重算自然得到新结果。
 */
@ApplicationScoped
public class ScoreService {

    @Inject
    EntityManager em;

    public static class StudentBreakdown {
        public final String studentId;
        public final List<ItemScore> items;
        public final BigDecimal total;

        public StudentBreakdown(String studentId, List<ItemScore> items) {
            this.studentId = studentId;
            this.items = items;
            this.total = items.stream().map(ItemScore::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    public List<StudentBreakdown> compute(Long cycleId) {
        List<BaseScore> bases = em.createQuery(
                        "select b from BaseScore b where b.cycleId = :c", BaseScore.class)
                .setParameter("c", cycleId).getResultList();
        List<BonusMaterial> materials = em.createQuery(
                        "select m from BonusMaterial m where m.cycleId = :c and m.status = 'APPROVED'",
                        BonusMaterial.class)
                .setParameter("c", cycleId).getResultList();

        // student -> itemKey -> 可变累加器 [itemName, base, bonus]
        Map<String, Map<String, Object[]>> acc = new TreeMap<>();

        for (BaseScore b : bases) {
            Object[] slot = acc.computeIfAbsent(b.getStudentId(), k -> new TreeMap<>())
                    .computeIfAbsent(b.getItemKey(),
                            k -> new Object[]{b.getItemName(), BigDecimal.ZERO, BigDecimal.ZERO});
            slot[0] = b.getItemName();
            slot[1] = ((BigDecimal) slot[1]).add(b.getScore());
        }
        for (BonusMaterial m : materials) {
            Object[] slot = acc.computeIfAbsent(m.getStudentId(), k -> new TreeMap<>())
                    .computeIfAbsent(m.getItemKey(),
                            k -> new Object[]{m.getItemName(), BigDecimal.ZERO, BigDecimal.ZERO});
            if (((String) slot[0]) == null || ((String) slot[0]).isBlank()) {
                slot[0] = m.getItemName();
            }
            BigDecimal awarded = m.getAwardedPoints() != null ? m.getAwardedPoints() : BigDecimal.ZERO;
            slot[2] = ((BigDecimal) slot[2]).add(awarded);
        }

        List<StudentBreakdown> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object[]>> e : acc.entrySet()) {
            List<ItemScore> items = new ArrayList<>();
            for (Map.Entry<String, Object[]> ie : e.getValue().entrySet()) {
                Object[] v = ie.getValue();
                BigDecimal base = (BigDecimal) v[1];
                BigDecimal bonus = (BigDecimal) v[2];
                items.add(new ItemScore(ie.getKey(), (String) v[0], base, bonus, base.add(bonus)));
            }
            result.add(new StudentBreakdown(e.getKey(), items));
        }
        return result;
    }
}
