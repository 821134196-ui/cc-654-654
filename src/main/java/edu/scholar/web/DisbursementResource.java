package edu.scholar.web;

import edu.scholar.domain.*;
import edu.scholar.service.DisbursementService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

@Path("/api/disbursements")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DisbursementResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    DisbursementService disbursements;

    public static class PayIn {
        public Long versionId;
        public String note;
    }

    /** 教务按封榜版本发放（生成不可变原发放记录） */
    @POST
    @Path("/pay")
    public Map<String, Object> pay(PayIn in) {
        User actor = currentUser.requireRole(Role.ACADEMIC);
        List<Disbursement> list = disbursements.pay(actor, in.versionId, in.note);
        return Map.of("paidCount", list.size(), "records", list);
    }

    public static class ReconcileIn {
        public Long correctionVersionId;
        public String reason;
    }

    /** 封榜更正后，按差额生成补发/退回记录；原发放记录不变。 */
    @POST
    @Path("/reconcile")
    public Map<String, Object> reconcile(ReconcileIn in) {
        User actor = currentUser.requireRole(Role.ACADEMIC);
        List<DisbursementAdjustment> list =
                disbursements.reconcile(actor, in.correctionVersionId, in.reason);
        return Map.of("adjustmentCount", list.size(), "records", list);
    }

    @GET
    public List<Disbursement> list() {
        User me = currentUser.get();
        return disbursements.listDisbursements(me.role == Role.STUDENT ? me.id : null);
    }

    @GET
    @Path("/adjustments")
    public List<DisbursementAdjustment> adjustments() {
        User me = currentUser.get();
        return disbursements.listAdjustments(me.role == Role.STUDENT ? me.id : null);
    }
}
