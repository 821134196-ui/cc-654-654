package edu.scholar.web;

import edu.scholar.domain.User;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    CurrentUser currentUser;

    /** 当前模拟登录用户 */
    @GET
    @Path("/me")
    public User me() {
        return currentUser.get();
    }

    /** 用户列表（前端切换身份用） */
    @GET
    @Path("/users")
    public List<User> users(@QueryParam("role") String role) {
        return role == null || role.isBlank()
                ? User.listAll()
                : User.list("role = ?1 order by id", edu.scholar.domain.Role.valueOf(role));
    }
}
