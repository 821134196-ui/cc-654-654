package org.scholarship.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.scholarship.domain.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 公示/封榜后的改分入口（非异议渠道）：
 * 导入勘误 IMPORT_FIX 与封榜后更正 POST_CLOSE。两者都必须留 ScoreCorrection 依据并触发新版本榜单；
 * 封榜后更正若影响奖金差额，由教务另行登记补发/退回（PaymentAdjustment），原发放记录不动。
 */
@ApplicationScoped
public class CorrectionService {

    @Inject
    EntityManager em;
    @Inject
    CycleService cycleService;

    @Transactional
    public RankingVersion correct(Long cycleId, String studentId, String itemKey,
                                  BigDecimal newScore, String source, String reason, String operator) {
        if (newScore == null || newScore.signum() < 0) {
            throw new BusinessException(400, "更正后分值不能小于 0");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(400, "必须填写处理依据");
        }
        ScholarshipCycle c = cycleService.mustGet(cycleId);
        if ("POST_CLOSE".equals(source) && c.getStatus() != CycleStatus.CLOSED) {
            throw new BusinessException(409, "封榜后更正只能在 CLOSED 状态执行");
        }
        if ("IMPORT_FIX".equals(source) && c.getStatus() == CycleStatus.DRAFT) {
            throw new BusinessException(409, "公示前可直接重新导入覆盖，无需走更正");
        }

        List<BaseScore> l = em.createQuery("select b from BaseScore b where b.cycleId=:c " +
                        "and b.studentId=:s and b.itemKey=:k", BaseScore.class)
                .setParameter("c", cycleId).setParameter("s", studentId)
                .setParameter("k", itemKey).getResultList();
        BaseScore bs = l.isEmpty() ? null : l.get(0);
        BigDecimal old = bs != null ? bs.getScore() : BigDecimal.ZERO;
        String itemName;
        if (bs == null) {
            itemName = itemKey;
            bs = new BaseScore(cycleId, studentId, itemKey, itemName, newScore, null);
            em.persist(bs);
        } else {
            itemName = bs.getItemName();
            bs.setScore(newScore);
        }
        bs.setEvidence("[" + source + "] " + reason + "（操作人 " + operator + "）；原依据："
                + (bs.getEvidence() != null ? bs.getEvidence() : "—"));

        ScoreCorrection corr = new ScoreCorrection(cycleId, studentId, itemKey, itemName,
                old, newScore, source, null, operator, reason);
        em.persist(corr);
        em.flush();

        String trigger = c.getStatus() == CycleStatus.CLOSED ? "POST_CLOSE" : "RECALC";
        String basis = ("POST_CLOSE".equals(source) ? "封榜后更正" : "导入勘误")
                + "：学生 " + studentId + " 分项 " + itemName + " "
                + old.stripTrailingZeros().toPlainString() + " → "
                + newScore.stripTrailingZeros().toPlainString() + "；依据：" + reason
                + "。按已锁定并列规则 " + c.getTieRule() + " 重算，旧版本保留。";
        return cycleService.recalculate(c, trigger, corr.getId(), null, basis, operator);
    }

    public List<ScoreCorrection> listCorrections(Long cycleId) {
        return em.createQuery("select x from ScoreCorrection x where x.cycleId=:c order by x.createdAt",
                        ScoreCorrection.class).setParameter("c", cycleId).getResultList();
    }
}
