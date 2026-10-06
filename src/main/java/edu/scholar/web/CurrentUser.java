package edu.scholar.web;

import edu.scholar.domain.Role;
import edu.scholar.domain.User;
import edu.scholar.service.BusinessException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;

/**
 * 本地模拟认证：前端通过 X-User-Id 头切换身份，无密码。
 */
@RequestScoped
public class CurrentUser {

    @Context
    HttpHeaders headers;

    public User get() {
        String id = headers.getHeaderString("X-User-Id");
        if (id == null || id.isBlank()) {
            throw new BusinessException(401, "缺少 X-User-Id 请求头（本地模拟登录）");
        }
        long uid;
        try {
            uid = Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(401, "X-User-Id 必须是用户数字 ID");
        }
        User user = User.findById(uid);
        if (user == null) {
            throw new BusinessException(401, "用户不存在: " + uid);
        }
        return user;
    }

    public User requireRole(Role... roles) {
        User user = get();
        for (Role role : roles) {
            if (user.role == role) {
                return user;
            }
        }
        throw BusinessException.forbidden("需要角色之一: " + java.util.Arrays.toString(roles) + "，当前: " + user.role);
    }
}
