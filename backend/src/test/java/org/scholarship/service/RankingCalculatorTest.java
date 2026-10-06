package org.scholarship.service;

import org.junit.jupiter.api.Test;
import org.scholarship.domain.TieRule;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RankingCalculatorTest {

    private ScoreService.StudentBreakdown student(String id, double score) {
        ItemScore item = new ItemScore("ACADEMIC", "学业", BigDecimal.valueOf(score),
                BigDecimal.ZERO, BigDecimal.valueOf(score));
        return new ScoreService.StudentBreakdown(id, List.of(item));
    }

    private List<RankingCalculator.RankedRow> run(TieRule rule, int quota) {
        // S001=90, S002=90（同分）, S003=85
        return RankingCalculator.rank(
                List.of(student("S001", 90), student("S002", 90), student("S003", 85)),
                rule, quota);
    }

    @Test
    void competitionRanking_shareRankAndSkipNumber() {
        var rows = run(TieRule.COMPETITION, 2);
        assertEquals(1, rows.get(0).rank()); // S001（学号小）
        assertEquals(1, rows.get(1).rank()); // S002 并列第一
        assertEquals(3, rows.get(2).rank()); // 跳号：第三名
        assertTrue(rows.get(0).inAward());
        assertTrue(rows.get(1).inAward());
        assertFalse(rows.get(2).inAward());
    }

    @Test
    void denseRanking_shareRankNoSkip() {
        var rows = run(TieRule.DENSE, 2);
        assertEquals(1, rows.get(0).rank());
        assertEquals(1, rows.get(1).rank());
        assertEquals(2, rows.get(2).rank()); // 不跳号
        // 密集排名下第三名名次=2，仍在名额 2 内（与竞赛排名的关键区别：不跳号）
        assertTrue(rows.get(2).inAward());
    }

    @Test
    void denseRanking_thirdPlaceCutWhenQuotaOne() {
        var rows = RankingCalculator.rank(
                List.of(student("S001", 90), student("S002", 90), student("S003", 85)),
                TieRule.DENSE, 1);
        assertEquals(1, rows.get(0).rank());
        assertEquals(1, rows.get(1).rank());
        assertTrue(rows.get(0).inAward());
        assertTrue(rows.get(1).inAward()); // 并列第一，即便名额 1 也一同在名额内（并列名次判定）
        assertEquals(2, rows.get(2).rank());
        assertFalse(rows.get(2).inAward());
    }

    @Test
    void sequentialRanking_noTie_studentIdWins() {
        var rows = run(TieRule.SEQUENTIAL, 1);
        assertEquals(1, rows.get(0).rank());
        assertEquals(2, rows.get(1).rank()); // 同分不并列，学号决胜
        assertEquals(3, rows.get(2).rank());
        assertTrue(rows.get(0).inAward());
        assertFalse(rows.get(1).inAward());
    }
}
