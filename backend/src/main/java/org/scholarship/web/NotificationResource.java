package org.scholarship.web;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.scholarship.service.NotificationService;
import org.scholarship.service.UserService;

import java.util.List;
import java.util.Map;

@Path("/api/notifications")
@Produces(MediaType.APPLICATION_JSON)
public class NotificationResource {

    @Inject
    UserService userService;
    @Inject
    NotificationService notificationService;
    @Inject
    ViewMapper mapper;

    @GET
    public List<Map<String, Object>> mine(@HeaderParam("X-User-Id") String userId) {
        var me = userService.get(userId);
        return notificationService.listMine(me.getId()).stream()
                .map(mapper::notification).toList();
    }

    @POST
    @Path("/{id}/read")
    public Map<String, Object> markRead(@HeaderParam("X-User-Id") String userId,
                                        @PathParam("id") Long id) {
        var me = userService.get(userId);
        notificationService.markRead(id, me.getId());
        return Map.of("ok", true);
    }
}
