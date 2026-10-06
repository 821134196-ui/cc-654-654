#!/usr/bin/env bash
# 端到端 curl 演练：规则锁定 → 自审拦截 → 申诉重算 → 封榜发放 → 更正补发/退回。
# 用法：先 ./run.sh --reset 启动系统，另开终端执行 ./demo.sh [BASE_URL]
set -uo pipefail
BASE="${1:-http://localhost:8080}"
TERM="DEMO-$(date +%s)"

C=$(command -v curl)

req() { # method path userid [json]
  local m="$1" p="$2" uid="$3" body="${4:-}"
  if [ -n "$body" ]; then
    curl -s -X "$m" "$BASE$p" -H "X-User-Id: $uid" -H 'Content-Type: application/json' -d "$body"
  else
    curl -s -X "$m" "$BASE$p" -H "X-User-Id: $uid"
  fi
}

id_of() { :; }

reqform() { # path userid field=value...  (multipart 材料提交)
  local p="$1" uid="$2"; shift 2
  local args=(-s -X POST "$BASE$p" -H "X-User-Id: $uid")
  for kv in "$@"; do args+=( -F "$kv" ); done
  curl "${args[@]}"
}

echo "== 0. 读取内置用户 =="
USERS=$(curl -s "$BASE/api/users")
uid() { echo "$USERS" | python3 -c "import sys,json;print([u['id'] for u in json.load(sys.stdin) if u['username']=='$1'][0])"; }
AC=$(uid jiaowu); C1=$(uid fudaoyuan1); C2=$(uid fudaoyuan2)
S1=$(uid s1); S2=$(uid s2); S3=$(uid s3); S4=$(uid s4); S5=$(uid s5)
echo "教务=$AC 辅导员A=$C1 辅导员B=$C2 学生 s1..s5=$S1 $S2 $S3 $S4 $S5"

echo; echo "== 1. 教务配置并锁定规则（竞赛排名 1,2,2,4，名额 3，奖金 5000 元，公示 7 天） =="
req PUT "/api/rules/$TERM" "$AC" \
  '{"tieRule":"COMPETITION","scholarshipCount":3,"awardAmount":500000,"appealWindowDays":7}' >/dev/null
req POST "/api/rules/$TERM/items" "$AC" '{"code":"GPA","name":"学业成绩","category":"GPA","maxPoints":100,"sortOrder":10}' >/dev/null
req POST "/api/rules/$TERM/items" "$AC" '{"code":"BONUS","name":"竞赛加分","category":"MATERIAL","maxPoints":10,"sortOrder":20}' >/dev/null
req POST "/api/rules/$TERM/lock" "$AC" | python3 -m json.tool | grep -E '"status"|"tieRule"'
echo "-- 红线1：锁定后再改规则，应被拒绝 --"
req PUT "/api/rules/$TERM" "$AC" '{"tieRule":"DENSE","scholarshipCount":5}' ; echo

