package org.scholarship.web;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.scholarship.domain.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 实体 → 对外视图的统一映射，避免懒加载序列化问题并附带学生姓名等冗余信息。 */
@ApplicationScoped
public class ViewMapper {

    @Inject
    EntityManager em;

    private final Map<String, String> nameCache = new HashMap<>();

    public String nameOf(String studentId) {
        return nameCache.computeIfAbsent(studentId, id -> {
            User u = em.find(User.class, id);
            return u != null ? u.getName() : id;
        });
    }

    public Map<String, Object> cycle(ScholarshipCycle c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("status", c.getStatus());
        m.put("rulesLocked", c.isRulesLocked());
        m.put("tieRule", c.getTieRule());
        m.put("awardQuota", c.getAwardQuota());
        m.put("awardAmount", c.getAwardAmount());
        m.put("rulesSnapshot", c.getRulesSnapshot());
        m.put("lockedBy", c.getLockedBy());
        m.put("lockedAt", c.getLockedAt());
        m.put("publishedAt", c.getPublishedAt());
        m.put("objectionDeadline", c.getObjectionDeadline());
        m.put("closedAt", c.getClosedAt());
        return m;
    }

    public Map<String, Object> entry(RankingEntry e) {
        Map<String, Object> m = new HashMap<>();
        m.put("studentId", e.getStudentId());
        m.put("studentName", nameOf(e.getStudentId()));
        m.put("rank", e.getRank());
        m.put("totalScore", e.getTotalScore());
        m.put("inAward", e.isInAward());
        m.put("prevRank", e.getPrevRank());
        m.put("change", e.getChange());
        return m;
    }

    public Map<String, Object> version(RankingVersion v) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", v.getId());
        m.put("versionNo", v.getVersionNo());
        m.put("trigger", v.getTrigger());
        m.put("correctionId", v.getTriggerCorrectionId());
        m.put("appealId", v.getAppealId());
        m.put("tieRule", v.getTieRule());
        m.put("awardQuota", v.getAwardQuota());
        m.put("basis", v.getBasis());
        m.put("createdBy", v.getCreatedBy());
        m.put("createdAt", v.getCreatedAt());
        m.put("sealedFlag", v.isSealedFlag());
        m.put("entries", v.getEntries().stream().map(this::entry).toList());
        return m;
    }

    public Map<String, Object> material(BonusMaterial x) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", x.getId());
        m.put("cycleId", x.getCycleId());
        m.put("studentId", x.getStudentId());
        m.put("studentName", nameOf(x.getStudentId()));
        m.put("itemKey", x.getItemKey());
        m.put("itemName", x.getItemName());
        m.put("title", x.getTitle());
        m.put("claimedPoints", x.getClaimedPoints());
        m.put("awardedPoints", x.getAwardedPoints());
        m.put("status", x.getStatus());
        m.put("submittedBy", x.getSubmittedBy());
        m.put("submittedByName", x.getSubmittedBy() == null ? null : nameOf(x.getSubmittedBy()));
        m.put("submittedAt", x.getSubmittedAt());
        m.put("reviewedBy", x.getReviewedBy());
        m.put("reviewedByName", x.getReviewedBy() == null ? null : nameOf(x.getReviewedBy()));
        m.put("reviewedAt", x.getReviewedAt());
        m.put("reviewComment", x.getReviewComment());
        m.put("attachmentName", x.getAttachmentName());
        m.put("hasAttachment", x.getAttachmentPath() != null);
        return m;
    }

    public Map<String, Object> appeal(Appeal a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("cycleId", a.getCycleId());
        m.put("studentId", a.getStudentId());
        m.put("studentName", nameOf(a.getStudentId()));
        m.put("itemKey", a.getItemKey());
        m.put("itemName", a.getItemName());
        m.put("reason", a.getReason());
        m.put("status", a.getStatus());
        m.put("submittedAt", a.getSubmittedAt());
        m.put("decidedBy", a.getDecidedBy());
        m.put("decidedByName", a.getDecidedBy() == null ? null : nameOf(a.getDecidedBy()));
        m.put("decidedAt", a.getDecidedAt());
        m.put("decisionComment", a.getDecisionComment());
        m.put("correctedScore", a.getCorrectedScore());
        m.put("resultingVersion", a.getResultingVersion());
        return m;
    }

    public Map<String, Object> correction(ScoreCorrection c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("studentId", c.getStudentId());
        m.put("studentName", nameOf(c.getStudentId()));
        m.put("itemKey", c.getItemKey());
        m.put("itemName", c.getItemName());
        m.put("oldScore", c.getOldScore());
        m.put("newScore", c.getNewScore());
        m.put("source", c.getSource());
        m.put("appealId", c.getAppealId());
        m.put("operator", c.getOperator());
        m.put("operatorName", nameOf(c.getOperator()));
        m.put("createdAt", c.getCreatedAt());
        m.put("reason", c.getReason());
        return m;
    }

    public Map<String, Object> payment(Payment p, BigDecimal net) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("cycleId", p.getCycleId());
        m.put("studentId", p.getStudentId());
        m.put("studentName", nameOf(p.getStudentId()));
        m.put("rankingVersionId", p.getRankingVersionId());
        m.put("amount", p.getAmount());
        m.put("status", p.getStatus());
        m.put("batchNo", p.getBatchNo());
        m.put("paidAt", p.getPaidAt());
        m.put("createdBy", p.getCreatedBy());
        m.put("netAmount", net);
        return m;
    }

    public Map<String, Object> adjustment(PaymentAdjustment a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("paymentId", a.getPaymentId());
        m.put("cycleId", a.getCycleId());
        m.put("studentId", a.getStudentId());
        m.put("studentName", nameOf(a.getStudentId()));
        m.put("type", a.getType());
        m.put("amount", a.getAmount());
        m.put("correctionId", a.getCorrectionId());
        m.put("createdBy", a.getCreatedBy());
        m.put("createdByName", nameOf(a.getCreatedBy()));
        m.put("createdAt", a.getCreatedAt());
        m.put("reason", a.getReason());
        return m;
    }

    public Map<String, Object> notification(Notification n) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", n.getId());
        m.put("title", n.getTitle());
        m.put("content", n.getContent());
        m.put("category", n.getCategory());
        m.put("read", n.isReadFlag());
        m.put("createdAt", n.getCreatedAt());
        return m;
    }

    public record ItemBonusView(String materialTitle, String status,
                                java.math.BigDecimal awardedPoints, String reviewer, String comment) {
    }

    public Map<String, Object> scoreItem(String studentId, org.scholarship.service.ItemScore item,
                                         List<ItemBonusView> bonuses, String evidence) {
        Map<String, Object> m = new HashMap<>();
        m.put("itemKey", item.itemKey());
        m.put("itemName", item.itemName());
        m.put("baseScore", item.baseScore());
        m.put("bonusScore", item.bonusScore());
        m.put("total", item.total());
        m.put("evidence", evidence);
        m.put("bonuses", bonuses);
        return m;
    }

    public Map<String, Object> user(User u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("name", u.getName());
        m.put("role", u.getRole());
        m.put("classRef", u.getClassRef());
        return m;
    }

    // LocalDateTime 在 Quarkus Jackson 下默认可能无法序列化，注册为 ISO 字符串视图兜底
    public static String iso(LocalDateTime t) {
        return t == null ? null : t.toString();
    }
}
