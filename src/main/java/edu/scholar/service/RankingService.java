package edu.scholar.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.scholar.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 榜单版本引擎：首次公示、异议重算、封榜后更正都生成不可变新版本并完整留痕。
 * 名次与并列按已锁定规则计算；每一行保存分项得分依据和相对上一版的变化标记。
 */
@ApplicationScoped
public class RankingService {

    @Inject
    RuleService ruleService;

    @Inject
    ScoringService scoring;

    @Inject
    AuditService audit;

    @Inject
    NotificationService notifications;

    @Inject
    ObjectMapper mapper;

    /* ---------------- 查询 ---------------- */

    public List<RankingVersion> listVersions(String term) {
        return RankingVersion.find("term = ?1 order by versionNo", term).list();
    }

    public RankingVersion requireVersion(Long id) {
        RankingVersion v = RankingVersion.findById(id);
        if (v == null) throw BusinessException.notFound("榜单版本不存在: " + id);
        return v;
    }

    public List<RankingRow> rows(Long versionId) {
        return RankingRow.find("versionId = ?1 order by rank, id", versionId).list();
    }

    /* ---------------- 首次公示 ---------------- */

    @Transactional
    public RankingVersion publish(User actor, String term, String reason) {
        RuleSet rs = ruleService.requireLocked(term);
        RankingVersion latest = RankingVersion.findLatest(term);
        if (latest != null && latest.status == VersionStatus.PUBLISHED) {
            throw new BusinessException("当前已有公示中的版本（v" + latest.versionNo + "），请先处理完异议或封榜");
        }
        if (latest != null && latest.status == VersionStatus.FINALIZED) {
            throw new BusinessException("榜单已封榜，发现错误请使用“封榜后更正”，不能重新公示");
        }

        List<ScoringService.StudentScore> scores = scoring.computeAll(term, ruleService.items(rs.id));
        RankingVersion v = createVersion(actor, rs, scores, VersionStatus.PUBLISHED,
                VersionTrigger.INITIAL, latest, null, reason);
        v.publishedAt = Instant.now();
        v.appealDeadline = v.publishedAt.plus(rs.appealWindowDays, ChronoUnit.DAYS);

        audit.log(actor, "RANKING_PUBLISH", "RankingVersion", String.valueOf(v.id),
                "首次公示 v" + v.versionNo + "，并列规则=" + v.tieRule + "，名额=" + v.scholarshipCount
                        + "，公示截止=" + v.appealDeadline + "；依据：" + nz(reason, "按已锁定规则公示"));
        notifyStudents(term, "RANKING_PUBLISHED", "奖学金榜单已公示（v" + v.versionNo + "）",
                "公示期至 " + v.appealDeadline + "，可在“我的得分”页查看分项依据并提交异议。");
        return v;
    }

    /* ---------------- 异议处理与重算 ---------------- */

    @Transactional
    public Appeal openAppeal(User student, Long versionId, String itemCode, String reason) {
        RankingVersion v = requireVersion(versionId);
        if (v.status != VersionStatus.PUBLISHED) {
            throw new BusinessException("只能对公示中的榜单提交异议");
        }
        if (Instant.now().isAfter(v.appealDeadline)) {
            throw new BusinessException("公示期已于 " + v.appealDeadline + " 截止，不能再提交异议");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("异议必须写明理由与依据");
        }
        Appeal a = new Appeal();
        a.studentId = student.id;
        a.versionId = versionId;
        a.itemCode = itemCode;
        a.reason = reason;
        a.status = AppealStatus.OPEN;
        a.createdAt = Instant.now();
        a.persist();
        audit.log(student, "APPEAL_OPEN", "Appeal", String.valueOf(a.id),
                "学生 " + student.name + " 对 v" + v.versionNo
                        + (itemCode == null ? " 总分/排名" : " 分项 " + itemCode) + " 提出异议：" + reason);
        return a;
    }

