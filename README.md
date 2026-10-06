# 学生奖学金重算与申诉系统

公示后学生可查看得分来源、针对具体分项申诉；教务在公示前锁定排名规则，
所有重算、封榜更正、发放补退都保留处理依据与历史版本。

- 后端：Java 17 + Quarkus 3（REST / Hibernate ORM Panache）
- 数据库：SQLite（`data/scholar.db`，xerial 驱动 + Hibernate 社区方言）
- 前端：Svelte 4 + Vite，构建产物由 Quarkus 作为静态资源一并提供
- 模拟能力：本地用户切换（`X-User-Id`，无密码）、成绩导入接口、站内通知、附件本地保存（`data/attachments`）

## 一条命令启动

```bash
./run.sh            # 构建前端+后端，启动后访问 http://localhost:8080
./run.sh --dev      # 开发模式：Vite(5173) 热更新 + Quarkus dev(8080)
./run.sh --reset    # 清空本地 SQLite/附件后重新启动
```

首次构建需要联网下载 Maven/npm 依赖。内置演示账号：

| 身份 | 账号 | 说明 |
| --- | --- | --- |
| 教务 | jiaowu / 王教务 | 锁定规则、公示、处理异议、封榜、更正、发放 |
| 辅导员 | fudaoyuan1 / 李辅导员、fudaoyuan2 / 赵辅导员 | 审核加分材料（不能自审） |
| 学生 | s1 张三、s2 李四、s3 王五、s4 赵六、s5 钱七 | 查看得分依据、提交异议、查看发放 |

另有 `demo.sh`：启动后运行，可一键 curl 演练完整业务流程（含三条红线）。

## 业务规则与红线

1. **规则先锁定再公示**：并列规则（竞赛 1,2,2,4 / 密集 1,2,2,3）、名额、奖金额、
   公示期在公示前锁定；每个榜单版本都保存规则快照 JSON，锁定后不可修改。
2. **材料审核自审拦截**：材料记录 `submittedBy`；审核人等于提交人时接口返回 403，
   并写 `MATERIAL_SELF_REVIEW_BLOCKED` 审计日志，必须由另一位辅导员审批；审核必须填写依据。
3. **申诉与重算**：仅公示中且未过公示截止可提异议，须针对分项并写明理由；
   异议成立 → 用最新数据重算生成新版本（trigger=APPEAL），旧版置为 SUPERSEDED 但永久可查，
   每行记录 `prevRank/prevScore/changed` 标出变化；驳回不产生新版本。
4. **并列处理**：按版本锁定的 `tieRule` 计算名次；名次 ≤ 名额即具备获奖资格，
   获奖线并列者全部入选（并列规则随版本锁定，杜绝人为取舍）。
5. **封榜后更正**：封榜（FINALIZED）后不能重新公示，只能生成更正版本（trigger=CORRECTION），
   旧封榜版本与其上的发放记录保持不变。
6. **发放差额只能补发/退回**：`Disbursement` 一经生成不可改不可删；
   更正后对账生成 `DisbursementAdjustment`（MAKEUP/REFUND），每条写明依据、关联原记录，且幂等不可重复生成。
7. **全程留痕**：`AuditLog` 记录规则配置/锁定、成绩导入、材料审核与自审拦截、
   公示、异议处理、封榜、更正、发放、补退；前端教务页「处理依据」可查。

## 主要接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/users` `/api/me` | 用户与当前模拟身份 |
| PUT/POST | `/api/rules/{term}`、`/items`、`/lock` | 教务配置/锁定规则 |
| POST | `/api/import/scores` | 本地模拟成绩导入 |
| POST/GET | `/api/materials`（multipart）、`/pending`、`/mine`、`/{id}/review`、`/{id}/attachment` | 材料、附件、审核 |
| GET/POST | `/api/rankings/publish`、`/{term}/versions`、`/versions/{id}/rows`、`/my-row`、`/{id}/finalize`、`/correct` | 榜单版本 |
| GET/POST | `/api/appeals`、`/mine`、`/{id}/handle` | 异议 |
| POST/GET | `/api/disbursements/pay`、`/reconcile`、`/adjustments` | 发放与补退 |
| GET | `/api/notifications` | 模拟站内通知 |
| GET | `/api/audit` | 处理依据（教务/辅导员） |

## 测试

```bash
mvn test
```

`CoreFlowTest` 覆盖：申诉成立重算+变化标记+旧版留存+并列规则、自审 403 拦截、
封榜发放后更正产生补发/退回且原发放记录不变、锁定规则不可改。
