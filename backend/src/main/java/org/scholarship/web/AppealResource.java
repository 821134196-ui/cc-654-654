package org.scholarship.web;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.scholarship.domain.User;
import org.scholarship.service.AppealService;
import org.scholarship.service.UserService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Path("/api/appeals")
@Produces(MediaType.APPLICATION_JSON)
public class AppealResource {

    @Inject
    UserService userService;
    @Inject
    AppealService appealService;
    @Inject
    ViewMapper mapper;

    /** 学生在公示期内对具体分项提交异议。 */
    @POST
    public Map<String, Object> submit(@HeaderParam("X-User-Id") String userId, AppealReq req) {
        User student = userService.requireRole(userId, "STUDENT");
        return mapper.appeal(appealService.submit(req.cycleId(), req.itemKey(), req.reason(), student));
    }

    public record AppealReq(Long cycleId, String itemKey, String reason) {
    }

    /** 教务裁定：upheld=true 时必须给 correctedScore，成立即写更正依据并重算新版本。 */
    @POST
    @Path("/{id}/decide")
    public Map<String, Object> decide(@HeaderParam("X-User-Id") String userId,
                                      @PathParam("id") Long id, DecideReq req) {
        User admin = userService.requireRole(userId, "ADMIN");
        return mapper.appeal(appealService.decide(id, req.upheld(), req.correctedScore(),
                req.comment(), admin));
    }

    public record DecideReq(boolean upheld, BigDecimal correctedScore, String comment) {
    }

    @GET
    public List<Map<String, Object>> list(@HeaderParam("X-User-Id") String userId,
                                          @QueryParam("cycleId") Long cycleId,
                                          @QueryParam("studentId") String studentId) {
        User me = userService.get(userId);
        if (studentId != null) {
            if ("STUDENT".equals(me.getRole()) && !me.getId().equals(studentId)) {
                throw new ForbiddenException("只能查看本人的异议");
            }
            return appealService.listByStudent(cycleId, studentId).stream()
                    .map(mapper::appeal).toList();
        }
        userService.requireRole(userId, "ADMIN", "COUNSELOR");
        return appealService.listByCycle(cycleId).stream().map(mapper::appeal).toList();
    }
}
