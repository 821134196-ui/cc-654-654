package edu.scholar;

import edu.scholar.domain.*;
import edu.scholar.service.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 三条核心红线的端到端验证：
 * 1. 申诉成立 -> 重算排名、标出变化、旧版可查、并列按锁定规则；
 * 2. 审核人不能审批自己提交的材料（自审拦截 403）；
 * 3. 封榜发放后的错误只能补发/退回，原发放记录不可修改。
 */
@QuarkusTest
class CoreFlowTest {

    static final String TERM = "test-term";

    @Inject RuleService ruleService;
    @Inject ImportService importService;
    @Inject MaterialService materialService;
    @Inject RankingService rankingService;
    @Inject DisbursementService disbursementService;

    User academic, counselorA, counselorB;
    User s1, s2, s3, s4, s5;

    @BeforeEach
    @Transactional
    void clean() {
        // 清空全部业务数据（无外键约束，顺序无关）
        DisbursementAdjustment.deleteAll();
        Disbursement.deleteAll();
        RankingRow.deleteAll();
        RankingVersion.deleteAll();
        Appeal.deleteAll();
        Material.deleteAll();
        CourseScore.deleteAll();
        Notification.deleteAll();
        AuditLog.deleteAll();
        RuleItem.deleteAll();
        RuleSet.deleteAll();
        User.deleteAll();

        academic = user("ac", "教务", Role.ACADEMIC);
        counselorA = user("ca", "辅导员A", Role.COUNSELOR);
        counselorB = user("cb", "辅导员B", Role.COUNSELOR);
        s1 = user("t1", "学生甲", Role.STUDENT);
        s2 = user("t2", "学生乙", Role.STUDENT);
        s3 = user("t3", "学生丙", Role.STUDENT);
        s4 = user("t4", "学生丁", Role.STUDENT);
        s5 = user("t5", "学生戊", Role.STUDENT);
    }

    private User user(String username, String name, Role role) {
        User u = new User();
        u.username = username;
        u.name = name;
        u.role = role;
        u.studentNo = role == Role.STUDENT ? "N" + username : null;
        u.persist();
        return u;
    }

    private void setupRuleAndScores(TieRule tieRule, Integer count) {
        ruleService.configure(academic, TERM, tieRule, count, 500000L, 7);
        ruleService.addItem(academic, TERM, "GPA", "学业成绩", RuleItem.CATEGORY_GPA, 100, 10);
        ruleService.addItem(academic, TERM, "BONUS", "竞赛加分", RuleItem.CATEGORY_MATERIAL, 10, 20);
        ruleService.lock(academic, TERM);

        List<ImportService.ScoreIn> rows = List.of(
                score("t1", 95), score("t2", 90), score("t3", 90),
                score("t4", 85), score("t5", 80));
        importService.importScores(academic, TERM, rows);
    }

    private ImportService.ScoreIn score(String username, double s) {
        ImportService.ScoreIn in = new ImportService.ScoreIn();
        in.studentUsername = username;
        in.courseName = "综合";
        in.credit = 1;
        in.score = s;
        return in;
    }

    private Long restAppeal(long studentId, Long versionId, String reason) {
        return given()
                .header("X-User-Id", studentId)
                .contentType("application/json")
                .body("""
                    {"versionId":%d,"itemCode":null,"reason":"%s"}
                    """.formatted(versionId, reason))
                .when().post("/api/appeals")
                .then().statusCode(200)
                .extract().jsonPath().getLong("id");
    }

    /* ---------------- 测试 1：自审拦截 ---------------- */

    @Test
    void selfReview_isBlocked_butAnotherCounselorCanApprove() {
        // 辅导员A 代学生提交材料
        Material m = materialService.submit(counselorA, s1.id, "BONUS",
                "省级竞赛一等奖", 8, "证书编号 XYZ", null, null);

        // A 试图审批自己提交的材料 -> 403
        given()
                .header("X-User-Id", counselorA.id)
                .contentType("application/json")
                .body("{\"approved\":true,\"approvedPoints\":8,\"comment\":\"符合加分条件\"}")
                .when().post("/api/materials/" + m.id + "/review")
                .then().statusCode(403)
                .body("error", containsString("不能审批自己提交的材料"));

        // 材料状态保持待审核
        Material reloaded = Material.findById(m.id);
        assertEquals(MaterialStatus.SUBMITTED, reloaded.status);
        assertNull(reloaded.reviewedBy);

        // 自审拦截留痕
        long blockedLogs = AuditLog.count("action", "MATERIAL_SELF_REVIEW_BLOCKED");
        assertTrue(blockedLogs >= 1, "自审拦截必须写审计日志");

        // 另一位辅导员 B 可以正常审批
        given()
                .header("X-User-Id", counselorB.id)
                .contentType("application/json")
                .body("{\"approved\":true,\"approvedPoints\":8,\"comment\":\"符合加分条件，认定8分\"}")
                .when().post("/api/materials/" + m.id + "/review")
                .then().statusCode(200)
                .body("status", equalTo("APPROVED"));

        // 缺处理依据也应被拒
        Material m2 = materialService.submit(s1, s2.id, "BONUS", "第二项", 2, null, null, null);
        given()
                .header("X-User-Id", counselorA.id)
                .contentType("application/json")
                .body("{\"approved\":true,\"approvedPoints\":2,\"comment\":\"\"}")
                .when().post("/api/materials/" + m2.id + "/review")
                .then().statusCode(400);
    }

