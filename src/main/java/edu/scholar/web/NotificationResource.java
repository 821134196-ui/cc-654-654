package edu.scholar.web;

import edu.scholar.domain.Notification;
import edu.scholar.domain.Role;
import edu.scholar.domain.User;
import edu.scholar.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

/** 本地模拟通知：仅入库查询，不接外部消息渠道。 */
@Path("/api/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NotificationResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    NotificationService notifications;

    @GET
    public List<Notification> list(@QueryParam("unread") boolean unread) {
        User me = currentUser.get();
        return notifications.list(me.id, unread);
    }

    @POST
    @Path("/{id}/read")
    @Transactional
    public Map<String, Object> markRead(@PathParam("id") Long id) {
        User me = currentUser.get();
        Notification n = Notification.findById(id);
        if (n == null || !n.userId.equals(me.id)) {
            throw new edu.scholar.service.BusinessException(404, "通知不存在");
        }
        n.readFlag = true;
        return Map.of("ok", true);
    }
}