    @Transactional
    public RankingVersion handleAppeal(User actor, Long appealId, boolean uphold, String comment) {
        Appeal a = Appeal.findById(appealId);
        if (a == null) throw BusinessException.notFound("异议不存在: " + appealId);
        if (a.status != AppealStatus.OPEN) {
            throw new BusinessException("该异议已处理，不能重复处理");
        }
        if (comment == null || comment.isBlank()) {
            throw new BusinessException("处理异议必须写明结论与依据");
        }
        RankingVersion old = requireVersion(a.versionId);
        a.handledBy = actor.id;
        a.handledAt = Instant.now();
        a.handleComment = comment;

        if (!uphold) {
            a.status = AppealStatus.REJECTED;
            audit.log(actor, "APPEAL_REJECT", "Appeal", String.valueOf(a.id),
                    "驳回 " + nameOf(a.studentId) + " 的异议；依据：" + comment);
            notifications.notify(a.studentId, "APPEAL_REJECTED", "申诉结果：异议驳回",
                    "您对 v" + old.versionNo + " 的异议未成立。依据：" + comment);
            return null;
        }

        // 异议成立：按当前最新材料/成绩数据重算，生成新版本，旧版本保留
        RuleSet rs = ruleService.requireLocked(old.term);
        List<ScoringService.StudentScore> scores = scoring.computeAll(old.term, ruleService.items(rs.id));
        RankingVersion v = createVersion(actor, rs, scores, VersionStatus.PUBLISHED,
                VersionTrigger.APPEAL, old, a.id,
                "异议成立（申诉人=" + nameOf(a.studentId)
                        + (a.itemCode == null ? "" : "，分项=" + a.itemCode) + "）：" + comment);
        v.publishedAt = Instant.now();
        v.appealDeadline = v.publishedAt.plus(rs.appealWindowDays, ChronoUnit.DAYS);

        old.status = VersionStatus.SUPERSEDED;
        a.status = AppealStatus.UPHELD;
        a.newVersionId = v.id;

        audit.log(actor, "APPEAL_UPHELD", "Appeal", String.valueOf(a.id),
                "异议成立并重算，旧版 v" + old.versionNo + " 保留可查，新版 v" + v.versionNo
                        + " 已公示，公示截止=" + v.appealDeadline + "；处理依据：" + comment);
        notifyStudents(old.term, "RANKING_PUBLISHED", "异议成立，榜单已更新（v" + v.versionNo + "）",
                "公示期内一项异议成立，排名已按已锁定规则重新计算，变化处已标出。公示期至 " + v.appealDeadline + "。");
        return v;
    }

    /* ---------------- 封榜 ---------------- */

    @Transactional
    public RankingVersion finalize(User actor, Long versionId, String reason) {
        RankingVersion v = requireVersion(versionId);
        if (v.status != VersionStatus.PUBLISHED) {
            throw new BusinessException("只有公示中的版本可以封榜（当前状态=" + v.status + "）");
        }
        long openAppeals = Appeal.count("versionId = ?1 and status = ?2", v.id, AppealStatus.OPEN);
        if (openAppeals > 0) {
            throw new BusinessException("仍有 " + openAppeals + " 条异议未处理，不能封榜");
        }
        v.status = VersionStatus.FINALIZED;
        v.finalizedAt = Instant.now();
        audit.log(actor, "RANKING_FINALIZE", "RankingVersion", String.valueOf(v.id),
                "封榜 v" + v.versionNo + "，本版成为发放依据"
                        + (reason == null || reason.isBlank() ? "" : "；备注：" + reason));
        return v;
    }

    /* ---------------- 封榜后更正 ---------------- */

