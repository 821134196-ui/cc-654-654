package edu.scholar.web;

import edu.scholar.domain.Material;
import edu.scholar.domain.Role;
import edu.scholar.domain.User;
import edu.scholar.service.AttachmentStorage;
import edu.scholar.service.BusinessException;
import edu.scholar.service.MaterialService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.nio.file.Files;
import java.util.List;

@Path("/api/materials")
@Produces(MediaType.APPLICATION_JSON)
public class MaterialResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    MaterialService materials;

    @Inject
    AttachmentStorage storage;

    /** 待审核材料（辅导员） */
    @GET
    @Path("/pending")
    public List<Material> pending() {
        currentUser.requireRole(Role.COUNSELOR, Role.ACADEMIC);
        return materials.listPending();
    }

    /** 我的材料（学生） */
    @GET
    @Path("/mine")
    public List<Material> mine() {
        User me = currentUser.requireRole(Role.STUDENT);
        return materials.listForStudent(me.id);
    }

    /** 提交材料：multipart/form-data，附件本地保存 */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Material submit(@RestForm("studentId") Long studentId,
                           @RestForm("itemCode") String itemCode,
                           @RestForm("title") String title,
                           @RestForm("claimedPoints") Double claimedPoints,
                           @RestForm("evidence") String evidence,
                           @RestForm("file") FileUpload file) throws Exception {
        User actor = currentUser.get(); // 学生本人或辅导员均可提交；审核另有自审拦截
        String storedName = null;
        String originalName = null;
        if (file != null) {
            byte[] bytes = Files.readAllBytes(file.uploadedFile());
            String[] saved = storage.save(bytes, file.fileName());
            storedName = saved[0];
            originalName = saved[1];
        }
        return materials.submit(actor, studentId, itemCode, title,
                claimedPoints == null ? 0 : claimedPoints, evidence, originalName, storedName);
    }

    public static class ReviewIn {
        public Boolean approved;
        public Double approvedPoints;
        public String comment;
    }

    /** 辅导员审核；审核人=提交人时服务层 403 拦截。 */
    @POST
    @Path("/{id}/review")
    @Consumes(MediaType.APPLICATION_JSON)
    public Material review(@PathParam("id") Long id, ReviewIn in) {
        User reviewer = currentUser.requireRole(Role.COUNSELOR, Role.ACADEMIC);
        if (in.approved == null) {
            throw new BusinessException("请明确审核结果 approved=true/false");
        }
        return materials.review(reviewer, id, in.approved, in.approvedPoints, in.comment);
    }

    /** 下载附件 */
    @GET
    @Path("/{id}/attachment")
    public Response attachment(@PathParam("id") Long id) throws Exception {
        currentUser.get();
        Material m = Material.findById(id);
        if (m == null || m.attachmentPath == null) {
            throw new BusinessException(404, "附件不存在");
        }
        java.nio.file.Path p = storage.resolve(m);
        String contentType = Files.probeContentType(p);
        return Response.ok(Files.readAllBytes(p))
                .type(contentType == null ? MediaType.APPLICATION_OCTET_STREAM : contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(m.attachmentName, "UTF-8"))
                .build();
    }
}
