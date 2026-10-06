package edu.scholar.web;

import edu.scholar.domain.*;
import edu.scholar.service.RuleService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

@Path("/api/rules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RuleResource {

    @Inject
    RuleService rules;

    @Inject
    CurrentUser currentUser;

    @GET
    @Path("/{term}")
    public Map<String, Object> get(@PathParam("term") String term) {
        RuleSet rs = rules.getOrNull(term);
        if (rs == null) {
            return Map.of("term", term, "status", "MISSING");
        }
        return Map.of("ruleSet", rs, "items", rules.items(rs.id));
    }

    public static class RuleConfigIn {
        public String tieRule;
        public Integer scholarshipCount;
        public Long awardAmount;
        public Integer appealWindowDays;
    }

    @PUT
    @Path("/{term}")
    public RuleSet configure(@PathParam("term") String term, RuleConfigIn in) {
        User actor = currentUser.requireRole(Role.ACADEMIC);
        return rules.configure(actor, term,
                in.tieRule == null ? null : TieRule.valueOf(in.tieRule),
                in.scholarshipCount, in.awardAmount, in.appealWindowDays);
    }

    public static class ItemIn {
        public String code;
        public String name;
        public String category;
        public double maxPoints;
        public int sortOrder;
    }

    @POST
    @Path("/{term}/items")
    public RuleItem addItem(@PathParam("term") String term, ItemIn in) {
        User actor = currentUser.requireRole(Role.ACADEMIC);
        return rules.addItem(actor, term, in.code, in.name, in.category, in.maxPoints, in.sortOrder);
    }

    @POST
    @Path("/{term}/lock")
    public RuleSet lock(@PathParam("term") String term) {
        User actor = currentUser.requireRole(Role.ACADEMIC);
        return rules.lock(actor, term);
    }
}