    /* ---------------- 测试 2：申诉重算、变化标记、旧版可查、并列规则 ---------------- */

    @Test
    void appealUpheld_recalculatesMarksChanges_keepsOldVersion_appliesTieRule() {
        // 竞赛排名 1,2,2,4：名额 3 -> 名次 1/2/2 获奖，第 4 名不获奖
        setupRuleAndScores(TieRule.COMPETITION, 3);
        RankingVersion v1 = rankingService.publish(academic, TERM, "首次公示");

        List<RankingRow> rows1 = rankingService.rows(v1.id);
        assertEquals(List.of(1, 2, 2, 4, 5),
                rows1.stream().map(r -> r.rank).sorted().toList(),
                "并列必须按锁定的竞赛排名规则：1,2,2,4,5");
        // 学生甲第1；乙、丙并列第2；丁第4（不获奖）
        RankingRow dingV1 = rows1.stream().filter(r -> r.studentId.equals(s4.id)).findFirst().orElseThrow();
        assertEquals(4, dingV1.rank);
        assertFalse(dingV1.awardEligible);
        // 并列第2的两人都在名额内
        assertEquals(3, rows1.stream().filter(r -> r.awardEligible).count());

        // 丁的竞赛材料经辅导员 B 审核通过加 8 分（超过乙、丙），丁提出异议称漏录加分
        Material mat = materialService.submit(s4, s4.id, "BONUS", "国家级竞赛获奖", 8, "证书", null, null);
        materialService.review(counselorB, mat.id, true, 8.0, "证书属实，按规则加8分");

        long appealId = restAppeal(s4.id, v1.id, "本人国家级竞赛获奖漏录，应加8分");
        RankingVersion v2 = rankingService.handleAppeal(academic, appealId, true,
                "证书核验属实，异议成立，按锁定规则重算");

        assertNotNull(v2);
        assertEquals(2, v2.versionNo);
        assertEquals(VersionStatus.PUBLISHED, v2.status);
        assertEquals(VersionTrigger.APPEAL, v2.trigger);

        // 旧版本仍可查，状态变为 SUPERSEDED，内容不被修改
        RankingVersion old = rankingService.requireVersion(v1.id);
        assertEquals(VersionStatus.SUPERSEDED, old.status);
        assertEquals(List.of(1, 2, 2, 4, 5),
                rankingService.rows(v1.id).stream().map(r -> r.rank).sorted().toList());

        // 新版本：丁总分 93 升至第 2，乙、丙降到第 3
        List<RankingRow> rows2 = rankingService.rows(v2.id);
        RankingRow dingV2 = rows2.stream().filter(r -> r.studentId.equals(s4.id)).findFirst().orElseThrow();
        assertEquals(2, dingV2.rank);
        assertEquals(93.0, dingV2.totalScore, 0.001);
        assertTrue(dingV2.changed, "名次变化必须标出");
        assertEquals(4, dingV2.prevRank);

        RankingRow jiaV2 = rows2.stream().filter(r -> r.studentId.equals(s1.id)).findFirst().orElseThrow();
        assertFalse(jiaV2.changed);
        assertEquals(1, jiaV2.prevRank);
        // 锁定规则：名次 ≤ 名额即具备资格，获奖线并列者全部入选。
        // v1 获奖 3 人（1,2,2）；v2 排名 1,2,3,3,5，第3名并列 -> 4 人，并列按锁定规则处理。
        assertEquals(4, rows2.stream().filter(r -> r.awardEligible).count());

        // 申诉记录关联新版本
        Appeal a = Appeal.findById(appealId);
        assertEquals(AppealStatus.UPHELD, a.status);
        assertEquals(v2.id, a.newVersionId);
    }

    @Test
    void rejectedAppeal_doesNotCreateVersion() {
        setupRuleAndScores(TieRule.COMPETITION, 3);
        RankingVersion v1 = rankingService.publish(academic, TERM, null);
        long appealId = restAppeal(s5.id, v1.id, "觉得分数不对");
        RankingVersion nv = rankingService.handleAppeal(academic, appealId, false, "成绩核对无误，驳回");
        assertNull(nv);
        assertEquals(1, RankingVersion.count("term", TERM));
        assertEquals(AppealStatus.REJECTED, ((Appeal) Appeal.findById(appealId)).status);
    }

    /* ---------------- 测试 3：封榜发放后的更正只能补发/退回 ---------------- */

