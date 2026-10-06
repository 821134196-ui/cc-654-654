package edu.scholar.service;

import edu.scholar.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.*;

/**
 * 奖学金发放与封榜后差额处理。
 * 红线：{@link Disbursement} 一经生成不可修改/删除；
 * 更正后产生的差额只能通过 {@link DisbursementAdjustment}（补发/退回）处理，
 * 每条调整都写明依据并可与原记录对账。
 */
@ApplicationScoped
public class DisbursementService {

    @Inject
    AuditService audit;

    @Inject
    NotificationService notifications;

    @Inject
    RankingService rankings;

    /** 按封榜版本对获奖名额内的学生发放。 */
    @Transactional
    public List<Disbursement> pay(User actor, Long versionId, String note) {
        RankingVersion v = rankings.requireVersion(versionId);
        if (v.status != VersionStatus.FINALIZED) {
            throw new BusinessException("只能按已封榜版本发放奖学金");
        }
        List<Disbursement> created = new ArrayList<>();
        List<RankingRow> winners = rankings.rows(versionId).stream()
                .filter(r -> r.awardEligible)
                .sorted(Comparator.comparingInt(r -> r.rank))
                .toList();
        for (RankingRow row : winners) {
            long existing = Disbursement.count("studentId = ?1 and versionId = ?2", row.studentId, v.id);
            if (existing > 0) {
                continue; // 幂等：同一版本同一学生不重复发放
            }
            Disbursement d = new Disbursement();
            d.versionId = v.id;
            d.studentId = row.studentId;
            d.amount = v.awardAmount;
            d.rankAtPayment = row.rank;
            d.paidAt = Instant.now();
            d.createdBy = actor.name;
            d.note = note;
            d.persist();
            created.add(d);
            audit.log(actor, "DISBURSE_PAY", "Disbursement", String.valueOf(d.id),
                    "按封榜 v" + v.versionNo + " 向 " + row.studentName + "（名次 " + row.rank
                            + "）发放 " + v.awardAmount + " 分；原发放记录不可修改"
                            + (note == null ? "" : "；备注：" + note));
            notifications.notify(row.studentId, "DISBURSEMENT_PAID", "奖学金已发放",
                    "按封榜 v" + v.versionNo + "（名次 " + row.rank + "）发放奖学金 "
                            + yuan(v.awardAmount) + " 元。");
        }
        return created;
    }

    /**
     * 根据更正版本与原发放记录自动生成补发/退回调整（幂等：同一更正版本不重复处理）。
     * 更正后仍获奖且金额不变 -> 无差额；
     * 更正后新获奖 -> 补发全额；原获奖现落榜 -> 退回全额；
     * 奖金标准若有差异（本系统锁定后金额一致，预留按应得差额计算）。
     */
    @Transactional
    public List<DisbursementAdjustment> reconcile(User actor, Long correctionVersionId, String reason) {
        RankingVersion corrected = rankings.requireVersion(correctionVersionId);
        if (corrected.trigger != VersionTrigger.CORRECTION || corrected.status != VersionStatus.FINALIZED) {
            throw new BusinessException("只能对封榜后的更正版本做补发/退回处理");
        }
        long already = DisbursementAdjustment.count("correctionVersionId", correctionVersionId);
        if (already > 0) {
            throw new BusinessException("该更正版本已生成过补发/退回记录，差额处理记录不可重复生成或修改");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("补发/退回必须填写处理依据");
        }

        // 找到本更正链路上最近一次“曾发放”的封榜版本（通常为被更正的上一 FINALIZED 版本）
        RankingVersion paidVersion = findPreviousPaidVersion(corrected);

        Map<Long, Disbursement> paidByStudent = new HashMap<>();
        if (paidVersion != null) {
            List<Disbursement> prevPaid = Disbursement.find("versionId", paidVersion.id).list();
            for (Disbursement d : prevPaid) {
                paidByStudent.put(d.studentId, d);
            }
        }
        Map<Long, RankingRow> newWinners = new HashMap<>();
        for (RankingRow r : rankings.rows(corrected.id)) {
            if (r.awardEligible) newWinners.put(r.studentId, r);
        }

        List<DisbursementAdjustment> result = new ArrayList<>();
        Set<Long> allStudents = new HashSet<>();
        allStudents.addAll(paidByStudent.keySet());
        allStudents.addAll(newWinners.keySet());

        for (Long sid : allStudents) {
            Disbursement original = paidByStudent.get(sid);
            RankingRow now = newWinners.get(sid);
            long paidAmount = original == null ? 0 : original.amount;
            long dueAmount = now == null ? 0 : corrected.awardAmount;
            long diff = dueAmount - paidAmount;
            if (diff == 0) {
                continue; // 仍获奖且金额一致，无差额
            }
            DisbursementAdjustment adj = new DisbursementAdjustment();
            adj.studentId = sid;
            adj.correctionVersionId = corrected.id;
            adj.disbursementId = original == null ? null : original.id;
            adj.kind = diff > 0 ? AdjustmentKind.MAKEUP : AdjustmentKind.REFUND;
            adj.amount = Math.abs(diff);
            adj.reason = reason;
            adj.createdAt = Instant.now();
            adj.createdBy = actor.name;
            adj.persist();
            result.add(adj);

            User stu = User.findById(sid);
            audit.log(actor, "DISBURSEMENT_ADJUST", "DisbursementAdjustment", String.valueOf(adj.id),
                    (adj.kind == AdjustmentKind.MAKEUP ? "补发" : "退回") + " " + (stu == null ? sid : stu.name)
                            + " 差额 " + adj.amount + " 分（原发放 " + paidAmount + "，更正后应得 " + dueAmount
                            + "）；原发放记录" + (original == null ? "为空（更正后新上榜）" : "#" + original.id + " 保持不变")
                            + "；依据更正版本 v" + corrected.versionNo + "；处理依据：" + reason);
            notifications.notify(sid, "ADJUSTMENT",
                    adj.kind == AdjustmentKind.MAKEUP ? "奖学金补发通知" : "奖学金退回通知",
                    (adj.kind == AdjustmentKind.MAKEUP ? "因榜单更正，需向您补发 " : "因榜单更正，需退回 ")
                            + yuan(adj.amount) + " 元。更正版本 v" + corrected.versionNo + "；依据：" + reason);
        }
        if (result.isEmpty()) {
            audit.log(actor, "DISBURSEMENT_ADJUST_NOOP", "RankingVersion", String.valueOf(corrected.id),
                    "更正版本 v" + corrected.versionNo + " 对账后无差额，无需补发/退回；依据：" + reason);
        }
        return result;
    }

    /** 在版本链上向父版本回溯，找最近存在发放记录的版本。 */
    private RankingVersion findPreviousPaidVersion(RankingVersion v) {
        Long pid = v.parentVersionId;
        while (pid != null) {
            RankingVersion p = rankings.requireVersion(pid);
            if (Disbursement.count("versionId", p.id) > 0) {
                return p;
            }
            pid = p.parentVersionId;
        }
        return null;
    }

    public List<Disbursement> listDisbursements(Long studentId) {
        return studentId == null
                ? Disbursement.find("order by id").list()
                : Disbursement.find("studentId = ?1 order by id", studentId).list();
    }

    public List<DisbursementAdjustment> listAdjustments(Long studentId) {
        return studentId == null
                ? DisbursementAdjustment.find("order by id").list()
                : DisbursementAdjustment.find("studentId = ?1 order by id", studentId).list();
    }

    private String yuan(long cents) {
        return String.format("%.2f", cents / 100.0);
    }
}
