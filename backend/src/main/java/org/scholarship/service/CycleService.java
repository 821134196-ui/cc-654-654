package org.scholarship.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.scholarship.domain.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 批次与榜单版本核心流程：
 * 规则锁定 → 公示（生成 v1 快照）→ 异议重算（追加新版本，旧版保留，标出变化）→ 封榜 →（封榜后更正仍可追加版本）。
 */
@ApplicationScoped
public class CycleService {

    @Inject
    EntityManager em;
    @Inject
    ScoreService scoreService;
    @Inject
    NotificationService notificationService;

    @Transactional
    public ScholarshipCycle createCycle(String name) {
        ScholarshipCycle c = new ScholarshipCycle(name);
        em.persist(c);
        return c;
    }

    @Transactional
    public ScholarshipCycle lockRules(Long cycleId, TieRule tieRule, Integer quota,
                                      java.math.BigDecimal awardAmount,
                                      String rulesSnapshot, String operator) {
        ScholarshipCycle c = mustGet(cycleId);
        if (c.isRulesLocked()) {
            throw new BusinessException(409, "排名规则已锁定，不能修改（锁定规则：" + c.getTieRule() + "）");
        }
        if (tieRule == null || quota == null || quota <= 0 || awardAmount == null) {
            throw new BusinessException(400, "并列规则、名额、奖金额为必填项");
        }
        c.setTieRule(tieRule);
        c.setAwardQuota(quota);
        c.setAwardAmount(awardAmount);
        c.setRulesSnapshot(rulesSnapshot);
        c.setRulesLocked(true);
        c.setLockedBy(operator);
        c.setLockedAt(LocalDateTime.now());
        return c;
    }

    /** 公示：规则必须已锁定；生成首个榜单快照版本。 */
    @Transactional
    public RankingVersion publish(Long cycleId, LocalDateTime objectionDeadline, String operator) {
        ScholarshipCycle c = mustGet(cycleId);
        if (!c.isRulesLocked()) {
            throw new BusinessException(409, "请先锁定排名规则再公示");
        }
        if (c.getStatus() != CycleStatus.DRAFT) {
            throw new BusinessException(409, "批次已公示，不能重复公示");
        }
        c.setStatus(CycleStatus.PUBLISHED);
        c.setPublishedAt(LocalDateTime.now());
        c.setObjectionDeadline(objectionDeadline);
        RankingVersion v = buildVersion(c, "INITIAL", null, null,
                "首次公示，按已锁定规则（" + c.getTieRule() + "，名额 " + c.getAwardQuota()
                        + "）计算", operator);
        notifyStudents(cycleId, "奖学金评定结果已公示",
                "公示期内可在系统中查看各分项得分并提交异议。截止：" + objectionDeadline, "INFO");
        return v;
    }

    /**
     * 重算：写新版本（不覆盖旧版），标记与上一版相比的名次变化。
     * trigger: RECALC（公示异议）/ POST_CLOSE（封榜后更正）
     */
    @Transactional
    public RankingVersion recalculate(ScholarshipCycle c, String trigger, Long correctionId,
                                      Long appealId, String basis, String operator) {
        return buildVersion(c, trigger, correctionId, appealId, basis, operator);
    }