    @Transactional
    public RankingVersion correctAfterFinalized(User actor, String term, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("封榜后更正必须写明错误原因与处理依据");
        }
        RuleSet rs = ruleService.requireLocked(term);
        RankingVersion latest = RankingVersion.findLatest(term);
        if (latest == null || latest.status != VersionStatus.FINALIZED) {
            throw new BusinessException("只能对已封榜的榜单进行更正（旧版记录保留不删改）");
        }
        List<ScoringService.StudentScore> scores = scoring.computeAll(term, ruleService.items(rs.id));
        RankingVersion v = createVersion(actor, rs, scores, VersionStatus.FINALIZED,
                VersionTrigger.CORRECTION, latest, null, "封榜后更正：" + reason);
        v.publishedAt = latest.publishedAt;
        v.appealDeadline = latest.appealDeadline;
        v.finalizedAt = Instant.now();
        // 旧封榜版本保持 FINALIZED 永久留痕（发放记录引用它），最新版本号代表当前有效榜单
        audit.log(actor, "RANKING_CORRECT", "RankingVersion", String.valueOf(v.id),
                "封榜后更正：v" + latest.versionNo + " -> v" + v.versionNo
                        + "，旧版与原发放记录均不改动；错误原因/依据：" + reason);
        notifyStudents(term, "RANKING_CORRECTED", "封榜榜单已更正（v" + v.versionNo + "）",
                "封榜后发现错误，已按相同锁定规则重新核算并生成更正版本，差额将通过补发/退回处理。依据：" + reason);
        return v;
    }

    /* ---------------- 版本构建 ---------------- */

    private RankingVersion createVersion(User actor, RuleSet rs, List<ScoringService.StudentScore> scores,
                                         VersionStatus status, VersionTrigger trigger,
                                         RankingVersion parent, Long appealId, String reason) {
        int no = parent == null ? 1 : parent.versionNo + 1;

        RankingVersion v = new RankingVersion();
        v.term = rs.term;
        v.versionNo = no;
        v.status = status;
        v.trigger = trigger;
        v.parentVersionId = parent == null ? null : parent.id;
        v.appealId = appealId;
        v.tieRule = rs.tieRule;
        v.scholarshipCount = rs.scholarshipCount;
        v.awardAmount = rs.awardAmount;
        v.reason = reason;
        v.createdBy = actor.name;
        v.createdAt = Instant.now();
        v.ruleSnapshotJson = buildSnapshot(rs);
        v.persist();

        Map<Long, RankingRow> prevRows = new HashMap<>();
        if (parent != null) {
            for (RankingRow pr : rows(parent.id)) {
                prevRows.put(pr.studentId, pr);
            }
        }

        List<ScoringService.StudentScore> ordered = scores.stream()
                .sorted(Comparator.comparingDouble((ScoringService.StudentScore s) -> s.total).reversed()
                        .thenComparing(s -> s.studentId))
                .toList();

        int compRank = 0;   // COMPETITION：新分组取位置名次，并列沿用组首
        int denseRank = 0;  // DENSE：每个新分组 +1
        double lastScore = Double.NaN;
        for (int i = 0; i < ordered.size(); i++) {
            ScoringService.StudentScore ss = ordered.get(i);
            boolean newGroup = i == 0 || ss.total != lastScore;
            if (newGroup) {
                lastScore = ss.total;
                compRank = i + 1;
                denseRank++;
            }
            int rank = rs.tieRule == TieRule.DENSE ? denseRank : compRank;
            RankingRow row = new RankingRow();
            row.versionId = v.id;
            row.studentId = ss.studentId;
            row.studentName = ss.studentName;
            row.studentNo = ss.studentNo;
            row.rank = rank;
            row.totalScore = ss.total;
            row.itemBreakdownJson = writeBreakdown(ss.items);
            row.awardEligible = rank <= rs.scholarshipCount;

            RankingRow prev = prevRows.get(ss.studentId);
            if (prev != null) {
                row.prevRank = prev.rank;
                row.prevScore = prev.totalScore;
                row.changed = prev.rank != row.rank || prev.totalScore != row.totalScore;
            } else {
                row.changed = false;
            }
            row.persist();
        }
        return v;
    }

    private String buildSnapshot(RuleSet rs) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("term", rs.term);
        snap.put("tieRule", rs.tieRule.name());
        snap.put("tieRuleDesc", rs.tieRule == TieRule.DENSE
                ? "密集排名：并列不占名次（1,2,2,3）"
                : "竞赛排名：并列占用名次（1,2,2,4）");
        snap.put("scholarshipCount", rs.scholarshipCount);
        snap.put("awardAmount", rs.awardAmount);
        snap.put("appealWindowDays", rs.appealWindowDays);
        snap.put("lockedBy", rs.lockedBy);
        snap.put("lockedAt", rs.lockedAt == null ? null : rs.lockedAt.toString());
        List<Map<String, Object>> items = new ArrayList<>();
        for (RuleItem ri : ruleService.items(rs.id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", ri.code);
            m.put("name", ri.name);
            m.put("category", ri.category);
            m.put("maxPoints", ri.maxPoints);
            items.add(m);
        }
        snap.put("items", items);
        try {
            return mapper.writeValueAsString(snap);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("规则快照序列化失败", e);
        }
    }

    private String writeBreakdown(List<ScoringService.ItemScore> items) {
        try {
            return mapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("分项得分序列化失败", e);
        }
    }

    private void notifyStudents(String term, String type, String title, String content) {
        List<User> students = User.find("role = ?1", Role.STUDENT).list();
        for (User s : students) {
            notifications.notify(s, type, title, content);
        }
    }

    private String nameOf(Long userId) {
        User u = userId == null ? null : User.findById(userId);
        return u == null ? "未知" : u.name;
    }

    private String nz(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }
}
