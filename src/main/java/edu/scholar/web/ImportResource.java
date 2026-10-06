package edu.scholar.web;

import edu.scholar.domain.Role;
import edu.scholar.domain.User;
import edu.scholar.service.AuditService;
import edu.scholar.service.ImportService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

/** 本地模拟导入接口（成绩由外部教务系统提供，这里以 JSON 请求代替）。 */
@Path("/api/import")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ImportResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    ImportService importService;

    public static class ImportIn {
        public String term;
        public List<ImportService.ScoreIn> scores;
    }

    @POST
    @Path("/scores")
    public Map<String, Object> importScores(ImportIn in) {
        User actor = currentUser.requireRole(Role.ACADEMIC);
        int n = importService.importScores(actor, in.term, in.scores);
        return Map.of("imported", n, "term", in.term);
    }
}
