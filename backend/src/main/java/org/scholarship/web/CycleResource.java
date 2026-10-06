package org.scholarship.web;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.scholarship.domain.*;
import org.scholarship.service.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Path("/api/cycles")
@Produces(MediaType.APPLICATION_JSON)
public class CycleResource {

    @Inject
    EntityManager em;
    @Inject
    UserService userService;
    @Inject
    CycleService cycleService;
    @Inject
    ImportService importService;
    @Inject
    ScoreService scoreService;
    @Inject
    CorrectionService correctionService;
    @Inject
    ViewMapper mapper;

    // ---------- 批次 ----------

    @POST
    public Map<String, Object> create(@HeaderParam("X-User-Id") String userId, CreateCycleReq req) {
        User admin = userService.requireRole(userId, "ADMIN");
        return mapper.cycle(cycleService.createCycle(req.name()));
    }

    @GET
    public List<Map<String, Object>> list(@HeaderParam("X-User-Id") String userId) {
        userService.get(userId);
        return em.createQuery("select c from ScholarshipCycle c order by c.id desc", ScholarshipCycle.class)
                .getResultList().stream().map(mapper::cycle).toList();
    }

    @GET
    @Path("/{id}")
    public Map<String, Object> get(@HeaderParam("X-User-Id") String userId, @PathParam("id") Long id) {
        userService.get(userId);
        return mapper.cycle(cycleService.mustGet(id));
    }

    public record CreateCycleReq(String name) {
    }

    // ---------- 规则锁定 / 公示 / 封榜 ----------

    @POST
    @Path("/{id}/lock")
    public Map<String, Object> lock(@HeaderParam("X-User-Id") String userId,
                                    @PathParam("id") Long id, LockReq req) {
        User admin = userService.requireRole(userId, "ADMIN");
        return mapper.cycle(cycleService.lockRules(id, req.tieRule(), req.awardQuota(),
                req.awardAmount(), req.rulesSnapshot(), admin.getId()));
    }

    public record LockReq(TieRule tieRule, Integer awardQuota,
                          BigDecimal awardAmount, String rulesSnapshot) {
    }

    @POST
    @Path("/{id}/publish")
    public Map<String, Object> publish(@HeaderParam("X-User-Id") String userId,
                                       @PathParam("id") Long id, PublishReq req) {
        User admin = userService.requireRole(userId, "ADMIN");
        LocalDateTime deadline = req.objectionDeadline() != null
                ? LocalDateTime.parse(req.objectionDeadline())
                : LocalDateTime.now().plusDays(7);
        return mapper.version(cycleService.publish(id, deadline, admin.getId()));
    }

    public record PublishReq(String objectionDeadline) {
    }

    @POST
    @Path("/{id}/close")
    public Map<String, Object> close(@HeaderParam("X-User-Id") String userId,
                                     @PathParam("id") Long id) {
        User admin = userService.requireRole(userId, "ADMIN");
        return mapper.version(cycleService.closeCycle(id, admin.getId()));
    }

    // ---------- 榜单版本（旧版可查） ----------

    @GET
    @Path("/{id}/versions")
    public List<Map<String, Object>> versions(@HeaderParam("X-User-Id") String userId,
                                              @PathParam("id") Long id) {
        userService.get(userId);
        return em.createQuery("select v from RankingVersion v where v.cycleId=:c order by v.versionNo",
                        RankingVersion.class).setParameter("c", id).getResultList()
                .stream().map(mapper::version).toList();
    }

    @GET
    @Path("/{id}/versions/{no}")
    public Map<String, Object> version(@HeaderParam("X-User-Id") String userId,
                                       @PathParam("id") Long id, @PathParam("no") int no) {
        userService.get(userId);
        return mapper.version(findVersion(id, no));
    }

    @GET
    @Path("/{id}/versions/latest")
    public Map<String, Object> latestVersion(@HeaderParam("X-User-Id") String userId,
                                             @PathParam("id") Long id) {
        userService.get(userId);
        return mapper.version(cycleService.latestVersion(id));
    }

    private RankingVersion findVersion(Long cycleId, int no) {
        List<RankingVersion> l = em.createQuery(
                        "select v from RankingVersion v where v.cycleId=:c and v.versionNo=:n",
                        RankingVersion.class)
                .setParameter("c", cycleId).setParameter("n", no).getResultList();
        if (l.isEmpty()) {
            throw new NotFoundException("版本 v" + no + " 不存在");
        }
        return l.get(0);
    }

