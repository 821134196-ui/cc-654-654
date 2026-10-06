package org.scholarship.web;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.scholarship.domain.User;
import org.scholarship.service.DemoSeedService;
import org.scholarship.service.UserService;

import java.util.List;
import java.util.Map;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class ApiResource {

    @Inject
    UserService userService;
    @Inject
    DemoSeedService seedService;
    @Inject
    ViewMapper mapper;

    @GET
    @Path("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "app", "scholarship-appeal");
    }

    /** 本地模拟登录：传 X-User-Id 返回当前用户信息。 */
    @GET
    @Path("/me")
    public Map<String, Object> me(@HeaderParam("X-User-Id") String userId) {
        return mapper.user(userService.get(userId));
    }

    @GET
    @Path("/users")
    public List<Map<String, Object>> users(@HeaderParam("X-User-Id") String userId) {
        userService.requireRole(userId, "ADMIN", "COUNSELOR");
        return seedService.listUsers().stream().map(mapper::user).toList();
    }

    @POST
    @Path("/dev/seed")
    public Response seed() {
        boolean created = seedService.seedUsersIfEmpty();
        return Response.ok(Map.of("seeded", created)).build();
    }
}
