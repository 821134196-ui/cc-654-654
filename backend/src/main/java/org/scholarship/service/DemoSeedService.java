package org.scholarship.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.scholarship.domain.User;

import java.util.List;

/** 演示数据播种（本地模拟）：幂等创建三类用户，供一键启动后直接体验。 */
@ApplicationScoped
public class DemoSeedService {

    @Inject
    EntityManager em;

    @Transactional
    public boolean seedUsersIfEmpty() {
        Long n = em.createQuery("select count(u) from User u", Long.class).getSingleResult();
        if (n > 0) {
            return false;
        }
        List.of(
                new User("S001", "张三", "STUDENT", "CS2301"),
                new User("S002", "李四", "STUDENT", "CS2301"),
                new User("S003", "王五", "STUDENT", "CS2301"),
                new User("S004", "赵六", "STUDENT", "CS2301"),
                new User("T001", "陈辅导员", "COUNSELOR", "CS2301"),
                new User("T002", "林辅导员", "COUNSELOR", "CS2301"),
                new User("A001", "周教务", "ADMIN", null)
        ).forEach(em::persist);
        return true;
    }

    public List<User> listUsers() {
        return em.createQuery("select u from User u order by u.role, u.id", User.class).getResultList();
    }
}
