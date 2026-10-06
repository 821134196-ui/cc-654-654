package edu.scholar.web;

import edu.scholar.domain.*;
import edu.scholar.service.RankingService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RankingResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    RankingService rankings;

    /* ---------- 版本与榜单查询 ---------- */

    @GET
    @Path("/rankings/{term}/versions")
    public List<RankingVersion> versions(@PathParam("term") String term) {
        currentUser.get();
        return rankings.listVersions(term);
    }

    @GET
    @Path("/rankings/versions/{id}/rows")
    public List<RankingRow> rows(@PathParam("id") Long id) {
        currentUser.get();
        return rankings.rows(id);
    }

    /** 学生查询自己在某版本的行（含分项依据） */
    @GET
    @Path("/rankings/versions/{id}/my-row")
    public RankingRow myRow(@PathParam("id") Long id) {
        User me = currentUser.requireRole(Role.STUDENT);
        return RankingRow.find("versionId = ?1 and studentId = ?2", id, me.id).firstResult();
    }

    /* ---------- 首次公示 ---------- */

    public static class PublishIn {
        public String term;
        public String reason;
    }

    @POST
    @Path("/rankings/publish")
    public RankingVersion publish(PublishIn in) {
        User actor = currentUser.requireRole(Role.ACADEMIC);
        return rankings.publish(actor, in.term, in.reason);
    }

    /* ---------- 申诉 ---------- */

    public static class AppealIn {
        public Long versionId;
        public String itemCode;
        public String reason;
    }

    @POST
    @Path("/appeals")
    public Appeal openAppeal(AppealIn in) {
        User student = currentUser.requireRole(Role.STUDENT);
        return rankings.openAppeal(student, in.versionId, in.itemCode, in.reason);
    }

    @GET
    @Path("/appeals")
    public List<Appeal> appeals(@QueryParam("status") String status) {
        currentUser.requireRole(Role.COUNSELOR, Role.ACADEMIC);
        if (status == null || status.isBlank()) {
            return Appeal.list("order by id desc");
        }
        return Appeal.list("status = ?1 order by id desc", AppealStatus.valueOf(status));
    }

    /** 学生查看自己的申诉 */
    @GET
    @Path("/appeals/mine")
    public List<Appeal> myAppeals() {
        User me = currentUser.requireRole(Role.STUDENT);
        return Appeal.list("studentId = ?1 order by id desc", me.id);
    }

    public static class AppealHandleIn {
        public Boolean uphold;
        public String comment;
    }

    @POST
    @Path("/appeals/{id}/handle")
    public Map<String, Object> handle(@PathParam("id") Long id, AppealHandleIn in) {
        User actor = currentUser.requireRole(Role.ACADEMIC);
        if (in.uphold == null) {
            throw new edu.scholar.service.BusinessException("请给出处理结论 uphold=true/false");
        }
        RankingVersion newVersion = rankings.handleAppeal(actor, id, in.uphold, in.comment);
        return Map.of("handled", true,
                "newVersion", newVersion == null ? "" : newVersion,
                "appeal", Appeal.findById(id));
    }

    /* ---------- 封榜 / 更正 ---------- */

    public static class FinalizeIn {
        public String reason;
    }

    @POST
    @Path("/rankings/versions/{id}/finalize")
    public RankingVersion finalize(@PathParam("id") Long id, FinalizeIn in) {
        User actor = currentUser.requireRole(Role.ACADEMIC);
        return rankings.finalize(actor, id, in == null ? null : in.reason);
    }

    public static class CorrectIn {
        public String term;
        public String reason;
    }

    @POST
    @Path("/rankings/correct")
    public RankingVersion correct(CorrectIn in) {
        User actor = currentUser.requireRole(Role.ACADEMIC);
        return rankings.correctAfterFinalized(actor, in.term, in.reason);
    }
}