    private RankingVersion buildVersion(ScholarshipCycle c, String trigger, Long correctionId,
                                        Long appealId, String basis, String operator) {
        int nextNo = ((Number) em.createQuery(
                        "select count(v) from RankingVersion v where v.cycleId = :c")
                .setParameter("c", c.getId()).getSingleResult()).intValue() + 1;

        RankingVersion version = new RankingVersion(c.getId(), nextNo, trigger, correctionId, appealId,
                c.getTieRule(), c.getAwardQuota(), basis, operator);

        List<RankingCalculator.RankedRow> rows = RankingCalculator.rank(
                scoreService.compute(c.getId()), c.getTieRule(), c.getAwardQuota());

        // 取上一版本名次做变化对比
        Map<String, Integer> prevRankByStudent = new HashMap<>();
        Map<String, Boolean> prevInAward = new HashMap<>();
        if (nextNo > 1) {
            RankingVersion prev = (RankingVersion) em.createQuery(
                            "select v from RankingVersion v where v.cycleId = :c order by v.versionNo desc")
                    .setParameter("c", c.getId()).setMaxResults(1).getSingleResult();
            for (RankingEntry e : prev.getEntries()) {
                prevRankByStudent.put(e.getStudentId(), e.getRank());
                prevInAward.put(e.getStudentId(), e.isInAward());
            }
        }

        for (RankingCalculator.RankedRow r : rows) {
            Integer prevRank = prevRankByStudent.get(r.studentId());
            String change;
            if (prevRank == null) {
                change = "NEW";
            } else if (prevRank == r.rank()) {
                change = "SAME";
            } else {
                change = r.rank() < prevRank ? "UP" : "DOWN";
            }
            // 掉出/进入获奖线优先标记（即使名次数字未变，边界并列截断时也可能发生）
            if (prevRank != null && prevInAward.getOrDefault(r.studentId(), false) && !r.inAward()) {
                change = "OUT_AWARD";
            }
            version.getEntries().add(new RankingEntry(version, r.studentId(), r.rank(),
                    r.total(), r.inAward(), prevRank, change));
        }
        em.persist(version);
        em.flush();
        return version;
    }

    /** 封榜：当前最新版本成为封榜版本（sealedFlag）。封榜后仍允许更正并追加版本。 */
    @Transactional
    public RankingVersion closeCycle(Long cycleId, String operator) {
        ScholarshipCycle c = mustGet(cycleId);
        if (c.getStatus() != CycleStatus.PUBLISHED) {
            throw new BusinessException(409, "只有公示中的批次可以封榜");
        }
        RankingVersion latest = latestVersion(cycleId);
        latest.setSealedFlag(true);
        c.setStatus(CycleStatus.CLOSED);
        c.setClosedAt(LocalDateTime.now());
        notifyStudents(cycleId, "奖学金评定已封榜",
                "封榜版本：v" + latest.getVersionNo() + "。如封榜后发现错误，将通过更正与补发/退回处理。", "INFO");
        return latest;
    }

    public ScholarshipCycle mustGet(Long cycleId) {
        ScholarshipCycle c = em.find(ScholarshipCycle.class, cycleId);
        if (c == null) {
            throw new BusinessException(404, "批次不存在：" + cycleId);
        }
        return c;
    }

    public RankingVersion latestVersion(Long cycleId) {
        List<RankingVersion> vs = em.createQuery(
                        "select v from RankingVersion v where v.cycleId = :c order by v.versionNo desc",
                        RankingVersion.class)
                .setParameter("c", cycleId).setMaxResults(1).getResultList();
        if (vs.isEmpty()) {
            throw new BusinessException(409, "尚无榜单版本");
        }
        return vs.get(0);
    }

    /** 返回封榜时打标的版本（发放依据）。 */
    public RankingVersion sealedVersion(Long cycleId) {
        List<RankingVersion> vs = em.createQuery(
                        "select v from RankingVersion v where v.cycleId = :c and v.sealedFlag = true",
                        RankingVersion.class)
                .setParameter("c", cycleId).setMaxResults(1).getResultList();
        if (vs.isEmpty()) {
            throw new BusinessException(409, "批次尚未封榜");
        }
        return vs.get(0);
    }

    private void notifyStudents(Long cycleId, String title, String content, String category) {
        List<String> ids = em.createQuery(
                        "select distinct b.studentId from BaseScore b where b.cycleId = :c", String.class)
                .setParameter("c", cycleId).getResultList();
        for (String id : ids) {
            notificationService.push(id, title, content, category);
        }
    }
}
