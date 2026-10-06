package org.scholarship.web;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.scholarship.domain.Payment;
import org.scholarship.domain.PaymentAdjustment;
import org.scholarship.domain.User;
import org.scholarship.service.PaymentService;
import org.scholarship.service.UserService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Path("/api/payments")
@Produces(MediaType.APPLICATION_JSON)
public class PaymentResource {

    @Inject
    UserService userService;
    @Inject
    PaymentService paymentService;
    @Inject
    ViewMapper mapper;

    /** 封榜后按封榜版本整批生成发放记录；重复调用被拒绝（原记录不可改）。 */
    @POST
    @Path("/cycles/{cycleId}/batch")
    public Map<String, Object> createBatch(@HeaderParam("X-User-Id") String userId,
                                           @PathParam("cycleId") Long cycleId) {
        User admin = userService.requireRole(userId, "ADMIN");
        String batchNo = paymentService.createBatch(cycleId, admin.getId());
        return Map.of("batchNo", batchNo);
    }

    /**
     * 发放后的差额处理：只能追加补发/退回，必填处理依据；原 Payment 不提供任何修改入口。
     * paymentId 对补发可空（新进获奖线者），退回必须关联原发放。
     */
    @POST
    @Path("/cycles/{cycleId}/adjustments")
    public Map<String, Object> adjust(@HeaderParam("X-User-Id") String userId,
                                      @PathParam("cycleId") Long cycleId, AdjustReq req) {
        User admin = userService.requireRole(userId, "ADMIN");
        return mapper.adjustment(paymentService.adjust(cycleId, req.studentId(), req.type(),
                req.amount(), req.paymentId(), req.correctionId(), req.reason(), admin.getId()));
    }

    public record AdjustReq(String studentId, String type, BigDecimal amount,
                            Long paymentId, Long correctionId, String reason) {
    }

    @GET
    @Path("/cycles/{cycleId}")
    public Map<String, Object> list(@HeaderParam("X-User-Id") String userId,
                                    @PathParam("cycleId") Long cycleId) {
        User me = userService.get(userId);
        List<Payment> payments = paymentService.listPayments(cycleId);
        if ("STUDENT".equals(me.getRole())) {
            payments = payments.stream().filter(p -> p.getStudentId().equals(me.getId())).toList();
        }
        List<PaymentAdjustment> adjustments = paymentService.listAdjustments(cycleId);
        if ("STUDENT".equals(me.getRole())) {
            adjustments = adjustments.stream().filter(a -> a.getStudentId().equals(me.getId())).toList();
        }
        return Map.of(
                "payments", payments.stream()
                        .map(p -> mapper.payment(p, paymentService.netAmount(cycleId, p.getStudentId())))
                        .toList(),
                "adjustments", adjustments.stream().map(mapper::adjustment).toList()
        );
    }
}
