package edu.scholar.service;

import edu.scholar.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;

/**
 * 加分材料提交与审核。核心红线：审核人不能审批自己提交的材料。
 * {@link #review} 本身不开事务，先在事务外完成校验与自审拦截
 * （拦截审计用独立事务落库），通过后才由 {@link MaterialReviewExecutor} 开事务写库。
 */
@ApplicationScoped
public class MaterialService {

    @Inject
    AuditService audit;

    @Inject
    MaterialReviewExecutor reviewExecutor;

    public List<Material> listPending() {
        return Material.find("status = ?1 order by id", MaterialStatus.SUBMITTED).list();
    }

    public List<Material> listForStudent(Long studentId) {
        return Material.find("studentId = ?1 order by id desc", studentId).list();
    }

    @Transactional
    public Material submit(User actor, Long studentId, String itemCode, String title,
                           double claimedPoints, String evidence,
                           String attachmentName, String attachmentPath) {
        User student = User.findById(studentId);
        if (student == null || student.role != Role.STUDENT) {
            throw BusinessException.notFound("学生不存在: " + studentId);
        }
        Material m = new Material();
        m.studentId = studentId;
        m.itemCode = itemCode;
        m.title = title;
        m.claimedPoints = claimedPoints;
        m.evidence = evidence;
        m.attachmentName = attachmentName;
        m.attachmentPath = attachmentPath;
        m.status = MaterialStatus.SUBMITTED;
        m.submittedBy = actor.id;
        m.submittedAt = Instant.now();
        m.persist();
        audit.log(actor, "MATERIAL_SUBMIT", "Material", String.valueOf(m.id),
                "学生 " + student.name + " 提交材料《" + title + "》申报 " + claimedPoints + " 分，分项 " + itemCode
                        + (evidence == null ? "" : "；说明：" + evidence));
        return m;
    }

    /**
     * 非事务入口：先校验，自审则用独立事务留痕并 403，否则进入审批事务。
     */
    public Material review(User reviewer, Long materialId, boolean approved,
                           Double approvedPoints, String comment) {
        // 事务外读取（只读字段做判定）
        Material m = Material.findById(materialId);
        if (m == null) {
            throw BusinessException.notFound("材料不存在: " + materialId);
        }
        if (m.status != MaterialStatus.SUBMITTED) {
            throw new BusinessException("该材料已审核，不能重复审批");
        }
        if (comment == null || comment.isBlank()) {
            throw new BusinessException("审核必须填写处理依据");
        }
        if (approved && approvedPoints != null && approvedPoints < 0) {
            throw new BusinessException("认定分值不能为负");
        }

        // —— 自审拦截红线：当前无外层事务，独立事务写入拦截依据，再拒绝 ——
        if (m.submittedBy != null && m.submittedBy.equals(reviewer.id)) {
            audit.logIndependent(reviewer, "MATERIAL_SELF_REVIEW_BLOCKED", "Material", String.valueOf(m.id),
                    "拦截自审：审核人 " + reviewer.name + " 就是材料《" + m.title + "》的提交人");
            throw BusinessException.forbidden("不能审批自己提交的材料（提交人=" + reviewer.name
                    + "），请由其他辅导员审核");
        }

        // 通过校验：进入真正的审批事务（在事务内重新加载实体）
        return reviewExecutor.applyReview(reviewer, materialId, approved, approvedPoints, comment);
    }
}
