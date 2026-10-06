package org.scholarship;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端关键路径：
 * 1) 公示异议成立 → 按锁定并列规则重算、标出变化、旧榜单保留；
 * 2) 审核人不能审批自己提交的材料（403），他人可审；
 * 3) 封榜发放后原记录不可变，差额只能补发/退回；封榜后更正生成新版本并可补发新进者。
 */
@QuarkusTest
class ScholarshipWorkflowTest {

    private static final String ADMIN = "A001";
    private static final String COUNSELOR1 = "T001";
    private static final String COUNSELOR2 = "T002";

    private Map<String, Object> json(String body) {
        return io.restassured.path.json.JsonPath.with(body).getMap("");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(String body, String path) {
        return io.restassured.path.json.JsonPath.from(body).getList(path);
    }

    private int createCycle(String name) {
        String body = given().header("X-User-Id", ADMIN)
                .contentType("application/json")
                .body(Map.of("name", name))
                .post("/api/cycles").then().statusCode(200).extract().asString();
        return ((Number) json(body).get("id")).intValue();
    }

    @Test
    void fullWorkflow_appealRecalc_selfReviewBlocked_paymentAdjustments() {
        // ===== 准备：建批次、导入三人成绩、锁定规则、公示 =====
        int cycleId = createCycle("2026 国家奖学金");

        given().header("X-User-Id", ADMIN).contentType("application/json")
                .body(Map.of("rows", List.of(
                        Map.of("studentId", "S001", "itemKey", "ACADEMIC", "itemName", "学业成绩",
                                "score", 85, "evidence", "GPA 导入"),
                        Map.of("studentId", "S002", "itemKey", "ACADEMIC", "itemName", "学业成绩",
                                "score", 90, "evidence", "GPA 导入"),
                        Map.of("studentId", "S003", "itemKey", "ACADEMIC", "itemName", "学业成绩",
                                "score", 88, "evidence", "GPA 导入"))))
                .post("/api/cycles/" + cycleId + "/import")
                .then().statusCode(200).body("imported", is(3));

        // 锁定后不能再改规则
        given().header("X-User-Id", ADMIN).contentType("application/json")
                .body(Map.of("tieRule", "COMPETITION", "awardQuota", 2,
                        "awwardAmount_dummy", ""))
                .post("/api/cycles/" + cycleId + "/lock").then().statusCode(400);
        given().header("X-User-Id", ADMIN).contentType("application/json")
                .body(Map.of("tieRule", "COMPETITION", "awardQuota", 2,
                        "awardAmount", 5000, "rulesSnapshot", "学业成绩排名，并列按竞赛排名处理"))
                .post("/api/cycles/" + cycleId + "/lock").then().statusCode(200);
        given().header("X-User-Id", ADMIN).contentType("application/json")
                .body(Map.of("tieRule", "DENSE", "awardQuota", 2,
                        "awardAmount", 5000, "rulesSnapshot", "试图二次锁定"))
                .post("/api/cycles/" + cycleId + "/lock").then().statusCode(409);
        // 锁定后整批导入被拒绝
        given().header("X-User-Id", ADMIN).contentType("application/json")
                .body(Map.of("rows", List.of(
                        Map.of("studentId", "S001", "itemKey", "ACADEMIC", "score", 100))))
                .post("/api/cycles/" + cycleId + "/import").then().statusCode(409);

        given().header("X-User-Id", ADMIN).contentType("application/json")
                .body(Map.of())
                .post("/api/cycles/" + cycleId + "/publish").then().statusCode(200);

        // v1：S002(90) 第1，S003(88) 第2，S001(85) 第3；名额2 → S002/S003 获奖
        String v1Body = given().header("X-User-Id", ADMIN)
                .get("/api/cycles/" + cycleId + "/versions/1").then().statusCode(200)
                .extract().asString();
        List<Map<String, Object>> v1 = list(v1Body, "entries");
        assertEquals("S002", v1.get(0).get("studentId"));
        assertEquals(1, ((Number) v1.get(0).get("rank")).intValue());
        assertEquals("S003", v1.get(1).get("studentId"));
        assertEquals(true, v1.get(0).get("inAward"));
        assertEquals(false, v1.get(2).get("inAward"));

        // 非公示期不能申诉：先在另一批次 DRAFT 上验证 409（通过同一批次关闭后再验证更复杂，直接测前置）
        int draftCycle = createCycle("草稿批次");
        given().header("X-User-Id", "S001").contentType("application/json")
                .body(Map.of("cycleId", draftCycle, "itemKey", "ACADEMIC", "reason", "分数有误"))
                .post("/api/appeals").then().statusCode(409);

        // ===== 检查点 1：S001 异议成立 → 85 更正为 95 → 重算 v2，变化标出，v1 保留 =====
        String appealBody = given().header("X-User-Id", "S001").contentType("application/json")
                .body(Map.of("cycleId", cycleId, "itemKey", "ACADEMIC",
                        "reason", "学业成绩应为 95 分，原导入遗漏一门课程"))
                .post("/api/appeals").then().statusCode(200).extract().asString();
        int appealId = ((Number) json(appealBody).get("id")).intValue();

        // 辅导员无权裁定
        given().header("X-User-Id", COUNSELOR1).contentType("application/json")
                .body(Map.of("upheld", true, "correctedScore", 95, "comment", "凭证有效"))
                .post("/api/appeals/" + appealId + "/decide").then().statusCode(403);
        // 成立但不给更正分 → 400
        given().header("X-User-Id", ADMIN).contentType("application/json")
                .body(Map.of("upheld", true, "comment", "凭证有效"))
                .post("/api/appeals/" + appealId + "/decide").then().statusCode(400);
        // 教务裁定成立
        given().header("X-User-Id", ADMIN).contentType("application/json")
                .body(Map.of("upheld", true, "correctedScore", 95,
                        "comment", "教务处复核成绩单，确认为 95 分"))
                .post("/api/appeals/" + appealId + "/decide").then().statusCode(200)
                .body("status", is("UPHELD")).body("resultingVersion", is(2));

        String v2Body = given().header("X-User-Id", ADMIN)
                .get("/api/cycles/" + cycleId + "/versions/2").then().statusCode(200)
                .extract().asString();
        List<Map<String, Object>> v2 = list(v2Body, "entries");
        // 新排名：S001(95) 第1，S002(90) 第2，S003(88) 第3（掉出获奖线）
        assertEquals("S001", v2.get(0).get("studentId"));
        assertEquals(1, ((Number) v2.get(0).get("rank")).intValue());
        assertEquals("UP", v2.get(0).get("change"));
        assertEquals(3, ((Number) v2.get(0).get("prevRank")).intValue());
        assertEquals("OUT_AWARD", v2.get(2).get("change"));
        assertEquals(false, v2.get(2).get("inAward"));
        assertEquals(true, v2.get(0).get("inAward"));
        assertEquals("COMPETITION", json(v2Body).get("tieRule"));
        assertNotNull(json(v2Body).get("basis"));

        // 旧榜单 v1 原样可查：S002 仍为第 1
        String v1Again = given().header("X-User-Id", ADMIN)
                .get("/api/cycles/" + cycleId + "/versions/1").then().statusCode(200)
                .extract().asString();
        List<Map<String, Object>> v1Entries = list(v1Again, "entries");
        assertEquals("S002", v1Entries.get(0).get("studentId"));
        assertEquals(1, ((Number) v1Entries.get(0).get("rank")).intValue());

        // 更正流水留痕
        given().header("X-User-Id", ADMIN)
                .get("/api/cycles/" + cycleId + "/corrections").then().statusCode(200)
                .body("[0].oldScore", is(85)).body("[0].newScore", is(95))
                .body("[0].source", is("APPEAL")).body("[0].appealId", is(appealId));

        // 学生得分页展示分项与依据，且本人只能查自己
        given().header("X-User-Id", "S001")
                .get("/api/cycles/" + cycleId + "/scores/S002").then().statusCode(403);
        given().header("X-User-Id", "S001")
                .get("/api/cycles/" + cycleId + "/scores/S001").then().statusCode(200)
                .body("total", is(95)).body("items[0].itemName", is("学业成绩"));

        // ===== 检查点 2：自审拦截 =====
        int matCycle = createCycle("材料审核批次");
        // 辅导员 T001 代学生提交材料
        String matBody = given()
                .multiPart("cycleId", String.valueOf(matCycle))
                .multiPart("studentId", "S001")
                .multiPart("itemKey", "COMP")
                .multiPart("itemName", "学科竞赛")
                .multiPart("title", "程序设计竞赛国一")
                .multiPart("claimedPoints", "8")
                .header("X-User-Id", COUNSELOR1)
                .post("/api/materials").then().statusCode(200).extract().asString();
        int materialId = ((Number) json(matBody).get("id")).intValue();
        assertEquals(COUNSELOR1, json(matBody).get("submittedBy"));

        // 提交人本人审批 → 403
        given().header("X-User-Id", COUNSELOR1).contentType("application/json")
                .body(Map.of("approved", true, "awardedPoints", 8, "comment", "材料真实"))
                .post("/api/materials/" + materialId + "/review")
                .then().statusCode(403);
        // 学生不能审核
        given().header("X-User-Id", "S001").contentType("application/json")
                .body(Map.of("approved", true, "awardedPoints", 8))
                .post("/api/materials/" + materialId + "/review")
                .then().statusCode(403);
        // 另一位辅导员审批通过 → 200
        given().header("X-User-Id", COUNSELOR2).contentType("application/json")
                .body(Map.of("approved", true, "awardedPoints", 8, "comment", "证书核验无误"))
                .post("/api/materials/" + materialId + "/review")
                .then().statusCode(200).body("status", is("APPROVED"))
                .body("reviewedBy", is(COUNSELOR2));
        // 重复审核 → 409
        given().header("X-User-Id", COUNSELOR2).contentType("application/json")
                .body(Map.of("approved", false))
                .post("/api/materials/" + materialId + "/review")
                .then().statusCode(409);

        // ===== 封榜 → 发放（依据 v2：S001/S002 获奖） =====
        given().header("X-User-Id", ADMIN)
                .post("/api/cycles/" + cycleId + "/close").then().statusCode(200)
                .body("sealedFlag", is(true)).body("versionNo", is(2));
        given().header("X-User-Id", ADMIN)
                .post("/api/payments/cycles/" + cycleId + "/batch").then().statusCode(200);
        // 重复发放被拒绝（原记录不可改）
        given().header("X-User-Id", ADMIN)
                .post("/api/payments/cycles/" + cycleId + "/batch").then().statusCode(409);

        String payBody = given().header("X-User-Id", ADMIN)
                .get("/api/payments/cycles/" + cycleId).then().statusCode(200).extract().asString();
        List<Map<String, Object>> payments = list(payBody, "payments");
        assertEquals(2, payments.size());
        assertTrue(payments.stream().anyMatch(p -> p.get("studentId").equals("S001")));
        assertTrue(payments.stream().anyMatch(p -> p.get("studentId").equals("S002")));
        long s001PaymentId = payments.stream().filter(p -> p.get("studentId").equals("S001"))
                .findFirst().map(p -> ((Number) p.get("id")).longValue()).orElseThrow();

        // ===== 检查点 3：发放后补退；原发放不可变 =====
        // 缺依据 → 400
        given().header("X-User-Id", ADMIN).contentType("application/json")
                .body(Map.of("studentId", "S001", "type", "REFUND", "amount", 1000))
                .post("/api/payments/cycles/" + cycleId + "/adjustments").then().statusCode(400);
        // 超额退回（净额 5000，退 6000）→ 409
        given().header("X-User-Id", ADMIN).contentType("application/json")
                .body(Map.of("studentId", "S001", "type", "REFUND", "amount", 6000,
                        "paymentId", s001PaymentId, "reason", "测试超额"))
                .post("/api/payments/cycles/" + cycleId + "/adjustments").then().statusCode(409);
        // 退回 5000 → 200
        given().header("X-User-Id", ADMIN).contentType("application/json")
                .body(Map.of("studentId", "S001", "type", "REFUND", "amount", 5000,
                        "paymentId", s001PaymentId, "reason", "异议复核后不符合获奖条件，全额退回"))
                .post("/api/payments/cycles/" + cycleId + "/adjustments").then().statusCode(200)
                .body("type", is("REFUND"));
        // 补发 200 → 200
        given().header("X-User-Id", ADMIN).contentType("application/json")
                .body(Map.of("studentId", "S001", "type", "SUPPLEMENT", "amount", 200,
                        "paymentId", s001PaymentId, "reason", "奖金额度政策调整补差"))
                .post("/api/payments/cycles/" + cycleId + "/adjustments").then().statusCode(200);

        String afterBody = given().header("X-User-Id", ADMIN)
                .get("/api/payments/cycles/" + cycleId).then().statusCode(200).extract().asString();
        List<Map<String, Object>> afterPays = list(afterBody, "payments");
        Map<String, Object> s001 = afterPays.stream()
                .filter(p -> p.get("studentId").equals("S001")).findFirst().orElseThrow();
        // 原发放金额仍是 5000，净额 = 5000 - 5000 + 200 = 200
        assertEquals(5000f, ((Number) s001.get("amount")).floatValue(), 0.001f);
        assertEquals(200f, ((Number) s001.get("netAmount")).floatValue(), 0.001f);
        assertEquals(2, list(afterBody, "adjustments").size());

        // ===== 封榜后更正：生成 v3，封榜版本仍为 v2，原发放不动；为新进者补发（无原记录） =====
        given().header("X-User-Id", ADMIN).contentType("application/json")
                .body(Map.of("studentId", "S003", "itemKey", "ACADEMIC", "newScore", 99,
                        "source", "POST_CLOSE", "reason", "封榜后接教务处勘误：S003 成绩漏统"))
                .post("/api/cycles/" + cycleId + "/corrections").then().statusCode(200)
                .body("versionNo", is(3)).body("trigger", is("POST_CLOSE"));
        // v2 封榜标记不变
        given().header("X-User-Id", ADMIN)
                .get("/api/cycles/" + cycleId + "/versions/2").then().statusCode(200)
                .body("sealedFlag", is(true));
        // v3 未被当作封榜版本
        given().header("X-User-Id", ADMIN)
                .get("/api/cycles/" + cycleId + "/versions/3").then().statusCode(200)
                .body("sealedFlag", is(false))
                .body("entries[0].studentId", is("S003"));
        // 发放批次仍只有两条原记录
        String payBody2 = given().header("X-User-Id", ADMIN)
                .get("/api/payments/cycles/" + cycleId).then().extract().asString();
        assertEquals(2, list(payBody2, "payments").size());
        // 为封榜后新进获奖线的 S003 登记补发（paymentId 可空）
        given().header("X-User-Id", ADMIN).contentType("application/json")
                .body(Map.of("studentId", "S003", "type", "SUPPLEMENT", "amount", 5000,
                        "reason", "封榜后勘误 v3 显示 S003 进入获奖线，据更正记录补发"))
                .post("/api/payments/cycles/" + cycleId + "/adjustments").then().statusCode(200)
                .body("paymentId", nullValue());
    }
}
