package edu.scholar.web;

import edu.scholar.domain.AuditLog;
import edu.scholar.domain.Role;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/** 处理依据查询：材料审核、规则锁定、公示重算、封榜更正、补退发放全程留痕。 */
@Path("/api/audit")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuditResource {

    @Inject
    CurrentUser currentUser;

    @GET
    public List<AuditLog> list(@QueryParam("action") String action) {
        currentUser.requireRole(Role.ACADEMIC, Role.COUNSELOR);
        if (action == null || action.isBlank()) {
            return AuditLog.list("order by id desc");
        }
        return AuditLog.list("action = ?1 order by id desc", action);
    }
}
