package org.scholarship.service;

import java.math.BigDecimal;

/** 单个学生单个分项的得分拆解：基础分 + 已通过材料加分。 */
public record ItemScore(String itemKey, String itemName,
                        BigDecimal baseScore, BigDecimal bonusScore, BigDecimal total) {

    public ItemScore addBonus(java.util.List<BigDecimal> bonuses) {
        BigDecimal b = bonuses.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ItemScore(itemKey, itemName, baseScore, b, baseScore.add(b));
    }
}
