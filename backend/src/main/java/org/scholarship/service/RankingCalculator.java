package org.scholarship.service;

import org.scholarship.domain.TieRule;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 并列名次计算（无状态纯逻辑）。严格使用批次锁定时的 {@link TieRule}：
 * <ul>
 *   <li>COMPETITION 竞赛排名：同分并列，后续跳号（1,1,3）</li>
 *   <li>DENSE 密集排名：同分并列，后续不跳号（1,1,2）</li>
 *   <li>SEQUENTIAL 顺序排名：不并列，同分按学号升序决胜（1,2,3）</li>
 * </ul>
 */
public final class RankingCalculator {

    public record RankedRow(String studentId, BigDecimal total, int rank, boolean inAward) {
    }

    private RankingCalculator() {
    }

    public static List<RankedRow> rank(List<ScoreService.StudentBreakdown> students,
                                       TieRule tieRule, int quota) {
        // 先按总分降序；总分相同再按学号升序，保证顺序稳定
        List<ScoreService.StudentBreakdown> ordered = new ArrayList<>(students);
        ordered.sort(Comparator.comparing((ScoreService.StudentBreakdown b) -> b.total)
                .reversed()
                .thenComparing(b -> b.studentId));

        List<RankedRow> rows = new ArrayList<>();
        int competitionPos = 0;   // 含并列者的累计人数（竞赛排名用）
        int denseLevel = 0;       // 已出现的不同分数档数（密集排名用）
        BigDecimal prevTotal = null;

        for (ScoreService.StudentBreakdown s : ordered) {
            competitionPos++;
            boolean sameAsPrev = prevTotal != null && s.total.compareTo(prevTotal) == 0;
            int rank;
            if (tieRule == TieRule.SEQUENTIAL) {
                rank = competitionPos;           // 学号决胜，绝不并列
            } else if (tieRule == TieRule.DENSE) {
                if (!sameAsPrev) {
                    denseLevel++;
                }
                rank = denseLevel;
            } else { // COMPETITION
                rank = sameAsPrev ? rows.get(rows.size() - 1).rank() : competitionPos;
            }
            rows.add(new RankedRow(s.studentId, s.total, rank, rank <= quota));
            prevTotal = s.total;
        }
        return rows;
    }
}
