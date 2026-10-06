package org.scholarship.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.scholarship.domain.BonusMaterial;
import org.scholarship.domain.ScholarshipCycle;
import org.scholarship.domain.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 加分材料提交与审核。
 * 关键约束：审核人不能审批自己提交的材料（reviewer.submittedBy 相同一律 403），
 * 与角色无关——即使辅导员代学生提交，该辅导员本人也不能审批这份材料。
 */
@ApplicationScoped
public class MaterialService {

    @Inject
    EntityManager em;
    @Inject
    NotificationService notifications;

    @Transactional
    public BonusMaterial submit(Long cycleId, String studentId, String itemKey, String itemName,
                                String title, BigDecimal claimedPoints,
                                String attachmentPath, String attachmentName, User submitter) {
        ScholarshipCycle c = em.find(ScholarshipCycle.class, cycleId);
        if (c == null) {
            throw new BusinessException(404, "批次不存在");
        }
        BonusMaterial m = new BonusMaterial(cycleId, studentId, itemKey, itemName,
                title, claimedPoints, submitter.getId());
        m.setAttachmentPath(attachmentPath);
        m.setAttachmentName(attachmentName);
        em.persist(m);
        return m;
    }

    @Transactional
    public BonusMaterial review(Long materialId, User reviewer, boolean approved,
                                BigDecimal awardedPoints, String comment) {
        BonusMaterial m = em.find(BonusMaterial.class, materialId);
        if (m == null) {
            throw new BusinessException(404, "材料不存在");
        }
        // ===== 自审拦截：审核人不得是材料提交人 =====
        if (reviewer.getId().equals(m.getSubmittedBy())) {
            throw new BusinessException(403,
                    "不能审批自己提交的材料（提交人：" + reviewer.getName()
                            + "）。请由其他辅导员/教务审核。");
        }
        if (!"PENDING".equals(m.getStatus())) {
            throw new BusinessException(409, "该材料已审核，不能重复审核");
        }
        if (approved) {
            if (awardedPoints == null || awardedPoints.signum() < 0) {
                throw new BusinessException(400, "通过时需填写不小于 0 的核定加分");
            }
            m.setStatus("APPROVED");
            m.setAwardedPoints(awardedPoints);
        } else {
            m.setStatus("REJECTED");
            m.setAwardedPoints(null);
        }
        m.setReviewedBy(reviewer.getId());
        m.setReviewedAt(LocalDateTime.now());
        m.setReviewComment(comment);

        notifications.push(m.getStudentId(),
                approved ? "加分材料已通过：" + m.getTitle() : "加分材料未通过：" + m.getTitle(),
                "审核人：" + reviewer.getName() + "。核定加分："
                        + (approved ? awardedPoints.stripTrailingZeros().toPlainString() : "—")
                        + (comment != null && !comment.isBlank() ? "。意见：" + comment : ""),
                "REVIEW");
        return m;
    }

    public List<BonusMaterial> listByCycle(Long cycleId) {
        return em.createQuery("select m from BonusMaterial m where m.cycleId = :c " +
                        "order by m.submittedAt desc", BonusMaterial.class)
                .setParameter("c", cycleId).getResultList();
    }

    public List<BonusMaterial> listByStudent(Long cycleId, String studentId) {
        return em.createQuery("select m from BonusMaterial m where m.cycleId = :c " +
                        "and m.studentId = :s order by m.submittedAt desc", BonusMaterial.class)
                .setParameter("c", cycleId).setParameter("s", studentId).getResultList();
    }

    public BonusMaterial get(Long id) {
        BonusMaterial m = em.find(BonusMaterial.class, id);
        if (m == null) {
            throw new BusinessException(404, "材料不存在");
        }
        return m;
    }
}
