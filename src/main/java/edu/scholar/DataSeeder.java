package edu.scholar;

import edu.scholar.domain.Role;
import edu.scholar.domain.User;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

/** 首次启动写入演示用户（本地模拟登录身份），已有数据则跳过。 */
@ApplicationScoped
public class DataSeeder {

    @Transactional
    public void seed(@Observes StartupEvent ev) {
        if (User.count() > 0) {
            return;
        }
        add("jiaowu", "王教务", Role.ACADEMIC, null, null);
        add("fudaoyuan1", "李辅导员", Role.COUNSELOR, null, "计科2301");
        add("fudaoyuan2", "赵辅导员", Role.COUNSELOR, null, "计科2302");
        add("s1", "张三", Role.STUDENT, "2023001", "计科2301");
        add("s2", "李四", Role.STUDENT, "2023002", "计科2301");
        add("s3", "王五", Role.STUDENT, "2023003", "计科2301");
        add("s4", "赵六", Role.STUDENT, "2023004", "计科2302");
        add("s5", "钱七", Role.STUDENT, "2023005", "计科2302");
    }

    private void add(String username, String name, Role role, String no, String clazz) {
        User u = new User();
        u.username = username;
        u.name = name;
        u.role = role;
        u.studentNo = no;
        u.className = clazz;
        u.persist();
    }
}