    // ---------- 本地模拟导入 ----------

    @POST
    @Path("/{id}/import")
    public Map<String, Object> importScores(@HeaderParam("X-User-Id") String userId,
                                            @PathParam("id") Long id, ImportReq req) {
        User admin = userService.requireRole(userId, "ADMIN");
        int n = importService.importScores(id, req.rows(), admin.getId());
        return Map.of("imported", n);
    }

    public record ImportReq(List<ImportService.ImportRow> rows) {
    }

    // ---------- 得分明细（学生页面：各分项得分与依据） ----------

    @GET
    @Path("/{id}/scores")
    public List<Map<String, Object>> allScores(@HeaderParam("X-User-Id") String userId,
                                               @PathParam("id") Long id) {
        userService.requireRole(userId, "ADMIN", "COUNSELOR");
        return scoreService.compute(id).stream()
                .map(b -> detailedView(id, b.studentId, b)).toList();
    }

    @GET
    @Path("/{id}/scores/{studentId}")
    public Map<String, Object> oneScore(@HeaderParam("X-User-Id") String userId,
                                        @PathParam("id") Long id,
                                        @PathParam("studentId") String studentId) {
        User me = userService.get(userId);
        if ("STUDENT".equals(me.getRole()) && !me.getId().equals(studentId)) {
            throw new ForbiddenException("只能查看本人得分");
        }
        ScoreService.StudentBreakdown b = scoreService.compute(id).stream()
                .filter(x -> x.studentId.equals(studentId)).findFirst()
                .orElseThrow(() -> new NotFoundException("该学生暂无成绩数据"));
        return detailedView(id, studentId, b);
    }

    private Map<String, Object> detailedView(Long cycleId, String studentId,
                                             ScoreService.StudentBreakdown b) {
        Map<String, BaseScore> baseByKey = new HashMap<>();
        em.createQuery("select x from BaseScore x where x.cycleId=:c and x.studentId=:s",
                        BaseScore.class)
                .setParameter("c", cycleId).setParameter("s", studentId).getResultList()
                .forEach(x -> baseByKey.put(x.getItemKey(), x));

        List<BonusMaterial> mats = em.createQuery(
                        "select m from BonusMaterial m where m.cycleId=:c and m.studentId=:s " +
                                "and m.status='APPROVED' order by m.reviewedAt", BonusMaterial.class)
                .setParameter("c", cycleId).setParameter("s", studentId).getResultList();
        Map<String, List<ViewMapper.ItemBonusView>> bonusByKey = new HashMap<>();
        for (BonusMaterial m : mats) {
            bonusByKey.computeIfAbsent(m.getItemKey(), k -> new ArrayList<>())
                    .add(new ViewMapper.ItemBonusView(m.getTitle(), m.getStatus(),
                            m.getAwardedPoints(), m.getReviewedBy(), m.getReviewComment()));
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (ItemScore item : b.items) {
            BaseScore bs = baseByKey.get(item.itemKey());
            items.add(mapper.scoreItem(studentId, item,
                    bonusByKey.getOrDefault(item.itemKey(), List.of()),
                    bs != null ? bs.getEvidence() : "（无基础分，来自加分材料）"));
        }
        Map<String, Object> view = new HashMap<>();
        view.put("cycleId", cycleId);
        view.put("studentId", studentId);
        view.put("studentName", mapper.nameOf(studentId));
        view.put("total", b.total);
        view.put("items", items);
        return view;
    }

    // ---------- 更正（导入勘误 / 封榜后更正，均留依据并重算） ----------

    @POST
    @Path("/{id}/corrections")
    public Map<String, Object> correct(@HeaderParam("X-User-Id") String userId,
                                       @PathParam("id") Long id, CorrectReq req) {
        User admin = userService.requireRole(userId, "ADMIN");
        RankingVersion v = correctionService.correct(id, req.studentId(), req.itemKey(),
                req.newScore(), req.source(), req.reason(), admin.getId());
        return mapper.version(v);
    }

    public record CorrectReq(String studentId, String itemKey, BigDecimal newScore,
                             String source, String reason) {
    }

    @GET
    @Path("/{id}/corrections")
    public List<Map<String, Object>> corrections(@HeaderParam("X-User-Id") String userId,
                                                 @PathParam("id") Long id) {
        userService.requireRole(userId, "ADMIN", "COUNSELOR");
        return correctionService.listCorrections(id).stream().map(mapper::correction).toList();
    }
}