echo; echo "== 2. 本地模拟导入成绩（s1..s5 互不相同） =="
req POST "/api/import/scores" "$AC" "{\"term\":\"$TERM\",\"scores\":[
 {\"studentUsername\":\"s1\",\"courseName\":\"综合\",\"credit\":1,\"score\":95},
 {\"studentUsername\":\"s2\",\"courseName\":\"综合\",\"credit\":1,\"score\":92},
 {\"studentUsername\":\"s3\",\"courseName\":\"综合\",\"credit\":1,\"score\":90},
 {\"studentUsername\":\"s4\",\"courseName\":\"综合\",\"credit\":1,\"score\":85},
 {\"studentUsername\":\"s5\",\"courseName\":\"综合\",\"credit\":1,\"score\":88}]}"
echo
# v1 名次：s1(95)=1 s2(92)=2 s3(90)=3 s5(88)=4 s4(85)=5，获奖 s1 s2 s3

echo; echo "== 3. 首次公示 v1 =="
V1=$(req POST "/api/rankings/publish" "$AC" "{\"term\":\"$TERM\",\"reason\":\"按已锁定规则公示\"}")
V1ID=$(echo "$V1" | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
echo "v1 id=$V1ID"
req GET "/api/rankings/versions/$V1ID/rows" "$S1" | python3 -c "
import sys,json
for r in json.load(sys.stdin):
    print(f\"  名次{r['rank']:>2}  {r['studentName']}  总分{r['totalScore']}  {'获奖' if r['awardEligible'] else ''}\")"

echo; echo "== 4. 红线2：辅导员A代s4提交材料，A自己审批必须被403拦截；换辅导员B通过(+6) =="
MID=$(reqform "/api/materials" "$C1" \
  "studentId=$S4" "itemCode=BONUS" "title=国家级竞赛一等奖" "claimedPoints=6" "evidence=证书编号NAT-001" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
echo "材料 id=$MID（提交人=辅导员A $C1）"
echo -n "  A 自审 -> "; req POST "/api/materials/$MID/review" "$C1" '{"approved":true,"approvedPoints":6,"comment":"我自己批"}'; echo
req POST "/api/materials/$MID/review" "$C2" '{"approved":true,"approvedPoints":6,"comment":"核验国家竞赛证书真实有效，按规则加6分"}' >/dev/null
echo "  辅导员B 审批通过（材料状态见下）"
req GET "/api/materials/pending" "$C1" >/dev/null && echo "  自审拦截依据已写入审计（见第 9 步）"

echo; echo "== 5. s4 在公示期内对 v1 提异议，教务认定成立 -> 重算生成 v2，旧版可查、变化标出 =="
AID=$(req POST "/api/appeals" "$S4" \
  "{\"versionId\":$V1ID,\"itemCode\":\"BONUS\",\"reason\":\"国家级竞赛获奖漏录，应加6分\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
V2=$(req POST "/api/appeals/$AID/handle" "$AC" '{"uphold":true,"comment":"证书核验属实，异议成立，按锁定规则重算"}')
V2ID=$(echo "$V2" | python3 -c "import sys,json;print(json.load(sys.stdin)['newVersion']['id'])")
echo "v2 id=$V2ID（v1 旧榜单仍可查，状态 SUPERSEDED）"
req GET "/api/rankings/versions/$V2ID/rows" "$S1" | python3 -c "
import sys,json
for r in json.load(sys.stdin):
    ch=''
    if r.get('prevRank') is not None:
        ch = f\"  上版第{r['prevRank']}名\" + ('，★有变化' if r['changed'] else '，无变化')
    print(f\"  名次{r['rank']:>2}  {r['studentName']}  总分{r['totalScore']}{ch}  {'获奖' if r['awardEligible'] else ''}\")"

echo; echo "== 6. 封榜 v2 并按封榜发放（原发放记录不可变） =="
req POST "/api/rankings/versions/$V2ID/finalize" "$AC" '{"reason":"公示期满异议处理完毕，封榜"}' >/dev/null
req POST "/api/disbursements/pay" "$AC" "{\"versionId\":$V2ID,\"note\":\"统一发放\"}" \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('  生成原发放记录',d['paidCount'],'条')"

echo; echo "== 7. 发放后发现 s5 漏算省级奖项(+10)，只能生成封榜更正版本，不能重新公示 =="
echo -n "  试图重新公示 -> "; req POST "/api/rankings/publish" "$AC" "{\"term\":\"$TERM\",\"reason\":\"想重发\"}"; echo
M5=$(reqform "/api/materials" "$S5" \
  "studentId=$S5" "itemCode=BONUS" "title=省级特等奖" "claimedPoints=10" "evidence=证书编号PV-009" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
req POST "/api/materials/$M5/review" "$C2" '{"approved":true,"approvedPoints":10,"comment":"封账复核：省级特等奖证书属实，补认定10分"}' >/dev/null
V3=$(req POST "/api/rankings/correct" "$AC" \
  "{\"term\":\"$TERM\",\"reason\":\"s5省级特等奖经复核属实，原核算漏加10分，按锁定规则更正\"}")
V3ID=$(echo "$V3" | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
echo "  更正版本 v3 id=$V3ID（旧封榜 v2 与原发放记录均不改动）"

echo; echo "== 8. 红线3：按更正版本对账，只生成补发/退回，不动原发放 =="
req POST "/api/disbursements/reconcile" "$AC" \
  "{\"correctionVersionId\":$V3ID,\"reason\":\"按更正版本多退少补，原发放记录不变\"}" | python3 -m json.tool \
  | python3 -c "
import sys,json
d=json.load(sys.stdin)
print('  补退记录数:',d['adjustmentCount'])
for a in d['records']:
    print('   -', '补发' if a['kind']=='MAKEUP' else '退回', '学生', a['studentId'], a['amount']/100,'元 |', a['reason'])
"
echo "  原发放记录（仍指向 v2、金额不变）："
req GET "/api/disbursements" "$AC" | python3 -c "
import sys,json
for d in json.load(sys.stdin):
    print(f\"   - 发放#{d['id']} 版本{d['versionId']} 学生{d['studentId']} {d['amount']/100}元\")"

echo; echo "== 9. 处理依据（审计节选） =="
req GET "/api/audit" "$AC" | python3 -c "
import sys,json
keep=('RULE_LOCK','MATERIAL_SELF_REVIEW_BLOCKED','MATERIAL_REVIEW','APPEAL_UPHELD','RANKING_FINALIZE','RANKING_CORRECT','DISBURSE_PAY','DISBURSEMENT_ADJUST')
for l in json.load(sys.stdin):
    if l['action'] in keep:
        print(f\"  [{l['action']}] {l['actorName']}: {l['detail'][:90]}\")
" | head -20

echo; echo "✅ 全流程演练完成。可打开 $BASE 用右上角身份切换查看各角色页面。"
