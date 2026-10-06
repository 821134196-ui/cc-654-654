package org.scholarship.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.scholarship.domain.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 公示异议与重算。
 * 学生仅能在批次 PUBLISHED（公示期内）对具体分项提交异议；
 * 教务裁定成立时：写 ScoreCorrection 留痕 → 更新基础分（保留变更说明）→ 按锁定规则重算新版本，
 * 旧版本完整保留，新版本标注名次变化。批次已封榜时裁定成立，触发的是 POST_CLOSE 版本。
 */
@ApplicationScoped
public class AppealService {

    @Inject
    EntityManager em;
    @Inject
    CycleService cycleService;
    @Inject
    NotificationService notifications;

    @Transactional
    public Appeal submit(Long cycleId, String itemKey, String reason, User student) {
        ScholarshipCycle c = cycleService.mustGet(cycleId);
        if (c.getStatus() != CycleStatus.PUBLISHED) {
            throw new BusinessException(409, "只有公示中的批次可以提交异议（当前状态：" + c.getStatus() + "）");
        }
        if (c.getObjectionDeadline() != null && LocalDateTime.now().isAfter(c.getObjectionDeadline())) {
            throw new BusinessException(409, "公示异议期已截止（" + c.getObjectionDeadline() + "）");
        }
        // 找到该生该分项，用于展示名称；允许对任意已有分项提出异议
        BaseScore bs = findBase(cycleId, student.getId(), itemKey);
        BonusMaterial m = findAnyMaterial(cycleId, student.getId(), itemKey);
        String itemName = bs != null ? bs.getItemName()
                : (m != null ? m.getItemName() : itemKey);

        Appeal a = new Appeal(cycleId, student.getId(), itemKey, itemName, reason, student.getId());
        em.persist(a);
        notifications.push(student.getId(), "异议已提交",
                "分项【" + itemName + "】的异议已登记，等待教务裁定。", "INFO");
        return a;
    }

    @Transactional
    public Appeal decide(Long appealId, boolean upheld, BigDecimal correctedScore,
                         String comment, User admin) {
        Appeal a = em.find(Appeal.class, appealId);
        if (a == null) {
            throw new BusinessException(404, "异议不存在");
        }
        if (!"PENDING".equals(a.getStatus())) {
            throw new BusinessException(409, "该异议已裁定，不能重复操作");
        }
        ScholarshipCycle c = cycleService.mustGet(a.getCycleId());
        a.setDecidedBy(admin.getId());
        a.setDecidedAt(LocalDateTime.now());
        a.setDecisionComment(comment);

        if (!upheld) {
            a.setStatus("REJECTED");
            notifications.push(a.getStudentId(), "异议已驳回：" + a.getItemName(),
                    "裁定人：" + admin.getName() + (comment != null ? "。依据：" + comment : ""),
                    "APPEAL_DECISION");
            return a;
        }
        if (correctedScore == null || correctedScore.signum() < 0) {
            throw new BusinessException(400, "裁定成立必须给出不小于 0 的更正后分值");
        }

        // 1) 更正基础分并写更正流水（排名变化的依据）
        BaseScore bs = findBase(a.getCycleId(), a.getStudentId(), a.getItemKey());
        BigDecimal oldScore = bs != null ? bs.getScore() : BigDecimal.ZERO;
        if (bs == null) {
            bs = new BaseScore(a.getCycleId(), a.getStudentId(), a.getItemKey(),
                    a.getItemName(), correctedScore, null);
            em.persist(bs);
        } else {
            bs.setScore(correctedScore);
        }
        String basisNote = "异议 #" + appealId + " 成立，按教务 " + admin.getName()
                + " 裁定更正；原依据：" + (bs.getEvidence() != null ? bs.getEvidence() : "—");
        bs.setEvidence(basisNote + "。裁定依据：" + comment);

        String trigger = c.getStatus() == CycleStatus.CLOSED ? "POST_CLOSE" : "RECALC";
        ScoreCorrection corr = new ScoreCorrection(a.getCycleId(), a.getStudentId(),
                a.getItemKey(), a.getItemName(), oldScore, correctedScore,
                trigger.equals("POST_CLOSE") ? "POST_CLOSE" : "APPEAL",
                appealId, admin.getId(),
                "异议 #" + appealId + " 成立：" + comment);
        em.persist(corr);
        em.flush();

        // 2) 按已锁定规则重算新版本（旧版保留，变化标在条目上）
        String versionBasis = "异议 #" + appealId + "（学生 " + a.getStudentId() + " 对分项 "
                + a.getItemName() + "）成立，" + oldScore.stripTrailingZeros().toPlainString()
                + " → " + correctedScore.stripTrailingZeros().toPlainString()
                + "；依据已锁定并列规则 " + c.getTieRule() + " 重算。裁定依据：" + comment;
        RankingVersion v = cycleService.recalculate(c, trigger, corr.getId(), appealId,
                versionBasis, admin.getId());

        a.setStatus("UPHELD");
        a.setCorrectedScore(correctedScore);
        a.setResultingVersion(v.getVersionNo());

        notifications.push(a.getStudentId(), "异议成立，排名已重新计算：" + a.getItemName(),
                "更正后分值 " + correctedScore.stripTrailingZeros().toPlainString()
                        + "，已生成榜单 v" + v.getVersionNo() + "，旧榜单仍可查阅。",
                "APPEAL_DECISION");
        return a;
    }

    public List<Appeal> listByCycle(Long cycleId) {
        return em.createQuery("select a from Appeal a where a.cycleId = :c order by a.submittedAt",
                        Appeal.class).setParameter("c", cycleId).getResultList();
    }

    public List<Appeal> listByStudent(Long cycleId, String studentId) {
        return em.createQuery("select a from Appeal a where a.cycleId = :c and a.studentId = :s " +
                        "order by a.submittedAt desc", Appeal.class)
                .setParameter("c", cycleId).setParameter("s", studentId).getResultList();
    }

    private BaseScore findBase(Long cycleId, String studentId, String itemKey) {
        List<BaseScore> l = em.createQuery("select b from BaseScore b where b.cycleId=:c " +
                        "and b.studentId=:s and b.itemKey=:k", BaseScore.class)
                .setParameter("c", cycleId).setParameter("s", studentId)
                .setParameter("k", itemKey).getResultList();
        return l.isEmpty() ? null : l.get(0);
    }

    private BonusMaterial findAnyMaterial(Long cycleId, String studentId, String itemKey) {
        List<BonusMaterial> l = em.createQuery("select m from BonusMaterial m where m.cycleId=:c " +
                        "and m.studentId=:s and m.itemKey=:k", BonusMaterial.class)
                .setParameter("c", cycleId).setParameter("s", studentId)
                .setParameter("k", itemKey).setMaxResults(1).getResultList();
        return l.isEmpty() ? null : l.get(0);
    }
}