    @Test
    void correctionAfterPayment_createsMakeupAndRefund_originalRecordUntouched() {
        // 互不相同的成绩，竞赛排名 1..5：初始获奖 s1(95) s2(92) s3(88)
        ruleService.configure(academic, TERM, TieRule.COMPETITION, 3, 500000L, 7);
        ruleService.addItem(academic, TERM, "GPA", "学业成绩", RuleItem.CATEGORY_GPA, 100, 10);
        ruleService.addItem(academic, TERM, "BONUS", "竞赛加分", RuleItem.CATEGORY_MATERIAL, 10, 20);
        ruleService.lock(academic, TERM);
        importService.importScores(academic, TERM, List.of(
                score("t1", 95), score("t2", 92), score("t3", 88),
                score("t4", 85), score("t5", 80)));

        RankingVersion v1 = rankingService.publish(academic, TERM, null);
        rankingService.finalize(academic, v1.id, null);

        // 按 v1 发放：s1(1)、s2(2)、s3(3) 三人
        List<Disbursement> paid = disbursementService.pay(academic, v1.id, "统一发放");
        assertEquals(3, paid.size());
        long paidSnapshotCount = Disbursement.count();
        User probe = (User) User.findById(s1.id);

        // 封榜后发现 s4 竞赛获奖漏算，材料补审通过 +6 -> 91，更正榜单
        Material mat = materialService.submit(s4, s4.id, "BONUS", "国家级竞赛获奖", 6, "证书", null, null);
        materialService.review(counselorA, mat.id, true, 6.0, "封榜后复核：证书属实，补认定6分");

        assertThrows(BusinessException.class,
                () -> rankingService.publish(academic, TERM, "试图重新公示"),
                "已封榜后不能重新公示");

        RankingVersion v2 = rankingService.correctAfterFinalized(academic, TERM,
                "s4 国家级竞赛材料经复核属实，原核算漏加6分");
        assertEquals(VersionTrigger.CORRECTION, v2.trigger);
        assertEquals(VersionStatus.FINALIZED, v2.status);
        // 更正后排名 s1(95)=1 s2(92)=2 s4(91)=3 s3(88)=4 s5(80)=5
        List<RankingRow> rows2 = rankingService.rows(v2.id);
        RankingRow s4row = rows2.stream().filter(r -> r.studentId.equals(s4.id)).findFirst().orElseThrow();
        assertEquals(3, s4row.rank);
        assertEquals(91.0, s4row.totalScore, 0.001);
        assertTrue(s4row.awardEligible, "s4 补分后应进入名额内");
        assertTrue(s4row.changed);
        assertEquals(4, s4row.prevRank);
        RankingRow s3row = rows2.stream().filter(r -> r.studentId.equals(s3.id)).findFirst().orElseThrow();
        assertFalse(s3row.awardEligible, "s3 应落榜");

        // 对账生成补发/退回：s4 补发、s3 退回，s1/s2 无差额
        List<DisbursementAdjustment> adjs =
                disbursementService.reconcile(academic, v2.id, "按更正版本 v2 多退少补");
        assertEquals(2, adjs.size(), "恰好一条补发 + 一条退回");
        DisbursementAdjustment makeup = adjs.stream()
                .filter(a -> a.kind == AdjustmentKind.MAKEUP).findFirst().orElseThrow();
        DisbursementAdjustment refund = adjs.stream()
                .filter(a -> a.kind == AdjustmentKind.REFUND).findFirst().orElseThrow();
        assertEquals(s4.id, makeup.studentId);
        assertNull(makeup.disbursementId, "新上榜补发无原发放记录");
        assertEquals(500000L, makeup.amount);
        assertEquals(s3.id, refund.studentId);
        assertNotNull(refund.disbursementId, "退回必须关联原发放记录");
        assertEquals(500000L, refund.amount);
        assertNotNull(refund.reason);

        // 原发放记录数量、金额、版本引用完全不变
        assertEquals(paidSnapshotCount, Disbursement.count(), "原发放记录不得增删");
        List<Disbursement> all = Disbursement.find("order by id").list();
        for (Disbursement d : all) {
            assertEquals(500000L, d.amount);
            assertEquals(v1.id, d.versionId, "原发放记录必须仍指向原封榜版本");
        }

        // 重复对账被拒绝（补退记录不可重复生成）
        assertThrows(BusinessException.class,
                () -> disbursementService.reconcile(academic, v2.id, "再次对账"));
        // 补退必须有依据
        assertThrows(BusinessException.class,
                () -> disbursementService.reconcile(academic, v2.id, " "));
    }

    /* ---------------- 规则锁定红线 ---------------- */

    @Test
    void lockedRules_cannotBeChanged_andPublishRequiresLock() {
        ruleService.configure(academic, TERM, TieRule.COMPETITION, 3, 500000L, 7);
        // 未锁定不能公示
        assertThrows(BusinessException.class, () -> rankingService.publish(academic, TERM, null));
        ruleService.addItem(academic, TERM, "GPA", "成绩", RuleItem.CATEGORY_GPA, 100, 1);
        ruleService.lock(academic, TERM);
        // 锁定后改规则/加分项都被拒
        assertThrows(BusinessException.class,
                () -> ruleService.configure(academic, TERM, TieRule.DENSE, 5, 1L, 1));
        assertThrows(BusinessException.class,
                () -> ruleService.addItem(academic, TERM, "X", "X", RuleItem.CATEGORY_MATERIAL, 1, 1));
    }
}
