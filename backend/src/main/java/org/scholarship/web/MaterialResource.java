package org.scholarship.web;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.scholarship.domain.BonusMaterial;
import org.scholarship.domain.User;
import org.scholarship.service.AttachmentService;
import org.scholarship.service.MaterialService;
import org.scholarship.service.UserService;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Path("/api/materials")
@Produces(MediaType.APPLICATION_JSON)
public class MaterialResource {

    @Inject
    UserService userService;
    @Inject
    MaterialService materialService;
    @Inject
    AttachmentService attachments;
    @Inject
    ViewMapper mapper;

    /**
     * 学生或辅导员提交加分材料（multipart：表单字段 + 可选附件，附件保存到本地）。
     * submittedBy 始终取登录用户，供自审拦截使用。
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Map<String, Object> submit(@HeaderParam("X-User-Id") String userId,
                                      MultipartFormDataInput form) throws Exception {
        User me = userService.requireRole(userId, "STUDENT", "COUNSELOR");
        Long cycleId = Long.valueOf(field(form, "cycleId"));
        String itemKey = field(form, "itemKey");
        String itemName = field(form, "itemName");
        String title = field(form, "title");
        BigDecimal claimed = new BigDecimal(field(form, "claimedPoints"));
        String studentId = field(form, "studentId");

        String ownerStudentId = "STUDENT".equals(me.getRole()) ? me.getId() : studentId;
        if (ownerStudentId == null || ownerStudentId.isBlank()) {
            throw new BadRequestException("辅导员代提交时必须指定 studentId");
        }

        String relPath = null;
        String origName = null;
        List<InputPart> files = form.getFormDataMap().get("file");
        if (hasFilename(files)) {
            InputPart filePart = files.get(0);
            String filename = extractFilename(filePart);
            try (InputStream in = filePart.getBody(InputStream.class, null)) {
                AttachmentService.StoredAttachment stored =
                        attachments.store(cycleId, in, filename);
                relPath = stored.relativePath();
                origName = stored.originalName();
            }
        }

        BonusMaterial m = materialService.submit(cycleId, ownerStudentId, itemKey, itemName,
                title, claimed, relPath, origName, me);
        return mapper.material(m);
    }

    private boolean hasFilename(List<InputPart> files) {
        return files != null && !files.isEmpty() && extractFilename(files.get(0)) != null;
    }

    private String field(MultipartFormDataInput form, String name) throws Exception {
        List<InputPart> parts = form.getFormDataMap().get(name);
        if (parts == null || parts.isEmpty()) {
            return null;
        }
        String v = parts.get(0).getBodyAsString();
        return v == null || v.isBlank() ? null : v.trim();
    }

    private String extractFilename(InputPart part) {
        String[] cd = part.getHeaders().getFirst("Content-Disposition").split(";");
        for (String s : cd) {
            String t = s.trim();
            if (t.startsWith("filename")) {
                String name = t.substring(t.indexOf('=') + 1).replace("\"", "").trim();
                return name.isEmpty() ? null : name;
            }
        }
        return null;
    }

    /** 辅导员/教务审核；提交人本人调用时服务层直接 403（自审拦截）。 */
    @POST
    @Path("/{id}/review")
    public Map<String, Object> review(@HeaderParam("X-User-Id") String userId,
                                      @PathParam("id") Long id, ReviewReq req) {
        User reviewer = userService.requireRole(userId, "COUNSELOR", "ADMIN");
        return mapper.material(materialService.review(id, reviewer, req.approved(),
                req.awardedPoints(), req.comment()));
    }

    public record ReviewReq(boolean approved, BigDecimal awardedPoints, String comment) {
    }

    @GET
    public List<Map<String, Object>> list(@HeaderParam("X-User-Id") String userId,
                                          @QueryParam("cycleId") Long cycleId,
                                          @QueryParam("studentId") String studentId) {
        User me = userService.get(userId);
        List<BonusMaterial> list;
        if (studentId != null) {
            if ("STUDENT".equals(me.getRole()) && !me.getId().equals(studentId)) {
                throw new ForbiddenException("只能查看本人的材料");
            }
            list = materialService.listByStudent(cycleId, studentId);
        } else {
            userService.requireRole(userId, "ADMIN", "COUNSELOR");
            list = materialService.listByCycle(cycleId);
        }
        return list.stream().map(mapper::material).toList();
    }

    @GET
    @Path("/{id}")
    public Map<String, Object> get(@HeaderParam("X-User-Id") String userId,
                                   @PathParam("id") Long id) {
        userService.get(userId);
        return mapper.material(materialService.get(id));
    }
}
