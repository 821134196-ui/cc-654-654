package org.scholarship.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.scholarship.domain.User;

@ApplicationScoped
public class UserService {

    @Inject
    EntityManager em;

    public User get(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(401, "缺少 X-User-Id 请求头（本地模拟登录）");
        }
        User u = em.find(User.class, userId);
        if (u == null) {
            throw new BusinessException(401, "用户不存在：" + userId);
        }
        return u;
    }

    /** 要求当前用户具备任一角色，否则 403。 */
    public User requireRole(String userId, String... roles) {
        User u = get(userId);
        for (String r : roles) {
            if (r.equals(u.getRole())) {
                return u;
            }
        }
        throw new BusinessException(403, "需要角色 " + String.join("/", roles) + "，当前为 " + u.getRole());
    }
}
