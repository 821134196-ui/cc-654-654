# 学生奖学金重算与申诉系统

面向奖学金公示后的得分溯源、分项申诉、排名重算与发放后纠错场景。技术栈：**Java 17 + Quarkus 3 + Hibernate ORM + SQLite**（后端）、**Svelte 4 + Vite**（前端），附件本地保存，导入与通知接口本地模拟，一条命令启动。

## 一键启动

```bash
./run.sh
```

脚本会：构建前端 → 把产物同步进后端静态资源目录 → 启动 Quarkus。启动后访问 <http://localhost:8080>（单端口同时提供页面与 API）。SQLite 数据库在 `backend/data/scholarship.db`，附件在 `backend/data/attachments/`。

- 需要 JDK 17+、Maven 3.9+、Node 18+；若系统无 JDK/Maven，脚本会自动使用 `~/tools` 下的便携版。
- 首次启动自动播种演示账号：学生 `S001~S004`、辅导员 `T001/T002`、教务 `A001`。登录页选择身份即可（本地用请求头 `X-User-Id` 模拟登录）。

运行测试（含三个关键检查点）：

```bash
./test.sh
```

## 业务流程与留痕设计

```
建批次 → 导入成绩(锁定前可覆盖) → 教务锁定排名规则(并列规则/名额/奖金/规则快照)
→ 学生提交加分材料 → 辅导员审核(禁止自审)
→ 公示并生成榜单 v1(快照) → 学生按分项提交异议(公示期内)
→ 教务裁定：成立→ScoreCorrection 留痕→按锁定规则重算新版本(变化标出,旧版永久可查)
→ 封榜(当前版本打封榜标记,作为发放依据)
→ 按封榜版本生成 Payment(不可改/不可删)
→ 发现错误：封榜后更正(新版本 POST_CLOSE) + PaymentAdjustment 补发/退回(只追加,带依据)
```

### 三个重点检查项的实现位置

1. **申诉重算**
   - 公示期校验、裁定入口：`service/AppealService.java`（仅 `PUBLISHED` 且未过截止期可提交；成立必须给更正分与依据）。
   - 改分必写 `ScoreCorrection`（source=APPEAL），再调 `CycleService.recalculate` 追加新版本；版本不覆盖旧版。
   - 名次变化（`UP/DOWN/SAME/NEW/OUT_AWARD`、`prevRank`）在 `CycleService.buildVersion` 中对比上一版本生成。
   - 并列名次严格使用**锁定时**的规则：`service/RankingCalculator.java`（竞赛 1,1,3 / 密集 1,1,2 / 顺序按学号决胜）。

2. **自审拦截**
   - `MaterialService.review`：`reviewer.id == material.submittedBy` 直接 **403**，与角色无关（辅导员代交的材料也不能由本人审批）。
   - 前端对“本人提交”的材料显式标注“禁止自审”，但拦截以后端为准（集成测试验证 403）。

3. **发放后的补退处理**
   - `Payment` 无任何更新/删除入口；整批只能生成一次，重复生成返回 409。
   - 差额只能追加 `PaymentAdjustment`：`SUPPLEMENT` 补发 / `REFUND` 退回，金额必须为正、依据必填；退回必须关联原发放，且累计退回不得超过“已发放+已补发”；净额 = 原发放 + 补发 − 退回。
   - 封榜后更正生成 `POST_CLOSE` 新版本，**不改变**封榜版本标记与原发放；新进获奖线者可无原发放记录直接补发。

### 数据模型（`backend/src/main/java/org/scholarship/domain/`）

| 表 | 作用 |
|---|---|
| users | 学生/辅导员/教务 |
| scholarship_cycles | 批次、锁定规则快照、状态（DRAFT/PUBLISHED/CLOSED） |
| base_scores | 各分项基础分与依据（更正时更新，变更另写流水） |
| bonus_materials | 加分材料、提交人/审核人/意见/附件路径 |
| appeals | 分项异议、裁定结论、更正分、产生的版本号 |
| score_corrections | 改分流水（异议/勘误/封榜后更正），排名变化的依据 |
| ranking_versions / ranking_entries | 榜单版本与条目快照（不可变、EAGER 随版本读取） |
| payments / payment_adjustments | 不可变发放记录 + 只追加的补发/退回 |
| notifications | 本地模拟通知（审核结果、公示、裁定、发放/补退） |

## 本地模拟说明

- **导入接口**：`POST /api/cycles/{id}/import`，接收 JSON 数组批量写成绩；规则锁定后拒绝整批导入，改分必须走更正/异议并留痕。
- **通知接口**：不做真实外发，只写入 `notifications` 表，页面右上角铃铛展示。
- **附件**：multipart 上传，保存到 `data/attachments/{cycleId}/uuid__文件名`，经 `/api/attachments/material/{id}` 下载（路径做了目录穿越校验）。

## 主要 API

- `POST /api/cycles`、`POST /api/cycles/{id}/lock|publish|close`
- `POST /api/cycles/{id}/import|corrections`；`GET /api/cycles/{id}/versions(/latest|/{no})`、`/scores/{studentId}`、`/corrections`
- `POST /api/materials`(multipart)、`POST /api/materials/{id}/review`、`GET /api/attachments/material/{id}`
- `POST /api/appeals`、`POST /api/appeals/{id}/decide`
- `POST /api/payments/cycles/{id}/batch`、`POST /api/payments/cycles/{id}/adjustments`、`GET /api/payments/cycles/{id}`
- `GET /api/notifications`、`POST /api/notifications/{id}/read`

角色：学生 `STUDENT`、辅导员 `COUNSELOR`、教务 `ADMIN`，所有请求带 `X-User-Id: <账号>`。

## 前端页面

- **学生**：各分项基础分/材料加分/小计与依据、材料提交（带附件）、公示期内按分项提交异议、异议状态与跳转新版本、所有历史榜单与变化标记。
- **辅导员**：待审/全部材料列表，本人提交材料红色警示且审批被后端 403，可下载附件、填核定分与审核依据。
- **教务**：建批次、锁规则、模拟导入、公示/封榜、异议裁定（成立即重算）、榜单版本对比、更正流水、发放批次生成与补发/退回登记（展示原发放金额与净额）。
