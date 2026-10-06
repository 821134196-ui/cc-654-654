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
 * 奖学金发放及发放后的差额处理。
 * 不可变原则：Payment 一经生成没有任何更新/删除入口；
 * 发现错误只能追加 PaymentAdjustment（补发 SUPPLEMENT / 退回 REFUND），
 * 每条调整必须写明处理依据；净额 = 原发放 + 补发合计 - 退回合计。
 */
@ApplicationScoped
public class PaymentService {

    @Inject
    EntityManager em;
    @Inject
    CycleService cycleService;
    @Inject
    NotificationService notifications;

    /** 按封榜版本生成整批发放记录（仅获奖线内学生），整批只能执行一次。 */
    @Transactional
    public String createBatch(Long cycleId, String operator) {
        ScholarshipCycle c = cycleService.mustGet(cycleId);
        if (c.getStatus() != CycleStatus.CLOSED) {
            throw new BusinessException(409, "只有已封榜批次才能发放");
        }
        Long existing = em.createQuery("select count(p) from Payment p where p.cycleId = :c", Long.class)
                .setParameter("c", cycleId).getSingleResult();
        if (existing > 0) {
            throw new BusinessException(409, "该批次已生成发放记录，原记录不可修改；差额请走补发/退回");
        }
        RankingVersion sealed = cycleService.sealedVersion(cycleId);
        String batchNo = "PAY-c" + cycleId + "-v" + sealed.getVersionNo()
                + "-" + LocalDateTime.now().getNano();
        for (RankingEntry e : sealed.getEntries()) {
            if (e.isInAward()) {
                em.persist(new Payment(cycleId, e.getStudentId(), sealed.getId(),
                        c.getAwardAmount(), operator, batchNo));
                notifications.push(e.getStudentId(), "奖学金已发放",
                        "批次 " + batchNo + "，金额 " + c.getAwardAmount().stripTrailingZeros().toPlainString()
                                + " 元，依据封榜版本 v" + sealed.getVersionNo(), "PAYMENT");
            }
        }
        return batchNo;
    }

    /**
     * 追加差额调整。
     * SUPPLEMENT 补发：paymentId 可空（封榜后更正中新进获奖线、原本无发放记录的学生）；
     * REFUND 退回：必须针对原发放记录，且累计退回不得超过 已发放+已补发，防止超额退回。
     */
    @Transactional
    public PaymentAdjustment adjust(Long cycleId, String studentId, String type,
                                    BigDecimal amount, Long paymentId, Long correctionId,
                                    String reason, String operator) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(400, "调整金额必须大于 0（方向由类型表达）");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(400, "必须填写处理依据");
        }
        Payment payment = null;
        if (paymentId != null) {
            payment = em.find(Payment.class, paymentId);
            if (payment == null || !payment.getCycleId().equals(cycleId)
                    || !payment.getStudentId().equals(studentId)) {
                throw new BusinessException(404, "原发放记录不存在或与批次/学生不匹配");
            }
        }
        if ("REFUND".equals(type)) {
            if (payment == null) {
                throw new BusinessException(400, "退回必须关联原发放记录");
            }
            BigDecimal paid = payment.getAmount();
            BigDecimal supplemented = sumAdjustments(cycleId, studentId, "SUPPLEMENT");
            BigDecimal refunded = sumAdjustments(cycleId, studentId, "REFUND");
            BigDecimal maxRefund = paid.add(supplemented).subtract(refunded);
            if (amount.compareTo(maxRefund) > 0) {
                throw new BusinessException(409,
                        "退回金额超出可退净额（最多可退 " + maxRefund.stripTrailingZeros().toPlainString() + " 元）");
            }
        } else if (!"SUPPLEMENT".equals(type)) {
            throw new BusinessException(400, "调整类型只能是 SUPPLEMENT 或 REFUND");
        }

        PaymentAdjustment adj = new PaymentAdjustment(
                paymentId, cycleId, studentId, type, amount, correctionId, operator, reason);
        em.persist(adj);
        notifications.push(studentId,
                "SUPPLEMENT".equals(type) ? "奖学金补发登记" : "奖学金退回登记",
                ( "SUPPLEMENT".equals(type) ? "补发 " : "退回 ") + amount.stripTrailingZeros().toPlainString()
                        + " 元。依据：" + reason + "。原发放记录保持不变。", "PAYMENT");
        return adj;
    }

    private BigDecimal sumAdjustments(Long cycleId, String studentId, String type) {
        List<BigDecimal> l = em.createQuery(
                        "select a.amount from PaymentAdjustment a where a.cycleId=:c " +
                                "and a.studentId=:s and a.type=:t", BigDecimal.class)
                .setParameter("c", cycleId).setParameter("s", studentId)
                .setParameter("t", type).getResultList();
        return l.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<Payment> listPayments(Long cycleId) {
        return em.createQuery("select p from Payment p where p.cycleId=:c order by p.studentId",
                        Payment.class).setParameter("c", cycleId).getResultList();
    }

    public List<PaymentAdjustment> listAdjustments(Long cycleId) {
        return em.createQuery("select a from PaymentAdjustment a where a.cycleId=:c " +
                        "order by a.createdAt", PaymentAdjustment.class)
                .setParameter("c", cycleId).getResultList();
    }

    /** 计算某学生在批次内的净额（原发放+补发-退回）。 */
    public BigDecimal netAmount(Long cycleId, String studentId) {
        BigDecimal paid = em.createQuery("select coalesce(sum(p.amount),0) from Payment p " +
                        "where p.cycleId=:c and p.studentId=:s", BigDecimal.class)
                .setParameter("c", cycleId).setParameter("s", studentId).getSingleResult();
        BigDecimal sup = sumAdjustments(cycleId, studentId, "SUPPLEMENT");
        BigDecimal ref = sumAdjustments(cycleId, studentId, "REFUND");
        return paid.add(sup).subtract(ref);
    }
}
