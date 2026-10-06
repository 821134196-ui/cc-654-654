package org.scholarship.web;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.scholarship.domain.BonusMaterial;
import org.scholarship.service.AttachmentService;
import org.scholarship.service.MaterialService;
import org.scholarship.service.UserService;

/** 附件本地下载：仅校验登录（演示），文件从本地存储目录流式返回。 */
@Path("/api/attachments")
public class AttachmentResource {

    @Inject
    UserService userService;
    @Inject
    MaterialService materialService;
    @Inject
    AttachmentService attachments;

    @GET
    @Path("/material/{id}")
    public Response download(@HeaderParam("X-User-Id") String userId,
                             @PathParam("id") Long id) {
        userService.get(userId);
        BonusMaterial m = materialService.get(id);
        if (m.getAttachmentPath() == null) {
            throw new NotFoundException("该材料没有附件");
        }
        java.nio.file.Path p = attachments.resolve(m.getAttachmentPath());
        if (!java.nio.file.Files.exists(p)) {
            throw new NotFoundException("附件文件缺失");
        }
        String filename = m.getAttachmentName() != null ? m.getAttachmentName() : p.getFileName().toString();
        String encoded = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        return Response.ok(p.toFile())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .type(MediaType.APPLICATION_OCTET_STREAM)
                .build();
    }
}
