package edu.scholar.service;

import edu.scholar.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;

/** 材料审批的实际写库逻辑，独立 Bean 以便由非事务入口在通过校验后调用。 */
@ApplicationScoped
public class MaterialReviewExecutor {

    @Inject
    AuditService audit;

    @Inject
    NotificationService notifications;

    @Transactional
    public Material applyReview(User reviewer, Long materialId, boolean approved,
                                Double approvedPoints, String comment) {
        Material m = Material.findById(materialId);
        if (m == null || m.status != MaterialStatus.SUBMITTED) {
            throw new BusinessException("材料状态已变化，请刷新后重试: " + materialId);
        }
        m.status = approved ? MaterialStatus.APPROVED : MaterialStatus.REJECTED;
        m.reviewedBy = reviewer.id;
        m.reviewedAt = Instant.now();
        m.reviewComment = comment;
        if (approved) {
            double pts = approvedPoints == null ? m.claimedPoints : approvedPoints;
            m.approvedPoints = pts;
        }
        m.persist();

        notifications.notify(m.studentId, "MATERIAL_REVIEWED",
                "材料审核结果：" + (approved ? "通过" : "驳回"),
                "《" + m.title + "》" + (approved ? "已通过，认定 " + m.approvedPoints + " 分" : "未通过")
                        + "。审核人：" + reviewer.name + "；依据：" + comment);
        audit.log(reviewer, "MATERIAL_REVIEW", "Material", String.valueOf(m.id),
                (approved ? "通过" : "驳回") + "材料《" + m.title + "》"
                        + (approved ? "，认定 " + m.approvedPoints + " 分" : "")
                        + "；处理依据：" + comment
                        + "；提交人=" + nameOf(m.submittedBy) + "，审核人=" + reviewer.name);
        return m;
    }

    private String nameOf(Long userId) {
        if (userId == null) return "未知";
        User u = User.findById(userId);
        return u == null ? "未知" : u.name;
    }
}
